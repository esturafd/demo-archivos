#!/usr/bin/env bash
#
# Aprovisionamiento del ambiente de base de datos.
#
# Crea y configura los roles técnicos de PostgreSQL que utilizan Flyway y la
# API, y otorga a app_migrator los privilegios mínimos para referenciar el
# esquema administrado neon_auth.
#
# Cuándo ejecutarlo:
#   Una sola vez, de forma manual, después de crear el proyecto/rama de Neon y
#   de habilitar Neon Auth, y antes de aplicar las migraciones de Flyway.
#
# Requisitos:
#   - psql 16 o superior disponible en el PATH.
#   - Un archivo .env junto a este script con las variables de .env.example.
#   - Una conexión con un rol propietario/administrador de la base
#     (por ejemplo neondb_owner). No uses app_migrator ni app_runtime.
#
# El script es idempotente: puede ejecutarse varias veces sin efectos
# adversos. Cada ejecución vuelve a aplicar las contraseñas indicadas en .env.
#
# Uso:
#   ./provision-environment.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/.env"

log()  { printf '%s\n' "$*"; }
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------------------
# 1. Cargar variables de entorno
# ---------------------------------------------------------------------------

[[ -f "$ENV_FILE" ]] || fail "No se encontró $ENV_FILE. Cópialo de .env.example y completa los valores."

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

require_var() {
  local name="$1"
  [[ -n "${!name:-}" ]] || fail "Falta la variable $name en $ENV_FILE."
}

require_var APP_MIGRATOR_PASSWORD
require_var APP_RUNTIME_PASSWORD

# ---------------------------------------------------------------------------
# 2. Validar el cliente psql
# ---------------------------------------------------------------------------

command -v psql >/dev/null 2>&1 \
  || fail "psql no está instalado o no está en el PATH."

psql_major="$(psql --version | sed -E 's/^psql \(PostgreSQL\) ([0-9]+).*/\1/')"
if [[ -n "$psql_major" ]] && (( psql_major < 16 )); then
  fail "Se requiere psql 16 o superior (se usa \\getenv). Versión detectada: $psql_major."
fi

# Conexión administrativa: por URL o por variables PG* del entorno.
psql_target=()
if [[ -n "${ADMIN_DATABASE_URL:-}" ]]; then
  psql_target+=("$ADMIN_DATABASE_URL")
else
  log "Aviso: ADMIN_DATABASE_URL no está definido; se usarán las variables PG* del entorno."
  log "       Asegúrate de definir PGHOST, PGPORT, PGDATABASE, PGUSER, PGPASSWORD y PGSSLMODE=require."
fi

log "Aprovisionando roles en la base de datos..."

# ---------------------------------------------------------------------------
# 3. Aplicar la configuración
# ---------------------------------------------------------------------------
# Las contraseñas se leen del entorno con \getenv para no exponerlas en la
# línea de comandos del proceso.

psql "${psql_target[@]}" -X -v ON_ERROR_STOP=1 -f - <<'SQL'

\getenv migrator_password APP_MIGRATOR_PASSWORD
\getenv runtime_password  APP_RUNTIME_PASSWORD

-- Verificación del entorno ---------------------------------------------------

DO $do$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'neon_auth') THEN
    RAISE EXCEPTION
      'No existe el esquema neon_auth. Habilita Neon Auth en el proyecto antes de aprovisionar.';
  END IF;

  IF NOT EXISTS (
    SELECT 1
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = 'neon_auth'
       AND c.relname = 'user'
  ) THEN
    RAISE EXCEPTION
      'No existe neon_auth."user". Habilita Neon Auth en el proyecto antes de aprovisionar.';
  END IF;

  RAISE NOTICE 'Entorno verificado: existe neon_auth."user".';
END
$do$;

-- Roles ----------------------------------------------------------------------

DO $do$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_migrator') THEN
    CREATE ROLE app_migrator;
    RAISE NOTICE 'Rol app_migrator creado.';
  ELSE
    RAISE NOTICE 'Rol app_migrator ya existe; se actualizará.';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_runtime') THEN
    CREATE ROLE app_runtime;
    RAISE NOTICE 'Rol app_runtime creado.';
  ELSE
    RAISE NOTICE 'Rol app_runtime ya existe; se actualizará.';
  END IF;
END
$do$;

-- Atributos y contraseñas ----------------------------------------------------
-- app_migrator: ejecuta Flyway y es propietario de app y audit.
-- app_runtime : conexión de la API; nunca debe omitir RLS.

ALTER ROLE app_migrator WITH
  LOGIN
  NOSUPERUSER
  NOBYPASSRLS
  NOCREATEDB
  NOCREATEROLE
  PASSWORD :'migrator_password';

ALTER ROLE app_runtime WITH
  LOGIN
  NOSUPERUSER
  NOBYPASSRLS
  NOCREATEDB
  NOCREATEROLE
  PASSWORD :'runtime_password';

-- Privilegios de aprovisionamiento -------------------------------------------
-- app_migrator debe poder crear los esquemas app y audit, y referenciar las
-- tablas de neon_auth para definir las claves foráneas de los perfiles y de
-- los archivos.

DO $do$
BEGIN
  EXECUTE format('GRANT CREATE ON DATABASE %I TO app_migrator', current_database());
  RAISE NOTICE 'Otorgado CREATE sobre la base de datos a app_migrator.';
END
$do$;

GRANT USAGE      ON SCHEMA neon_auth    TO app_migrator;
GRANT REFERENCES ON neon_auth."user"    TO app_migrator;

-- Opcional: si la API necesitara leer correos o nombres directamente desde
-- neon_auth, descomenta la siguiente línea. Por defecto se usa el API de
-- administración de Neon Auth, por lo que app_runtime no requiere acceso.
-- GRANT USAGE ON SCHEMA neon_auth TO app_runtime;

-- Verificación ---------------------------------------------------------------

\echo ''
\echo '== Rol conectado =='
SELECT current_user AS usuario, current_database() AS base_de_datos;

\echo ''
\echo '== Roles configurados =='
SELECT rolname           AS rol,
       rolcanlogin       AS inicia_sesion,
       rolsuper          AS superusuario,
       rolbypassrls      AS omite_rls,
       rolcreatedb       AS crea_base,
       rolcreaterole     AS crea_roles
  FROM pg_roles
 WHERE rolname IN ('app_migrator', 'app_runtime')
 ORDER BY rolname;

\echo ''
\echo '== Privilegios de app_migrator =='
SELECT has_database_privilege('app_migrator', current_database(), 'CREATE')        AS crea_esquemas,
       has_schema_privilege('app_migrator', 'neon_auth', 'USAGE')                  AS usa_neon_auth,
       has_table_privilege('app_migrator', 'neon_auth."user"', 'REFERENCES')       AS referencia_usuario;
SQL

log ""
log "Aprovisionamiento completado."
log "Siguiente paso: aplicar las migraciones con Flyway usando MIGRATION_DATABASE_URL (rol app_migrator)."

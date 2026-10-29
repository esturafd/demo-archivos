#!/usr/bin/env bash
#
# Ambiente local de base de datos y verificación de migraciones.
#
# Levanta un PostgreSQL desechable, simula la existencia de Neon Auth, ejecuta
# database/provision-environment.sh y aplica las migraciones con Flyway.
#
# Tiene dos usos:
#   1) Verificar el aprovisionamiento y las migraciones (comportamiento por
#      defecto). Al terminar, elimina el contenedor y la red.
#   2) Dejar un ambiente de base de datos listo para pruebas locales:
#        KEEP_ALIVE=1 ./test-database.sh
#
# Requisitos:
#   - El comando docker disponible en el PATH.
#   - Python 3 en el PATH (solo se usa la librería estándar).
#   - No requiere psql, Flyway ni PostgreSQL instalados en el host.
#
# Uso:
#   ./test-database.sh
#   KEEP_ALIVE=1 ./test-database.sh
#   ENV_FILE=/ruta/otro.env ./test-database.sh
#
# Configuración:
#   Las credenciales del ambiente local se leen de database/.env.test-local.
#
# Pruebas:
#   Las comprobaciones específicas de cada migración viven en database/tests,
#   un script de Python por migración. Este script los ejecuta al final.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MIGRATIONS_DIR="${SCRIPT_DIR}/migrations"
ENV_FILE="${ENV_FILE:-${SCRIPT_DIR}/.env.test-local}"

log()  { printf '%s\n' "$*"; }
step() { printf '\n[%s] %s\n' "$1" "$2"; }
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------------------
# 1. Configuración
# ---------------------------------------------------------------------------

step 1/8 "Cargando la configuración"

[[ -f "$ENV_FILE" ]] \
  || fail "No se encontró $ENV_FILE. Usa ENV_FILE para indicar otro archivo."

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

require_var() {
  local name="$1"
  [[ -n "${!name:-}" ]] || fail "Falta la variable $name en $ENV_FILE."
}

for v in DB_NAME DB_OWNER DB_OWNER_PASSWORD HOST_PORT \
         APP_MIGRATOR_PASSWORD APP_RUNTIME_PASSWORD; do
  require_var "$v"
done

# Estas contraseñas se escriben en el .env que se genera dentro del contenedor,
# por lo que se restringen a caracteres sin significado especial en el shell,
# en la cadena de conexión ni en SQL.
for v in DB_OWNER_PASSWORD APP_MIGRATOR_PASSWORD APP_RUNTIME_PASSWORD; do
  [[ "${!v}" =~ ^[A-Za-z0-9._-]+$ ]] \
    || fail "$v solo puede contener letras, números, punto, guion y guion bajo."
done

PG_IMAGE="${PG_IMAGE:-postgres:16-alpine}"
FLYWAY_IMAGE="${FLYWAY_IMAGE:-flyway/flyway:latest}"

RUN_ID="$$-$RANDOM"
PG_CONTAINER="demo-archivos-pg-${RUN_ID}"
NET_NAME="demo-archivos-net-${RUN_ID}"

log "Configuración: $ENV_FILE"
log "PostgreSQL:    $PG_IMAGE"
log "Flyway:        $FLYWAY_IMAGE"

# ---------------------------------------------------------------------------
# Limpieza
# ---------------------------------------------------------------------------
# Los contenedores se crean con --rm, por lo que basta detenerlos para que se
# eliminen. La red se elimina explícitamente.

cleanup() {
  docker stop "$PG_CONTAINER" >/dev/null 2>&1 || true
  docker network rm "$NET_NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT

# ---------------------------------------------------------------------------
# 2. Comprobaciones previas
# ---------------------------------------------------------------------------

step 2/8 "Comprobando el entorno"

command -v docker >/dev/null 2>&1 \
  || fail "No se encontró el comando docker en el PATH."
command -v python3 >/dev/null 2>&1 \
  || fail "No se encontró python3 en el PATH."

[[ -d "$MIGRATIONS_DIR" ]] || fail "No existe el directorio $MIGRATIONS_DIR."
[[ -f "${SCRIPT_DIR}/provision-environment.sh" ]] \
  || fail "No existe ${SCRIPT_DIR}/provision-environment.sh."

shopt -s nullglob
migrations=("$MIGRATIONS_DIR"/*.sql)
shopt -u nullglob
(( ${#migrations[@]} > 0 )) || fail "No hay archivos .sql en $MIGRATIONS_DIR."

log "Migraciones encontradas: ${#migrations[@]}"

# ---------------------------------------------------------------------------
# 3. Red y contenedor de PostgreSQL
# ---------------------------------------------------------------------------

step 3/8 "Creando la red y el contenedor de PostgreSQL"

docker network create "$NET_NAME" >/dev/null

docker run --rm -d \
  --name "$PG_CONTAINER" \
  --network "$NET_NAME" \
  -e POSTGRES_USER="$DB_OWNER" \
  -e POSTGRES_PASSWORD="$DB_OWNER_PASSWORD" \
  -e POSTGRES_DB="$DB_NAME" \
  -p "${HOST_PORT}:5432" \
  "$PG_IMAGE" >/dev/null

log "Esperando a que PostgreSQL acepte conexiones..."
ready=0
for _ in $(seq 1 60); do
  if docker exec "$PG_CONTAINER" \
       psql -q -tA -U "$DB_OWNER" -d "$DB_NAME" -c 'SELECT 1' >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 1
done
(( ready == 1 )) || fail "PostgreSQL no quedó disponible a tiempo."
log "PostgreSQL listo en localhost:${HOST_PORT}"

# ---------------------------------------------------------------------------
# 4. Simulación de Neon Auth
# ---------------------------------------------------------------------------
# En Neon, el servicio de Auth crea el esquema neon_auth y su tabla de usuarios.
# Localmente se replica esa estructura mínima para poder aplicar las claves
# foráneas definidas en las migraciones.

step 4/8 "Simulando Neon Auth (esquema neon_auth)"

docker exec -i "$PG_CONTAINER" \
  psql -q -v ON_ERROR_STOP=1 -U "$DB_OWNER" -d "$DB_NAME" <<'SQL'
CREATE SCHEMA IF NOT EXISTS neon_auth;
CREATE TABLE IF NOT EXISTS neon_auth."user" (
  id              text PRIMARY KEY,
  email           text NOT NULL,
  "emailVerified" boolean NOT NULL DEFAULT false
);
SQL
log 'neon_auth."user" disponible.'

# ---------------------------------------------------------------------------
# 5. Aprovisionamiento de roles
# ---------------------------------------------------------------------------
# El script se ejecuta dentro del contenedor para no depender de que el host
# tenga psql instalado. El .env se genera dentro del contenedor y es temporal.

step 5/8 "Ejecutando provision-environment.sh"

docker exec "$PG_CONTAINER" mkdir -p /work
docker cp "${SCRIPT_DIR}/provision-environment.sh" \
  "$PG_CONTAINER:/work/provision-environment.sh"

docker exec -i "$PG_CONTAINER" bash -c 'cat > /work/.env' <<ENV
ADMIN_DATABASE_URL=postgresql://${DB_OWNER}:${DB_OWNER_PASSWORD}@localhost:5432/${DB_NAME}?sslmode=disable
APP_MIGRATOR_PASSWORD=${APP_MIGRATOR_PASSWORD}
APP_RUNTIME_PASSWORD=${APP_RUNTIME_PASSWORD}
ENV

docker exec "$PG_CONTAINER" bash /work/provision-environment.sh

# ---------------------------------------------------------------------------
# 6. Migraciones con Flyway
# ---------------------------------------------------------------------------
# Flyway se conecta como app_migrator y guarda su historial en un esquema
# propio (flyway), que crea automáticamente. Así los esquemas de negocio app y
# audit los crea exclusivamente la primera migración.

step 6/8 "Aplicando migraciones con Flyway"

flyway_run() {
  docker run --rm \
    --network "$NET_NAME" \
    -v "${MIGRATIONS_DIR}:/flyway/sql:ro" \
    -e FLYWAY_URL="jdbc:postgresql://${PG_CONTAINER}:5432/${DB_NAME}" \
    -e FLYWAY_USER="app_migrator" \
    -e FLYWAY_PASSWORD="$APP_MIGRATOR_PASSWORD" \
    -e FLYWAY_DEFAULT_SCHEMA="flyway" \
    -e FLYWAY_LOCATIONS="filesystem:/flyway/sql" \
    -e FLYWAY_CONNECT_RETRIES="30" \
    "$FLYWAY_IMAGE" "$@"
}

flyway_run migrate
flyway_run validate

# ---------------------------------------------------------------------------
# 7. Pruebas de las migraciones
# ---------------------------------------------------------------------------
# Las comprobaciones específicas de cada migración viven en database/tests, un
# script de Python por migración. Se ejecutan con Python para que funcionen en
# cualquier sistema operativo, no solo donde haya bash.

step 7/8 "Ejecutando las pruebas de las migraciones"

log ""
log "-- Migraciones registradas por Flyway --"
flyway_run info

TESTS_DIR="${SCRIPT_DIR}/tests"
shopt -s nullglob
test_scripts=("$TESTS_DIR"/test_*.py)
shopt -u nullglob
(( ${#test_scripts[@]} > 0 )) || fail "No hay pruebas en $TESTS_DIR."

failed_tests=0
for test_script in "${test_scripts[@]}"; do
  log ""
  if ! python3 "$test_script" --container "$PG_CONTAINER" --env-file "$ENV_FILE"; then
    failed_tests=$((failed_tests + 1))
  fi
done

(( failed_tests == 0 )) || fail "$failed_tests script(s) de prueba fallaron."

# ---------------------------------------------------------------------------
# 8. Resultado
# ---------------------------------------------------------------------------

step 8/8 "Resultado"

if [[ "${KEEP_ALIVE:-0}" == "1" ]]; then
  trap - EXIT
  log "Ambiente local de base de datos en ejecución."
  log ""
  log "  Contenedor : $PG_CONTAINER"
  log "  Servidor   : localhost:${HOST_PORT}"
  log "  Base datos : $DB_NAME"
  log ""
  log "  Rol propietario : $DB_OWNER / $DB_OWNER_PASSWORD"
  log "  Rol migraciones : app_migrator / $APP_MIGRATOR_PASSWORD"
  log "  Rol aplicación  : app_runtime / $APP_RUNTIME_PASSWORD"
  log ""
  log "  Cadena: postgresql://${DB_OWNER}:${DB_OWNER_PASSWORD}@localhost:${HOST_PORT}/${DB_NAME}?sslmode=disable"
  log ""
  log "  Cliente: docker exec -it $PG_CONTAINER psql -U app_runtime -d $DB_NAME"
  log ""
  log "Para detener y eliminar el ambiente:"
  log "  docker stop $PG_CONTAINER && docker network rm $NET_NAME"
else
  log "Todas las comprobaciones pasaron. El ambiente se eliminará al finalizar."
fi

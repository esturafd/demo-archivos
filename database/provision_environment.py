#!/usr/bin/env python3
"""Aprovisionamiento del ambiente de base de datos.

Crea y configura los roles técnicos de PostgreSQL que utilizan Flyway y la API,
y otorga a app_migrator el permiso para crear los esquemas de la aplicación.

Cuándo ejecutarlo:
    Una sola vez, de forma manual, después de crear el proyecto o la rama de
    Neon, y antes de aplicar las migraciones.

Requisitos:
    - psql 16 o superior, en el sistema o dentro de un contenedor. Se usa
      \\getenv para que las contraseñas no aparezcan en la sentencia SQL.
    - Un archivo de configuración. Toma database/.env.example como base.
    - Una conexión con un rol que pueda crear roles (CREATEROLE). No hace falta
      que sea superusuario: el script nunca cambia atributos que lo requieran.

Uso:
    ./provision_environment.py
    ./provision_environment.py --env-file /ruta/otro.env
    ./provision_environment.py --container <nombre> --env-file /ruta/test.env

Solo usa la librería estándar de Python.
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path
from typing import List, Optional

from psql_utils import (
    Connection,
    EnvFile,
    ToolError,
    connection_from_pg_variables,
    connection_from_url,
    docker_available,
    enable_line_buffering,
    psql_available,
)

SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_ENV_FILE = SCRIPT_DIR / ".env"

REQUIRED_PSQL_MAJOR = 16
MIGRATOR_USER = "app_migrator"
APP_USER = "app_runtime"

PROVISIONING_SQL = r"""
\set ON_ERROR_STOP on

\getenv migrator_password APP_MIGRATOR_PASSWORD
\getenv runtime_password  APP_RUNTIME_PASSWORD

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

-- Acceso y contraseñas --------------------------------------------------------
-- app_migrator: ejecuta Flyway y es propietario de app y audit.
-- app_runtime : conexión de la API; nunca debe omitir RLS.

ALTER ROLE app_migrator WITH
  LOGIN
  PASSWORD :'migrator_password';

ALTER ROLE app_runtime WITH
  LOGIN
  PASSWORD :'runtime_password';

-- Comprobación de atributos ---------------------------------------------------
-- No se intenta corregirlos, solo detectarlos: si app_runtime pudiera omitir
-- RLS, las políticas de V3 dejarían de proteger las filas y el fallo sería
-- silencioso. Por eso aquí se aborta con un mensaje explícito.

DO $do$
DECLARE
  problemas text;
BEGIN
  SELECT string_agg(rolname || ' -> ' || atributo, ', ' ORDER BY rolname, atributo)
    INTO problemas
    FROM (
      SELECT rolname, 'SUPERUSER'  AS atributo FROM pg_roles
       WHERE rolname IN ('app_migrator', 'app_runtime') AND rolsuper
      UNION ALL
      SELECT rolname, 'BYPASSRLS'  FROM pg_roles
       WHERE rolname IN ('app_migrator', 'app_runtime') AND rolbypassrls
      UNION ALL
      SELECT rolname, 'CREATEDB'   FROM pg_roles
       WHERE rolname IN ('app_migrator', 'app_runtime') AND rolcreatedb
      UNION ALL
      SELECT rolname, 'CREATEROLE' FROM pg_roles
       WHERE rolname IN ('app_migrator', 'app_runtime') AND rolcreaterole
    ) AS detectados;

  IF problemas IS NOT NULL THEN
    RAISE EXCEPTION
      'Los roles técnicos tienen atributos inseguros y no se pueden corregir desde aquí: %. Revócalos con el rol propietario antes de continuar.',
      problemas
      USING ERRCODE = 'insufficient_privilege';
  END IF;

  RAISE NOTICE
    'Atributos verificados: sin SUPERUSER, BYPASSRLS, CREATEDB ni CREATEROLE.';
END
$do$;

-- Privilegios de aprovisionamiento -------------------------------------------
-- app_migrator debe poder crear los esquemas app y audit, y la extensión
-- pg_trgm que usan los índices de búsqueda.
--
-- Ya no se otorga nada sobre esquemas de servicios externos: la identidad es
-- propia y vive íntegramente en el esquema app.

DO $do$
BEGIN
  EXECUTE format('GRANT CREATE ON DATABASE %I TO app_migrator', current_database());
  RAISE NOTICE 'Otorgado CREATE sobre la base de datos a app_migrator.';
END
$do$;

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
SELECT has_database_privilege('app_migrator', current_database(), 'CREATE') AS crea_esquemas;
"""


# ---------------------------------------------------------------------------
# Configuración
# ---------------------------------------------------------------------------


def build_admin_connection(env: EnvFile, container: Optional[str]) -> Connection:
    """Decide con qué credenciales administrativas conectarse."""
    url = env.get("ADMIN_DATABASE_URL")
    if url:
        return connection_from_url(url, container)

    merged = dict(os.environ)
    merged.update(env.as_dict())
    from_pg = connection_from_pg_variables(merged, container)
    if from_pg is not None:
        return from_pg

    raise ToolError(
        "Define ADMIN_DATABASE_URL o las variables PG* "
        "(PGHOST, PGPORT, PGDATABASE, PGUSER, PGPASSWORD)."
    )


def check_requirements(container: Optional[str]) -> None:
    if container:
        if not docker_available():
            raise ToolError("No se encontró docker en el PATH.")
    elif not psql_available():
        raise ToolError(
            "No hay psql en el PATH. Usa --container para ejecutarlo dentro de un contenedor."
        )


def getenv_names() -> List[str]:
    """Nombres de las variables que la sentencia lee con \\getenv.

    Se extraen del propio SQL para que no puedan desincronizarse con las
    variables que se pasan al proceso.
    """
    return re.findall(r"\\getenv\s+\S+\s+(\w+)", PROVISIONING_SQL)


def check_psql_version(connection: Connection) -> None:
    major = connection.psql_major_version()
    if major is not None and major < REQUIRED_PSQL_MAJOR:
        raise ToolError(
            f"Se requiere psql {REQUIRED_PSQL_MAJOR} o superior porque se usa \\getenv. "
            f"Versión detectada: {major}."
        )


# ---------------------------------------------------------------------------
# Punto de entrada
# ---------------------------------------------------------------------------


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Crea y configura los roles técnicos de PostgreSQL."
    )
    parser.add_argument(
        "--env-file",
        type=Path,
        default=DEFAULT_ENV_FILE,
        help="Archivo de configuración (por defecto: database/.env)",
    )
    parser.add_argument(
        "--container",
        help="Contenedor Docker donde ejecutar psql. Si se omite, se usa el psql del sistema.",
    )
    args = parser.parse_args()
    enable_line_buffering()

    try:
        if not args.env_file.is_file():
            raise ToolError(
                f"No se encontró {args.env_file}. "
                "Copia database/.env.example y completa los valores."
            )

        env = EnvFile.load(args.env_file)
        check_requirements(args.container)

        admin = build_admin_connection(env, args.container)
        check_psql_version(admin)

        # Las contraseñas se pasan como variables de entorno y la sentencia las
        # lee con \getenv, así que nunca aparecen en la línea de comandos ni en
        # el texto enviado a psql.
        admin.extra_env = {
            name: env.require(name, args.env_file) for name in getenv_names()
        }

        print("Aprovisionando roles en la base de datos...")
        if not admin.run_streaming(PROVISIONING_SQL):
            raise ToolError("el aprovisionamiento falló.")

        print()
        print("Aprovisionamiento completado.")
        print(
            "Siguiente paso: aplicar las migraciones con Flyway "
            f"usando el rol {MIGRATOR_USER}."
        )
        return 0

    except ToolError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())

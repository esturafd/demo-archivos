#!/usr/bin/env python3
"""Ambiente local de base de datos y verificación de migraciones.

Levanta un PostgreSQL desechable, simula la existencia de Neon Auth, ejecuta el
aprovisionamiento de roles y aplica las migraciones con Flyway. Después ejecuta
las pruebas de cada migración que viven en database/tests.

Tiene dos usos:
  1) Verificar el aprovisionamiento y las migraciones (por defecto). Al
     terminar, elimina el contenedor y la red.
  2) Dejar un ambiente de base de datos listo para pruebas locales:
        ./test_database.py --keep-alive

Requisitos:
  - docker en el PATH.
  - Python 3 (solo se usa la librería estándar).
  - No requiere psql, Flyway ni PostgreSQL instalados en el sistema.

Uso:
    ./test_database.py
    ./test_database.py --keep-alive
    ./test_database.py --env-file /ruta/otro.env

Nota sobre Windows: el directorio de migraciones se monta en el contenedor; se
usa el formato con barras para que Docker Desktop lo acepte.
"""

from __future__ import annotations

import argparse
import os
import secrets
import subprocess
import sys
import tempfile
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

sys.path.insert(0, str(Path(__file__).resolve().parent))

from psql_utils import EnvFile, ToolError, docker_available, enable_line_buffering  # noqa: E402

SCRIPT_DIR = Path(__file__).resolve().parent
MIGRATIONS_DIR = SCRIPT_DIR / "migrations"
TESTS_DIR = SCRIPT_DIR / "tests"
PROVISION_SCRIPT = SCRIPT_DIR / "provision_environment.py"
DEFAULT_ENV_FILE = SCRIPT_DIR / ".env.test-local"

TOTAL_STEPS = 8

NEON_AUTH_STUB = """
CREATE SCHEMA IF NOT EXISTS neon_auth;
CREATE TABLE IF NOT EXISTS neon_auth."user" (
  id              text PRIMARY KEY,
  email           text NOT NULL,
  "emailVerified" boolean NOT NULL DEFAULT false
);
"""


# ---------------------------------------------------------------------------
# Configuración
# ---------------------------------------------------------------------------


@dataclass
class Settings:
    env_file: Path
    keep_alive: bool
    container: str
    network: str
    db_name: str
    db_owner: str
    db_owner_password: str
    app_migrator_password: str
    app_runtime_password: str
    host_port: str
    pg_image: str
    flyway_image: str


def step(number: int, message: str) -> None:
    print(f"\n[{number}/{TOTAL_STEPS}] {message}")


def log(message: str = "") -> None:
    print(message)


def validate_local_secret(name: str, value: str) -> None:
    """Estas contraseñas se copian a un .env temporal en texto plano."""
    forbidden = set("\n\r\x00'\"")
    if not value or any(character in forbidden for character in value):
        raise ToolError(
            f"{name} no puede estar vacía ni contener comillas o saltos de línea "
            "en el ambiente local."
        )


def load_settings(args: argparse.Namespace) -> Settings:
    env_file = args.env_file
    if not env_file.is_file():
        raise ToolError(f"No se encontró {env_file}. Usa --env-file para indicar otro archivo.")

    env = EnvFile.load(env_file)
    required = (
        "DB_NAME",
        "DB_OWNER",
        "DB_OWNER_PASSWORD",
        "HOST_PORT",
        "APP_MIGRATOR_PASSWORD",
        "APP_RUNTIME_PASSWORD",
    )
    values = {name: env.require(name, env_file) for name in required}

    for name in ("DB_OWNER_PASSWORD", "APP_MIGRATOR_PASSWORD", "APP_RUNTIME_PASSWORD"):
        validate_local_secret(name, values[name])

    run_id = f"{os.getpid()}-{secrets.randbelow(100000)}"

    return Settings(
        env_file=env_file,
        keep_alive=bool(args.keep_alive) or os.environ.get("KEEP_ALIVE") == "1",
        container=f"demo-archivos-pg-{run_id}",
        network=f"demo-archivos-net-{run_id}",
        db_name=values["DB_NAME"],
        db_owner=values["DB_OWNER"],
        db_owner_password=values["DB_OWNER_PASSWORD"],
        app_migrator_password=values["APP_MIGRATOR_PASSWORD"],
        app_runtime_password=values["APP_RUNTIME_PASSWORD"],
        host_port=values["HOST_PORT"],
        pg_image=env.get("PG_IMAGE", "postgres:16-alpine"),
        flyway_image=env.get("FLYWAY_IMAGE", "flyway/flyway:latest"),
    )


# ---------------------------------------------------------------------------
# docker
# ---------------------------------------------------------------------------


def docker(
    *arguments: str,
    env: Optional[dict] = None,
    capture: bool = False,
    stdin: Optional[str] = None,
    check: bool = True,
) -> subprocess.CompletedProcess:
    kwargs = {
        "check": check,
        "text": True,
        "capture_output": capture,
        "env": env,
    }
    if stdin is not None:
        kwargs["input"] = stdin
    return subprocess.run(["docker", *arguments], **kwargs)


def create_network(settings: Settings) -> None:
    docker("network", "create", settings.network, capture=True)


def start_database(settings: Settings) -> None:
    docker(
        "run", "--rm", "-d",
        "--name", settings.container,
        "--network", settings.network,
        "-e", f"POSTGRES_USER={settings.db_owner}",
        "-e", f"POSTGRES_PASSWORD={settings.db_owner_password}",
        "-e", f"POSTGRES_DB={settings.db_name}",
        "-p", f"{settings.host_port}:5432",
        settings.pg_image,
        capture=True,
    )


def wait_until_ready(settings: Settings) -> None:
    for _ in range(60):
        probe = docker(
            "exec", settings.container,
            "psql", "-q", "-tA", "-U", settings.db_owner, "-d", settings.db_name,
            "-c", "SELECT 1",
            capture=True, check=False,
        )
        if probe.returncode == 0:
            return
        time.sleep(1)
    raise ToolError("PostgreSQL no quedó disponible a tiempo.")


def create_neon_auth_stub(settings: Settings) -> None:
    docker(
        "exec", "-i", settings.container,
        "psql", "-q", "-v", "ON_ERROR_STOP=1",
        "-U", settings.db_owner, "-d", settings.db_name,
        stdin=NEON_AUTH_STUB,
    )


# ---------------------------------------------------------------------------
# Aprovisionamiento y migraciones
# ---------------------------------------------------------------------------


def write_temporary_env(settings: Settings) -> Path:
    """Escribe un .env temporal que usa provision_environment.py.

    Se usan variables PG* en lugar de una URL para no tener que escapar la
    contraseña. El archivo se elimina al terminar.
    """
    handle, path = tempfile.mkstemp(prefix="demo-archivos-", suffix=".env", text=True)
    with os.fdopen(handle, "w", encoding="utf-8") as file:
        file.write(
            "PGHOST=localhost\n"
            "PGPORT=5432\n"
            f"PGDATABASE={settings.db_name}\n"
            f"PGUSER={settings.db_owner}\n"
            f"PGPASSWORD='{settings.db_owner_password}'\n"
            "PGSSLMODE=disable\n"
            f"APP_MIGRATOR_PASSWORD='{settings.app_migrator_password}'\n"
            f"APP_RUNTIME_PASSWORD='{settings.app_runtime_password}'\n"
        )
    return Path(path)


def run_provisioning(settings: Settings) -> None:
    temporary_env = write_temporary_env(settings)
    try:
        command = [
            sys.executable, str(PROVISION_SCRIPT),
            "--env-file", str(temporary_env),
            "--container", settings.container,
        ]
        if subprocess.run(command, check=False).returncode != 0:
            raise ToolError("el aprovisionamiento falló.")
    finally:
        temporary_env.unlink(missing_ok=True)


def flyway(settings: Settings, *arguments: str) -> None:
    environment = dict(os.environ)
    environment["FLYWAY_PASSWORD"] = settings.app_migrator_password

    docker(
        "run", "--rm",
        "--network", settings.network,
        "-v", f"{MIGRATIONS_DIR.as_posix()}:/flyway/sql:ro",
        "-e", f"FLYWAY_URL=jdbc:postgresql://{settings.container}:5432/{settings.db_name}",
        "-e", "FLYWAY_USER=app_migrator",
        # Sin valor: docker toma la contraseña del entorno, no de la línea de comandos.
        "-e", "FLYWAY_PASSWORD",
        "-e", "FLYWAY_DEFAULT_SCHEMA=flyway",
        "-e", "FLYWAY_LOCATIONS=filesystem:/flyway/sql",
        "-e", "FLYWAY_CONNECT_RETRIES=30",
        settings.flyway_image,
        *arguments,
        env=environment,
    )


# ---------------------------------------------------------------------------
# Pruebas
# ---------------------------------------------------------------------------


def run_tests(settings: Settings) -> int:
    scripts = sorted(TESTS_DIR.glob("test_*.py"))
    if not scripts:
        raise ToolError(f"No hay pruebas en {TESTS_DIR}.")

    failures = 0
    for script in scripts:
        log()
        command = [
            sys.executable, str(script),
            "--container", settings.container,
            "--env-file", str(settings.env_file),
        ]
        if subprocess.run(command, check=False).returncode != 0:
            failures += 1
    return failures


# ---------------------------------------------------------------------------
# Limpieza y resultado
# ---------------------------------------------------------------------------


def cleanup(settings: Settings) -> None:
    # Los contenedores se crean con --rm, así que basta detenerlos.
    docker("stop", settings.container, capture=True, check=False)
    docker("network", "rm", settings.network, capture=True, check=False)


def print_result(settings: Settings) -> None:
    if settings.keep_alive:
        log("Ambiente local de base de datos en ejecución.")
        log()
        log(f"  Contenedor : {settings.container}")
        log(f"  Servidor   : localhost:{settings.host_port}")
        log(f"  Base datos : {settings.db_name}")
        log()
        log(f"  Rol propietario : {settings.db_owner} / {settings.db_owner_password}")
        log(f"  Rol migraciones : app_migrator / {settings.app_migrator_password}")
        log(f"  Rol aplicación  : app_runtime / {settings.app_runtime_password}")
        log()
        log(
            "  Cadena: postgresql://"
            f"{settings.db_owner}:{settings.db_owner_password}"
            f"@localhost:{settings.host_port}/{settings.db_name}?sslmode=disable"
        )
        log()
        log(f"  Cliente: docker exec -it {settings.container} psql -U app_runtime -d {settings.db_name}")
        log()
        log("  Una prueba suelta:")
        log(
            f"    python3 {TESTS_DIR / 'test_v1_initial_schema.py'} "
            f"--container {settings.container}"
        )
        log()
        log("Para detener y eliminar el ambiente:")
        log(f"  docker stop {settings.container} && docker network rm {settings.network}")
    else:
        log("Todas las comprobaciones pasaron. El ambiente se eliminará al finalizar.")


# ---------------------------------------------------------------------------
# Punto de entrada
# ---------------------------------------------------------------------------


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Levanta un ambiente local de base de datos y verifica las migraciones."
    )
    parser.add_argument(
        "--env-file",
        type=Path,
        default=DEFAULT_ENV_FILE,
        help="Credenciales del ambiente local (por defecto: database/.env.test-local)",
    )
    parser.add_argument(
        "--keep-alive",
        action="store_true",
        help="Deja el contenedor en ejecución al terminar para pruebas manuales.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_arguments()
    settings: Optional[Settings] = None
    enable_line_buffering()

    try:
        step(1, "Cargando la configuración")
        settings = load_settings(args)
        log(f"Configuración: {settings.env_file}")
        log(f"PostgreSQL:    {settings.pg_image}")
        log(f"Flyway:        {settings.flyway_image}")

        step(2, "Comprobando el entorno")
        if not docker_available():
            raise ToolError("No se encontró el comando docker en el PATH.")
        if not MIGRATIONS_DIR.is_dir():
            raise ToolError(f"No existe el directorio {MIGRATIONS_DIR}.")
        if not PROVISION_SCRIPT.is_file():
            raise ToolError(f"No existe {PROVISION_SCRIPT}.")
        if not TESTS_DIR.is_dir():
            raise ToolError(f"No existe el directorio {TESTS_DIR}.")

        migrations = sorted(MIGRATIONS_DIR.glob("*.sql"))
        if not migrations:
            raise ToolError(f"No hay archivos .sql en {MIGRATIONS_DIR}.")
        log(f"Migraciones encontradas: {len(migrations)}")

        step(3, "Creando la red y el contenedor de PostgreSQL")
        create_network(settings)
        start_database(settings)
        log("Esperando a que PostgreSQL acepte conexiones...")
        wait_until_ready(settings)
        log(f"PostgreSQL listo en localhost:{settings.host_port}")

        step(4, "Simulando Neon Auth (esquema neon_auth)")
        create_neon_auth_stub(settings)
        log('neon_auth."user" disponible.')

        step(5, "Ejecutando el aprovisionamiento de roles")
        run_provisioning(settings)

        step(6, "Aplicando migraciones con Flyway")
        flyway(settings, "migrate")
        flyway(settings, "validate")

        step(7, "Ejecutando las pruebas de las migraciones")
        log()
        log("-- Migraciones registradas por Flyway --")
        flyway(settings, "info")
        failures = run_tests(settings)
        if failures:
            raise ToolError(f"{failures} script(s) de prueba fallaron.")

        step(8, "Resultado")
        print_result(settings)
        return 0

    except ToolError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    except subprocess.CalledProcessError as error:
        print(f"ERROR: falló el comando: {' '.join(map(str, error.cmd))}", file=sys.stderr)
        return 1
    finally:
        if settings is not None and not settings.keep_alive:
            cleanup(settings)


if __name__ == "__main__":
    sys.exit(main())

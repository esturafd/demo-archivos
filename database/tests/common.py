"""Infraestructura compartida por los scripts de prueba de migraciones.

Solo usa la librería estándar de Python.

Las utilidades de conexión viven en database/psql_utils.py, en el directorio
padre, para que también las usen el aprovisionamiento y el arranque del
ambiente local. Aquí se añade un pequeño arnés de comprobaciones.

Ejecución típica desde test_database.py:
    python3 tests/test_v1_initial_schema.py --container <nombre>

Ejecución manual conservando el ambiente (--keep-alive):
    python3 database/tests/test_v1_initial_schema.py --container <nombre>

Contra un PostgreSQL accesible con el psql del sistema:
    python3 database/tests/test_v1_initial_schema.py
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Callable, List, Optional

# El directorio padre contiene psql_utils.py.
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from psql_utils import (  # noqa: E402
    Connection,
    EnvFile,
    ToolError,
    docker_available,
    enable_line_buffering,
    first_line,
    psql_available,
)

TestFailure = ToolError

DEFAULT_ENV_FILE = Path(__file__).resolve().parent.parent / ".env.test-local"

MIGRATOR_USER = "app_migrator"
APP_USER = "app_runtime"


# ---------------------------------------------------------------------------
# Ambiente bajo prueba
# ---------------------------------------------------------------------------


class Context:
    """Reúne una conexión por rol contra el ambiente bajo prueba."""

    def __init__(
        self,
        owner: Connection,
        migrator: Connection,
        app: Connection,
        database: str,
    ) -> None:
        self.owner = owner
        self.migrator = migrator
        self.app = app
        self.database = database


def in_user_context(user_id: str, statement: str, *, commit: bool = False) -> str:
    """Envuelve una sentencia en una transacción con el usuario indicado.

    El contexto de V2 es local a la transacción, así que SET LOCAL y la
    sentencia deben viajar juntos. Por defecto se termina con ROLLBACK para que
    ninguna prueba deje residuos; con commit=True el cambio se conserva.
    """
    body = statement.strip()
    if not body.endswith(";"):
        body += ";"
    ending = "COMMIT;" if commit else "ROLLBACK;"
    return (
        "BEGIN;\n"
        f"SET LOCAL app.user_id = '{user_id}';\n"
        f"{body}\n"
        f"{ending}"
    )


def build_context(env_file: Path, container: Optional[str]) -> Context:
    env = EnvFile.load(env_file)
    database = env.require("DB_NAME")
    host_port = env.get("HOST_PORT", "5432")
    host = env.get("HOST", "localhost")

    def connection(user: str, password: str) -> Connection:
        return Connection(
            user=user,
            password=password,
            database=database,
            container=container,
            host=host,
            port=host_port,
        )

    return Context(
        owner=connection(env.require("DB_OWNER"), env.require("DB_OWNER_PASSWORD")),
        migrator=connection(MIGRATOR_USER, env.require("APP_MIGRATOR_PASSWORD")),
        app=connection(APP_USER, env.require("APP_RUNTIME_PASSWORD")),
        database=database,
    )


# ---------------------------------------------------------------------------
# Resultados
# ---------------------------------------------------------------------------


class Report:
    """Acumula los resultados de una prueba."""

    def __init__(self, title: str) -> None:
        self.title = title
        self.passed = 0
        self.failures: List[str] = []

    def ok(self) -> bool:
        return not self.failures

    def check(self, condition: bool, message: str, detail: str = "") -> None:
        if condition:
            self.passed += 1
            print(f"  OK    {message}")
        else:
            suffix = f" ({detail})" if detail else ""
            self.fail(f"{message}{suffix}")

    def fail(self, message: str) -> None:
        self.failures.append(message)
        print(f"  FALLA {message}")

    def check_succeeds(self, connection: Connection, sql: str, message: str) -> None:
        ok, output = connection.attempt(sql)
        if ok:
            self.passed += 1
            print(f"  OK    {message}")
        else:
            self.fail(f"{message} (debería haber funcionado: {first_line(output)})")

    def check_fails(
        self,
        connection: Connection,
        sql: str,
        message: str,
        containing: Optional[str] = None,
    ) -> None:
        ok, output = connection.attempt(sql)
        if ok:
            self.fail(f"{message} (la sentencia debía fallar y no lo hizo)")
        elif containing and containing not in output:
            self.fail(f"{message} (error inesperado: {first_line(output)})")
        else:
            self.passed += 1
            print(f"  OK    {message}")


# ---------------------------------------------------------------------------
# Punto de entrada común
# ---------------------------------------------------------------------------


def run_test(title: str, body: Callable[[Context, Report], None]) -> int:
    parser = argparse.ArgumentParser(description=title)
    parser.add_argument(
        "--env-file",
        type=Path,
        default=DEFAULT_ENV_FILE,
        help="Credenciales del ambiente local (por defecto: database/.env.test-local)",
    )
    parser.add_argument(
        "--container",
        help="Contenedor Docker donde ejecutar psql. Si se omite, se usa el psql del sistema.",
    )
    args = parser.parse_args()

    report = Report(title)
    enable_line_buffering()
    print(title)

    try:
        if not args.env_file.is_file():
            raise TestFailure(f"No se encontró el archivo de configuración {args.env_file}")
        if args.container:
            if not docker_available():
                raise TestFailure("No se encontró docker en el PATH.")
        elif not psql_available():
            raise TestFailure(
                "No hay psql en el PATH. Indica --container para usar el del contenedor."
            )

        context = build_context(args.env_file, args.container)
        body(context, report)
    except TestFailure as failure:
        report.fail(str(failure))
    except Exception as unexpected:  # noqa: BLE001
        report.fail(f"error inesperado: {unexpected!r}")

    print()
    print(f"{title}: {report.passed} correctas, {len(report.failures)} fallidas")
    for failure in report.failures:
        print(f"  - {failure}")
    print("RESULTADO:", "OK" if report.ok() else "FALLA")
    return 0 if report.ok() else 1


def main(title: str, body: Callable[[Context, Report], None]) -> None:
    sys.exit(run_test(title, body))

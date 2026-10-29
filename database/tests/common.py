"""Infraestructura compartida para los scripts de prueba de migraciones.

Usa únicamente la librería estándar de Python.

Las pruebas se ejecutan contra un PostgreSQL local. Por defecto lo hacen dentro
de un contenedor Docker (docker exec ... psql), pero también admiten un psql
disponible en el host, lo que permite ejecutarlas desde cualquier sistema
operativo sin depender de bash.

Uso típico desde test-database.sh:
    python3 tests/test_v1_initial_schema.py --container <nombre>

Ejecución manual conservando el ambiente (KEEP_ALIVE=1):
    python3 database/tests/test_v1_initial_schema.py --container <nombre>

Contra un PostgreSQL accesible con el psql del host:
    python3 database/tests/test_v1_initial_schema.py
"""

from __future__ import annotations

import argparse
import csv
import io
import json
import shutil
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Dict, List, Optional, Tuple

DEFAULT_ENV_FILE = Path(__file__).resolve().parent.parent / ".env.test-local"

MIGRATOR_USER = "app_migrator"
APP_USER = "app_runtime"


class TestFailure(Exception):
    """Error de configuración o de entorno que impide continuar la prueba."""


# ---------------------------------------------------------------------------
# Archivo de configuración .env
# ---------------------------------------------------------------------------


class EnvFile:
    """Lector mínimo de archivos .env (sin dependencias externas)."""

    def __init__(self, values: Dict[str, str]) -> None:
        self._values = values

    @classmethod
    def load(cls, path: Path) -> "EnvFile":
        values: Dict[str, str] = {}
        for raw in path.read_text(encoding="utf-8").splitlines():
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            if line.startswith("export "):
                line = line[len("export "):].lstrip()
            if "=" not in line:
                continue
            key, _, value = line.partition("=")
            key = key.strip()
            value = value.strip()
            if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
                value = value[1:-1]
            values[key] = value
        return cls(values)

    def get(self, key: str, default: Optional[str] = None) -> Optional[str]:
        return self._values.get(key, default)

    def require(self, key: str) -> str:
        value = self._values.get(key)
        if not value:
            raise TestFailure(f"Falta la variable {key} en el archivo de configuración.")
        return value


# ---------------------------------------------------------------------------
# Ejecución de psql
# ---------------------------------------------------------------------------


@dataclass
class Connection:
    """Describe cómo ejecutar psql para un rol concreto."""

    user: str
    password: str
    database: str
    container: Optional[str] = None
    host: str = "localhost"
    port: str = "5432"

    def _command(self, extra: List[str]) -> List[str]:
        if self.container:
            return [
                "docker", "exec", "-i",
                "-e", f"PGPASSWORD={self.password}",
                self.container,
                "psql", "-X", "-q", "-v", "ON_ERROR_STOP=1",
                "-h", "localhost", "-U", self.user, "-d", self.database,
                *extra,
            ]
        return [
            "psql", "-X", "-q", "-v", "ON_ERROR_STOP=1",
            "-h", self.host, "-p", self.port, "-U", self.user, "-d", self.database,
            *extra,
        ]

    def attempt(self, sql: str, *, csv_mode: bool = False) -> Tuple[bool, str]:
        """Ejecuta SQL y devuelve (éxito, salida). Nunca lanza por error de SQL."""
        extra = ["--csv"] if csv_mode else []
        process = subprocess.run(
            self._command(extra),
            input=sql,
            text=True,
            capture_output=True,
            env=self._environment(),
        )
        output = process.stdout if process.returncode == 0 else (process.stderr or process.stdout)
        return process.returncode == 0, output

    def _environment(self) -> Dict[str, str]:
        import os

        env = dict(os.environ)
        if not self.container:
            env["PGPASSWORD"] = self.password
            env.setdefault("PGSSLMODE", "prefer")
        return env

    def run(self, sql: str) -> str:
        ok, output = self.attempt(sql)
        if not ok:
            raise TestFailure(f"psql falló: {first_line(output)}\nSQL: {sql.strip()[:200]}")
        return output

    def succeeds(self, sql: str) -> bool:
        return self.attempt(sql)[0]

    def fails(self, sql: str, containing: Optional[str] = None) -> bool:
        ok, output = self.attempt(sql)
        if ok:
            return False
        return containing is None or containing in output

    def query(self, sql: str) -> List[Dict[str, str]]:
        """Ejecuta una consulta y devuelve las filas como diccionarios."""
        ok, output = self.attempt(sql, csv_mode=True)
        if not ok:
            raise TestFailure(f"consulta falló: {first_line(output)}\nSQL: {sql.strip()[:200]}")
        return [dict(row) for row in csv.DictReader(io.StringIO(output)) if row]

    def scalar(self, sql: str) -> Optional[str]:
        rows = self.query(sql)
        if not rows:
            return None
        return next(iter(rows[0].values()))

    def json(self, sql: str) -> object:
        raw = self.scalar(sql)
        if raw is None:
            raise TestFailure(f"la consulta no devolvió JSON\nSQL: {sql.strip()[:200]}")
        return json.loads(raw)


@dataclass
class Context:
    """Ambiente bajo prueba, con una conexión por rol."""

    owner: Connection
    migrator: Connection
    app: Connection
    database: str


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


def first_line(text: str) -> str:
    for line in text.splitlines():
        line = line.strip()
        if line:
            return line
    return "(sin detalle)"


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
        help="Contenedor Docker donde ejecutar psql. Si se omite, se usa el psql del host.",
    )
    args = parser.parse_args()

    report = Report(title)
    print(title)

    try:
        if not args.env_file.is_file():
            raise TestFailure(f"No se encontró el archivo de configuración {args.env_file}")
        if args.container:
            if shutil.which("docker") is None:
                raise TestFailure("No se encontró docker en el PATH.")
        elif shutil.which("psql") is None:
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

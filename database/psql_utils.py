"""Utilidades compartidas para hablar con PostgreSQL desde los scripts.

Solo usa la librería estándar de Python.

No existe un cliente PostgreSQL en la librería estándar, así que se invoca
`psql` por medio de subprocess. Eso permite dos modos:

  * Dentro de un contenedor: docker exec <contenedor> psql ...
  * Contra un psql del sistema: psql -h <host> -p <puerto> ...

Los secretos se pasan al proceso mediante variables de entorno y, en el modo
contenedor, con `-e NOMBRE` (docker toma el valor del entorno del cliente), de
forma que nunca aparecen en la línea de comandos.
"""

from __future__ import annotations

import csv
import io
import json
import os
import shutil
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Tuple
from urllib.parse import parse_qsl, unquote, urlsplit


class ToolError(Exception):
    """Error de configuración o de ejecución que impide continuar."""


def first_line(text: str) -> str:
    for line in text.splitlines():
        line = line.strip()
        if line:
            return line
    return "(sin detalle)"


def docker_available() -> bool:
    return shutil.which("docker") is not None


def psql_available() -> bool:
    return shutil.which("psql") is not None


def enable_line_buffering() -> None:
    """Escribe la salida línea a línea.

    Cuando stdout no es una terminal, Python la almacena por bloques mientras
    los subprocesos (docker, psql, flyway) escriben directamente sobre el mismo
    descriptor. Sin esto, el orden de los mensajes se altera.
    """
    reconfigure = getattr(sys.stdout, "reconfigure", None)
    if reconfigure is not None:
        reconfigure(line_buffering=True)


# ---------------------------------------------------------------------------
# Archivo de configuración .env
# ---------------------------------------------------------------------------


class EnvFile:
    """Lector mínimo de archivos .env (sin dependencias externas).

    Admite comentarios con #, líneas en blanco, el prefijo `export` y valores
    entrecomillados con comillas simples o dobles.
    """

    def __init__(self, values: Dict[str, str]) -> None:
        self._values = values

    @classmethod
    def load(cls, path: Path) -> "EnvFile":
        try:
            text = path.read_text(encoding="utf-8")
        except OSError as error:
            raise ToolError(f"No se pudo leer {path}: {error}") from error

        values: Dict[str, str] = {}
        for raw in text.splitlines():
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

    def require(self, key: str, origin: object = "el archivo de configuración") -> str:
        value = self._values.get(key)
        if not value:
            raise ToolError(f"Falta la variable {key} en {origin}.")
        return value

    def keys(self) -> Iterable[str]:
        return self._values.keys()

    def as_dict(self) -> Dict[str, str]:
        return dict(self._values)


# ---------------------------------------------------------------------------
# Conexión
# ---------------------------------------------------------------------------


@dataclass
class Connection:
    """Describe cómo invocar psql para un rol concreto."""

    user: str
    password: str
    database: str
    container: Optional[str] = None
    host: str = "localhost"
    port: str = "5432"
    sslmode: Optional[str] = None
    extra_env: Dict[str, str] = field(default_factory=dict)

    # -- construcción ------------------------------------------------------

    def psql_environment(self) -> Dict[str, str]:
        """Variables que debe ver psql para autenticarse."""
        values: Dict[str, str] = {"PGPASSWORD": self.password}
        values.update(self.extra_env)
        if self.container:
            # Dentro del contenedor la conexión es local y sin TLS.
            values["PGSSLMODE"] = "disable"
        else:
            values["PGSSLMODE"] = self.sslmode or "prefer"
        return values

    def _command(self, extra: List[str]) -> List[str]:
        if self.container:
            command = ["docker", "exec", "-i"]
            for name in self.psql_environment():
                # Se pasa solo el nombre: docker toma el valor del entorno.
                command += ["-e", name]
            command += [
                self.container,
                "psql", "-X", "-q", "-v", "ON_ERROR_STOP=1",
                "-h", "localhost", "-U", self.user, "-d", self.database,
                *extra,
            ]
            return command

        return [
            "psql", "-X", "-q", "-v", "ON_ERROR_STOP=1",
            "-h", self.host, "-p", self.port, "-U", self.user, "-d", self.database,
            *extra,
        ]

    def _environment(self) -> Dict[str, str]:
        environment = dict(os.environ)
        environment.update(self.psql_environment())
        return environment

    # -- ejecución ---------------------------------------------------------

    def attempt(self, sql: str, *, csv_mode: bool = False) -> Tuple[bool, str]:
        """Ejecuta SQL y devuelve (éxito, salida). No lanza por error de SQL."""
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

    def run(self, sql: str) -> str:
        ok, output = self.attempt(sql)
        if not ok:
            raise ToolError(f"psql falló: {first_line(output)}\nSQL: {sql.strip()[:200]}")
        return output

    def run_streaming(self, sql: str) -> bool:
        """Ejecuta SQL mostrando la salida tal cual llega (avisos incluidos).

        Devuelve True si psql terminó sin error.
        """
        process = subprocess.run(
            self._command([]),
            input=sql,
            text=True,
            env=self._environment(),
        )
        return process.returncode == 0

    def succeeds(self, sql: str) -> bool:
        return self.attempt(sql)[0]

    def fails(self, sql: str, containing: Optional[str] = None) -> bool:
        ok, output = self.attempt(sql)
        if ok:
            return False
        return containing is None or containing in output

    def query(self, sql: str) -> List[Dict[str, str]]:
        ok, output = self.attempt(sql, csv_mode=True)
        if not ok:
            raise ToolError(f"consulta falló: {first_line(output)}\nSQL: {sql.strip()[:200]}")
        return [dict(row) for row in csv.DictReader(io.StringIO(output)) if row]

    def scalar(self, sql: str) -> Optional[str]:
        rows = self.query(sql)
        if not rows:
            return None
        return next(iter(rows[0].values()))

    def json(self, sql: str):
        raw = self.scalar(sql)
        if raw is None:
            raise ToolError(f"la consulta no devolvió JSON\nSQL: {sql.strip()[:200]}")
        return json.loads(raw)

    # -- utilidades --------------------------------------------------------

    def psql_version(self) -> str:
        if self.container:
            command = ["docker", "exec", self.container, "psql", "--version"]
        else:
            command = ["psql", "--version"]
        process = subprocess.run(command, text=True, capture_output=True)
        if process.returncode != 0:
            raise ToolError(
                "No se pudo determinar la versión de psql: " + first_line(process.stderr)
            )
        return process.stdout.strip()

    def psql_major_version(self) -> Optional[int]:
        version = self.psql_version()
        for chunk in version.replace("(", " ").replace(")", " ").split():
            if chunk.isdigit():
                return int(chunk)
        return None


def connection_from_url(url: str, container: Optional[str] = None) -> Connection:
    """Crea una conexión a partir de una URL de PostgreSQL."""
    parts = urlsplit(url)
    if parts.scheme not in ("postgres", "postgresql"):
        raise ToolError(f"Esquema de URL no soportado: {parts.scheme or '(vacío)'}")
    if not parts.hostname:
        raise ToolError("La URL de conexión no incluye un host.")

    sslmode = None
    for key, value in parse_qsl(parts.query):
        if key == "sslmode":
            sslmode = value

    return Connection(
        user=unquote(parts.username or ""),
        password=unquote(parts.password or ""),
        database=(parts.path or "").lstrip("/") or "postgres",
        container=container,
        host=parts.hostname,
        port=str(parts.port or 5432),
        sslmode=sslmode,
    )


def connection_from_pg_variables(
    environ: Dict[str, str], container: Optional[str] = None
) -> Optional[Connection]:
    """Crea una conexión a partir de las variables PG* del entorno."""
    if not environ.get("PGUSER") and not environ.get("PGHOST"):
        return None
    return Connection(
        user=environ.get("PGUSER", "postgres"),
        password=environ.get("PGPASSWORD", ""),
        database=environ.get("PGDATABASE", "postgres"),
        container=container,
        host=environ.get("PGHOST", "localhost"),
        port=environ.get("PGPORT", "5432"),
        sslmode=environ.get("PGSSLMODE"),
    )

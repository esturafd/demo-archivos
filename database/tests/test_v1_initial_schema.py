"""Pruebas de la migración V1 (esquemas, tablas, barreras y restricciones).

Verifica que la estructura, los privilegios de app_runtime y las protecciones
creadas por V1 se comportan como se espera, antes de que existan las políticas
RLS de V3.

Ejecución:
    python3 tests/test_v1_initial_schema.py --container <nombre>
"""

from __future__ import annotations

from common import Context, Report, TestFailure, main

TITLE = "V1 - esquema inicial"

EXPECTED_TABLES = {
    ("app", "areas"),
    ("app", "user_profiles"),
    ("app", "manager_areas"),
    ("app", "files"),
    ("app", "login_rate_limits"),
    ("audit", "action_logs"),
}

# Tablas que deben tener RLS habilitado con denegación por defecto.
RLS_TABLES = EXPECTED_TABLES

EXPECTED_ENUM_VALUES = {
    "login_succeeded",
    "login_failed",
    "logout",
    "file_uploaded",
    "file_downloaded",
    "file_deleted",
    "access_denied",
}

# Únicas columnas que app_runtime puede actualizar en app.files: el registro
# del borrado lógico. El resto de la fila es inmutable tras la carga.
EXPECTED_UPDATABLE_FILE_COLUMNS = {"deleted_at", "deleted_by_id", "deleted_by_email"}

TEST_FILE_ID = "b0000000-0000-0000-0000-000000000001"


def run(ctx: Context, report: Report) -> None:
    _check_structure(ctx, report)
    _check_privileges(ctx, report)
    _check_default_deny(ctx, report)
    _check_audit_immutability(ctx, report)
    _check_constraints(ctx, report)
    _cleanup(ctx)


# ---------------------------------------------------------------------------
# Estructura
# ---------------------------------------------------------------------------


def _check_structure(ctx: Context, report: Report) -> None:
    print("\n-- Estructura --")

    tables = {
        (row["table_schema"], row["table_name"])
        for row in ctx.owner.query(
            """
            SELECT table_schema, table_name
              FROM information_schema.tables
             WHERE table_schema IN ('app', 'audit')
            """
        )
    }
    missing = EXPECTED_TABLES - tables
    report.check(not missing, "existen las seis tablas esperadas", f"faltan {sorted(missing)}")

    rls = {
        (row["nspname"], row["relname"])
        for row in ctx.owner.query(
            """
            SELECT n.nspname, c.relname
              FROM pg_class c
              JOIN pg_namespace n ON n.oid = c.relnamespace
             WHERE n.nspname IN ('app', 'audit')
               AND c.relrowsecurity
            """
        )
    }
    without_rls = RLS_TABLES - rls
    report.check(
        not without_rls,
        "RLS habilitado en las seis tablas sensibles",
        f"sin RLS: {sorted(without_rls)}",
    )

    values = {
        row["accion"]
        for row in ctx.owner.query(
            "SELECT unnest(enum_range(NULL::audit.audit_action)) AS accion"
        )
    }
    report.check(
        values == EXPECTED_ENUM_VALUES,
        "el enum de acciones del historial está completo",
        f"obtenido {sorted(values)}",
    )


# ---------------------------------------------------------------------------
# Privilegios de app_runtime
# ---------------------------------------------------------------------------


def _check_privileges(ctx: Context, report: Report) -> None:
    print("\n-- Privilegios de app_runtime --")

    for table in ("app.files", "app.areas", "app.user_profiles"):
        has_delete = ctx.owner.scalar(
            f"SELECT has_table_privilege('app_runtime', '{table}', 'DELETE')"
        )
        report.check(
            has_delete == "f",
            f"app_runtime no tiene DELETE sobre {table}",
        )

    has_insert_audit = ctx.owner.scalar(
        "SELECT has_table_privilege('app_runtime', 'audit.action_logs', 'INSERT')"
    )
    report.check(
        has_insert_audit == "f",
        "app_runtime no tiene INSERT sobre el historial",
    )

    updatable = {
        row["column_name"]
        for row in ctx.owner.query(
            """
            SELECT column_name
              FROM information_schema.column_privileges
             WHERE grantee = 'app_runtime'
               AND privilege_type = 'UPDATE'
               AND table_schema = 'app' AND table_name = 'files'
            """
        )
    }
    report.check(
        updatable == EXPECTED_UPDATABLE_FILE_COLUMNS,
        "app_runtime solo puede actualizar las columnas de borrado lógico",
        f"obtenido {sorted(updatable)}",
    )

    # Comprobación de comportamiento, no solo de catálogo. El chequeo de
    # permisos ocurre antes de evaluar las filas, por lo que no hace falta que
    # exista el archivo.
    report.check_fails(
        ctx.app,
        f"UPDATE app.files SET original_name = 'otro.pdf' WHERE id = '{TEST_FILE_ID}'",
        "app_runtime no puede modificar el nombre de un archivo",
        containing="permission denied",
    )
    report.check_fails(
        ctx.app,
        f"DELETE FROM app.files WHERE id = '{TEST_FILE_ID}'",
        "app_runtime no puede eliminar físicamente un archivo",
        containing="permission denied",
    )
    report.check_fails(
        ctx.app,
        "INSERT INTO audit.action_logs (action, ip_address, request_id) "
        "VALUES ('logout', '127.0.0.1', 'v1')",
        "app_runtime no puede escribir directo en el historial",
        containing="permission denied",
    )


# ---------------------------------------------------------------------------
# Denegación por defecto de RLS
# ---------------------------------------------------------------------------


def _check_default_deny(ctx: Context, report: Report) -> None:
    print("\n-- Denegación por defecto (RLS sin políticas) --")

    # Se siembra una fila como propietario para que el conteo sea significativo:
    # con la tabla vacía, leer cero filas no probaría nada.
    ctx.migrator.run(
        f"""
        INSERT INTO app.files
          (id, owner_id, owner_email, area_id, storage_key, original_name, mime_type, size_bytes)
        VALUES
          ('{TEST_FILE_ID}', NULL, 'prueba@example.com', NULL, 'tests/v1/f1',
           'prueba.pdf', 'application/pdf', 10)
        """
    )

    visible = ctx.app.scalar("SELECT count(*) FROM app.files")
    report.check(
        visible == "0",
        "app_runtime no ve ninguna fila aunque existan",
        f"vio {visible}",
    )

    report.check_fails(
        ctx.app,
        """
        INSERT INTO app.files
          (owner_email, storage_key, original_name, mime_type, size_bytes)
        VALUES ('x@example.com', 'tests/v1/f2', 'x.pdf', 'application/pdf', 10)
        """,
        "app_runtime no puede insertar sin una política que lo permita",
        containing="row-level security",
    )


# ---------------------------------------------------------------------------
# Inmutabilidad del historial
# ---------------------------------------------------------------------------


def _check_audit_immutability(ctx: Context, report: Report) -> None:
    print("\n-- Inmutabilidad del historial --")

    report.check_succeeds(
        ctx.migrator,
        """
        BEGIN;
        INSERT INTO audit.action_logs (action, ip_address, request_id)
        VALUES ('login_succeeded', '127.0.0.1', 'v1-insert');
        ROLLBACK;
        """,
        "el propietario puede insertar en el historial",
    )

    # El trigger es por fila, así que hace falta insertar una antes de probar.
    # Todo va en una transacción revertida para no dejar residuos.
    report.check_fails(
        ctx.migrator,
        """
        BEGIN;
        INSERT INTO audit.action_logs (action, ip_address, request_id)
        VALUES ('login_succeeded', '127.0.0.1', 'v1-trigger-update');
        UPDATE audit.action_logs SET action = 'logout';
        ROLLBACK;
        """,
        "el historial rechaza UPDATE (trigger append-only)",
        containing="append-only",
    )

    report.check_fails(
        ctx.migrator,
        """
        BEGIN;
        INSERT INTO audit.action_logs (action, ip_address, request_id)
        VALUES ('login_succeeded', '127.0.0.1', 'v1-trigger-delete');
        DELETE FROM audit.action_logs;
        ROLLBACK;
        """,
        "el historial rechaza DELETE (trigger append-only)",
        containing="append-only",
    )


# ---------------------------------------------------------------------------
# Restricciones
# ---------------------------------------------------------------------------

MAX_SIZE = 20 * 1024 * 1024


def _file_insert(storage_key: str, size: int, columns: str = "", values: str = "") -> str:
    return (
        "INSERT INTO app.files "
        "(owner_email, storage_key, original_name, mime_type, size_bytes"
        f"{columns}) "
        f"VALUES ('x@example.com', '{storage_key}', 'x.pdf', 'application/pdf', {size}"
        f"{values})"
    )


def _check_constraints(ctx: Context, report: Report) -> None:
    print("\n-- Restricciones --")

    report.check_succeeds(
        ctx.migrator,
        _file_insert("tests/v1/limite-ok", MAX_SIZE)
        + "; DELETE FROM app.files WHERE storage_key = 'tests/v1/limite-ok'",
        "se acepta un archivo de exactamente 20 MB",
    )

    report.check_fails(
        ctx.migrator,
        _file_insert("tests/v1/limite-excedido", MAX_SIZE + 1),
        "se rechaza un archivo de más de 20 MB",
        containing="files_size_bytes_ck",
    )

    report.check_fails(
        ctx.migrator,
        _file_insert("tests/v1/borrado", 10, ", deleted_at", ", now()"),
        "se rechaza un borrado lógico incompleto",
        containing="files_deleted_consistent_ck",
    )

    report.check_fails(
        ctx.migrator,
        _file_insert("tests/v1/hash", 10, ", sha256_hex", ", 'no-es-un-hash'"),
        "se rechaza un hash SHA-256 con formato inválido",
        containing="files_sha256_format_ck",
    )

    report.check_fails(
        ctx.migrator,
        "INSERT INTO app.user_profiles (user_id, role) VALUES ('sin-perfil', 'area_head')",
        "se rechaza un jefe de área sin área asignada",
        containing="user_profiles_role_area_ck",
    )

    report.check_fails(
        ctx.migrator,
        "INSERT INTO app.areas (name) VALUES ('   ')",
        "se rechaza un área con nombre en blanco",
        containing="areas_name_not_blank",
    )


def _cleanup(ctx: Context) -> None:
    if not ctx.migrator.succeeds(f"DELETE FROM app.files WHERE id = '{TEST_FILE_ID}'"):
        raise TestFailure("no se pudo limpiar la fila de prueba en app.files")


if __name__ == "__main__":
    main(TITLE, run)

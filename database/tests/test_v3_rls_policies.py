"""Pruebas de la migración V3 (políticas RLS).

Comprueba, con el rol de la API, qué filas resultan visibles y qué operaciones
se permiten para cada rol de negocio. A diferencia de V2, aquí no se evalúan
las funciones de autorización de forma aislada, sino el efecto real de las
políticas sobre las consultas.

Ejecución:
    python3 tests/test_v3_rls_policies.py --container <nombre>
"""

from __future__ import annotations

import fixtures
from common import Context, Report, in_user_context, main

TITLE = "V3 - políticas RLS"

AREA_A = fixtures.AREA_A
AREA_B = fixtures.AREA_B

STANDARD_A = fixtures.STANDARD_A
STANDARD_B = fixtures.STANDARD_B
HEAD_A = fixtures.HEAD_A
MANAGER = fixtures.MANAGER
ADMIN = fixtures.ADMIN

F_STANDARD_A = fixtures.F_STANDARD_A
F_STANDARD_B = fixtures.F_STANDARD_B
F_HEAD_A = fixtures.F_HEAD_A
F_MANAGER = fixtures.F_MANAGER
F_ADMIN = fixtures.F_ADMIN

NAME_STANDARD_A = fixtures.NAME_STANDARD_A
NAME_STANDARD_B = fixtures.NAME_STANDARD_B
NAME_HEAD_A = fixtures.NAME_HEAD_A
NAME_MANAGER = fixtures.NAME_MANAGER
NAME_ADMIN = fixtures.NAME_ADMIN

RLS_VIOLATION = "row-level security"


def run(ctx: Context, report: Report) -> None:
    fixtures.seed(ctx, with_audit=True)
    print("\n(datos de prueba sembrados con el rol propietario)")

    _check_files_visibility(ctx, report)
    _check_files_insert(ctx, report)
    _check_files_delete(ctx, report)
    _check_files_physical_delete(ctx, report)
    _check_user_profiles(ctx, report)
    _check_areas(ctx, report)
    _check_manager_areas(ctx, report)
    _check_audit(ctx, report)
    _check_rate_limits(ctx, report)


# ---------------------------------------------------------------------------
# Utilidades
# ---------------------------------------------------------------------------


def select_as(ctx: Context, user_id: str, query: str) -> list:
    return ctx.app.query(in_user_context(user_id, query))


def column_of(ctx: Context, user_id: str, query: str, column: str) -> set:
    return {row[column] for row in select_as(ctx, user_id, query)}


def check_set(report: Report, message: str, obtained: set, expected: set) -> None:
    report.check(
        obtained == expected,
        message,
        f"obtenido {sorted(obtained)}",
    )


def update_count(ctx: Context, user_id: str, statement: str) -> str:
    """Devuelve cuántas filas cambió una sentencia de escritura.

    Las políticas RLS de UPDATE y DELETE deniegan en silencio: la sentencia
    termina sin error y sin afectar ninguna fila. Por eso aquí no se espera una
    excepción, se cuentan las filas realmente modificadas.
    """
    body = statement.strip().rstrip(";")
    sql = f"WITH changed AS ({body} RETURNING 1) SELECT count(*) AS total FROM changed"
    return ctx.app.scalar(in_user_context(user_id, sql))


def file_insert_sql(
    user_id: str,
    owner_id: str,
    area_id: str,
    storage_key: str,
    extra_columns: str = "",
    extra_values: str = "",
) -> str:
    area = "NULL" if area_id is None else f"'{area_id}'"
    statement = (
        "INSERT INTO app.files "
        "(owner_id, owner_email, area_id, storage_key, original_name, mime_type, size_bytes"
        f"{extra_columns}) "
        f"VALUES ('{owner_id}', 'x@example.com', {area}, '{storage_key}', 'x.pdf', "
        f"'application/pdf', 10{extra_values})"
    )
    return in_user_context(user_id, statement)


def delete_returns(ctx: Context, user_id: str, file_id: str, *, commit: bool = False) -> str:
    """Invoca la eliminación lógica y devuelve 't' o 'f'."""
    email = f"{user_id}@example.com"
    statement = f"SELECT app.soft_delete_file('{file_id}', '{email}') AS borrado"
    return ctx.app.scalar(in_user_context(user_id, statement, commit=commit))


# ---------------------------------------------------------------------------
# Eliminación lógica
# ---------------------------------------------------------------------------


def _check_files_delete(ctx: Context, report: Report) -> None:
    print("\n-- Eliminación lógica --")

    # La API no puede modificar la tabla: el privilegio de UPDATE fue retirado
    # porque PostgreSQL no permite que la fila resultante deje de cumplir las
    # políticas de SELECT (los eliminados son invisibles).
    report.check_fails(
        ctx.app,
        in_user_context(
            ADMIN,
            "UPDATE app.files "
            f"SET deleted_at = now(), deleted_by_id = '{ADMIN}', deleted_by_email = 'x@example.com' "
            f"WHERE id = '{F_STANDARD_A}'",
        ),
        "la API no puede modificar app.files con UPDATE",
        containing="permission denied",
    )

    # Intentos denegados: devuelven false y no tocan el archivo.
    denied = (
        (STANDARD_A, F_STANDARD_B, "un estándar no elimina un archivo de otra área"),
        (HEAD_A, F_STANDARD_A, "un jefe de área no elimina el archivo de un estándar, aunque lo vea"),
        (MANAGER, F_ADMIN, "un gerente no elimina el archivo de un administrador"),
    )
    for user_id, file_id, description in denied:
        report.check(delete_returns(ctx, user_id, file_id) == "f", description)

    report.check(
        ctx.owner.scalar(
            f"SELECT deleted_at IS NULL FROM app.files WHERE id = '{F_STANDARD_B}'"
        )
        == "t",
        "un intento denegado no modifica el archivo",
    )

    # Casos permitidos: se confirman para poder verificar el estado final.
    allowed = (
        (STANDARD_A, F_STANDARD_A, "un estándar elimina su propio archivo"),
        (MANAGER, F_HEAD_A, "un gerente elimina un archivo de su área gestionada"),
        (ADMIN, F_STANDARD_B, "un administrador elimina cualquier archivo"),
    )
    for user_id, file_id, description in allowed:
        report.check(
            delete_returns(ctx, user_id, file_id, commit=True) == "t", description
        )

    report.check(
        delete_returns(ctx, STANDARD_A, F_STANDARD_A) == "f",
        "un archivo ya eliminado no se elimina dos veces",
    )

    # El registro conserva quién y cuándo.
    register = ctx.owner.query(
        f"""
        SELECT deleted_by_id, deleted_by_email, deleted_at IS NOT NULL AS eliminado
          FROM app.files WHERE id = '{F_STANDARD_A}'
        """
    )[0]
    report.check(
        register["eliminado"] == "t"
        and register["deleted_by_id"] == STANDARD_A
        and register["deleted_by_email"] == f"{STANDARD_A}@example.com",
        "el archivo conserva quién lo eliminó",
        f"obtenido {register}",
    )

    manager_register = ctx.owner.scalar(
        f"SELECT deleted_by_id FROM app.files WHERE id = '{F_HEAD_A}'"
    )
    report.check(
        manager_register == MANAGER,
        "la eliminación del gerente queda registrada a su nombre",
        f"obtenido {manager_register}",
    )

    # Un archivo eliminado deja de estar disponible para todos.
    remaining_all = {
        user_id: column_of(ctx, user_id, "SELECT original_name FROM app.files", "original_name")
        for user_id in (STANDARD_A, STANDARD_B, HEAD_A, MANAGER, ADMIN)
    }
    for user_id, remaining in remaining_all.items():
        report.check(
            NAME_STANDARD_A not in remaining and NAME_HEAD_A not in remaining,
            f"{user_id} ya no ve los archivos eliminados",
            f"ve {sorted(remaining)}",
        )

    # Sin contexto o sin correo no se puede eliminar.
    report.check_fails(
        ctx.app,
        f"SELECT app.soft_delete_file('{F_MANAGER}', 'x@example.com')",
        "sin contexto de usuario no se elimina nada",
        containing="No hay usuario",
    )
    report.check_fails(
        ctx.app,
        in_user_context(STANDARD_A, f"SELECT app.soft_delete_file('{F_MANAGER}', NULL)"),
        "el correo de quien elimina es obligatorio",
        containing="obligatorios",
    )


def _check_files_visibility(ctx: Context, report: Report) -> None:
    print("\n-- Archivos visibles por rol --")

    query = "SELECT original_name FROM app.files"
    expected = {
        STANDARD_A: {NAME_STANDARD_A},
        STANDARD_B: {NAME_STANDARD_B},
        HEAD_A: {NAME_HEAD_A, NAME_STANDARD_A},
        MANAGER: {NAME_MANAGER, NAME_STANDARD_A, NAME_HEAD_A},
        ADMIN: {
            NAME_STANDARD_A,
            NAME_STANDARD_B,
            NAME_HEAD_A,
            NAME_MANAGER,
            NAME_ADMIN,
        },
    }

    for user_id, wanted in expected.items():
        obtained = column_of(ctx, user_id, query, "original_name")
        check_set(report, f"{user_id} ve los archivos esperados", obtained, wanted)

    report.check(
        ctx.app.scalar("SELECT count(*) FROM app.files") == "0",
        "sin contexto de usuario no se ve ninguna fila",
    )


# ---------------------------------------------------------------------------
# Carga de archivos
# ---------------------------------------------------------------------------


def _check_files_insert(ctx: Context, report: Report) -> None:
    print("\n-- Carga de archivos --")

    report.check_succeeds(
        ctx.app,
        file_insert_sql(STANDARD_A, STANDARD_A, AREA_A, "tests/new-1"),
        "un estándar carga en su propia área",
    )
    report.check_fails(
        ctx.app,
        file_insert_sql(STANDARD_A, STANDARD_A, AREA_B, "tests/new-2"),
        "un estándar no carga en otra área",
        containing=RLS_VIOLATION,
    )
    report.check_fails(
        ctx.app,
        file_insert_sql(STANDARD_A, STANDARD_A, None, "tests/new-3"),
        "un estándar no carga sin área",
        containing=RLS_VIOLATION,
    )
    report.check_fails(
        ctx.app,
        file_insert_sql(STANDARD_A, STANDARD_B, AREA_A, "tests/new-4"),
        "nadie puede cargar un archivo a nombre de otro",
        containing=RLS_VIOLATION,
    )
    report.check_succeeds(
        ctx.app,
        file_insert_sql(HEAD_A, HEAD_A, AREA_A, "tests/new-5"),
        "un jefe de área carga en su área",
    )
    report.check_succeeds(
        ctx.app,
        file_insert_sql(MANAGER, MANAGER, None, "tests/new-6"),
        "un gerente carga sin área, como archivo propio",
    )
    report.check_fails(
        ctx.app,
        file_insert_sql(MANAGER, MANAGER, AREA_A, "tests/new-7"),
        "un gerente no asigna a un área gestionada",
        containing=RLS_VIOLATION,
    )
    report.check_succeeds(
        ctx.app,
        file_insert_sql(ADMIN, ADMIN, None, "tests/new-8"),
        "un administrador carga sin área",
    )
    report.check_fails(
        ctx.app,
        file_insert_sql(
            STANDARD_A,
            STANDARD_A,
            AREA_A,
            "tests/new-9",
            extra_columns=", deleted_at, deleted_by_id, deleted_by_email",
            extra_values=", now(), 'x', 'x@example.com'",
        ),
        "no se puede cargar un archivo ya eliminado",
        containing=RLS_VIOLATION,
    )

    # Un usuario desactivado pierde el acceso en la siguiente acción.
    ctx.owner.run(
        f"""
        UPDATE app.user_profiles
           SET is_active = false, deactivated_at = now()
         WHERE user_id = '{STANDARD_A}'
        """
    )
    report.check_fails(
        ctx.app,
        file_insert_sql(STANDARD_A, STANDARD_A, AREA_A, "tests/new-10"),
        "un usuario desactivado no puede cargar",
        containing=RLS_VIOLATION,
    )
    ctx.owner.run(
        f"""
        UPDATE app.user_profiles
           SET is_active = true, deactivated_at = NULL
         WHERE user_id = '{STANDARD_A}'
        """
    )



def _check_files_physical_delete(ctx: Context, report: Report) -> None:
    print("\n-- Eliminación física --")

    for user_id in (STANDARD_A, HEAD_A, MANAGER, ADMIN):
        report.check_fails(
            ctx.app,
            in_user_context(user_id, "DELETE FROM app.files WHERE true"),
            f"{user_id} no puede eliminar físicamente archivos",
            containing="permission denied",
        )


# ---------------------------------------------------------------------------
# Perfiles, áreas y asignaciones
# ---------------------------------------------------------------------------


def _check_user_profiles(ctx: Context, report: Report) -> None:
    print("\n-- Perfiles visibles por rol --")

    query = "SELECT user_id FROM app.user_profiles"
    expected = {
        STANDARD_A: {STANDARD_A},
        STANDARD_B: {STANDARD_B},
        HEAD_A: {HEAD_A, STANDARD_A},
        MANAGER: {MANAGER, STANDARD_A, HEAD_A},
        ADMIN: {STANDARD_A, STANDARD_B, HEAD_A, MANAGER, ADMIN},
    }

    for user_id, wanted in expected.items():
        obtained = column_of(ctx, user_id, query, "user_id")
        check_set(report, f"{user_id} ve los perfiles esperados", obtained, wanted)

    report.check(
        update_count(
            ctx,
            HEAD_A,
            "UPDATE app.user_profiles SET is_active = false, deactivated_at = now() "
            f"WHERE user_id = '{STANDARD_A}'",
        )
        == "0",
        "un jefe de área no desactiva a nadie (0 filas afectadas)",
    )
    report.check(
        update_count(
            ctx,
            MANAGER,
            f"UPDATE app.user_profiles SET area_id = '{AREA_A}' WHERE user_id = '{MANAGER}'",
        )
        == "0",
        "un gerente no cambia su propio perfil (0 filas afectadas)",
    )
    report.check(
        update_count(
            ctx,
            ADMIN,
            "UPDATE app.user_profiles SET is_active = false, deactivated_at = now() "
            f"WHERE user_id = '{STANDARD_B}'",
        )
        == "1",
        "un administrador sí desactiva a otro usuario (1 fila afectada)",
    )


def _check_areas(ctx: Context, report: Report) -> None:
    print("\n-- Áreas visibles por rol --")

    query = "SELECT name FROM app.areas"
    expected = {
        STANDARD_A: {"Area A"},
        STANDARD_B: {"Area B"},
        HEAD_A: {"Area A"},
        MANAGER: {"Area A"},
        ADMIN: {"Area A", "Area B"},
    }

    for user_id, wanted in expected.items():
        obtained = column_of(ctx, user_id, query, "name")
        check_set(report, f"{user_id} ve las áreas esperadas", obtained, wanted)

    report.check_fails(
        ctx.app,
        in_user_context(ADMIN, "DELETE FROM app.areas WHERE true"),
        "las áreas no se eliminan",
        containing="permission denied",
    )
    report.check(
        update_count(
            ctx, MANAGER, f"UPDATE app.areas SET is_active = false WHERE id = '{AREA_A}'"
        )
        == "0",
        "un gerente no edita áreas (0 filas afectadas)",
    )
    report.check(
        update_count(
            ctx, ADMIN, f"UPDATE app.areas SET is_active = false WHERE id = '{AREA_B}'"
        )
        == "1",
        "un administrador edita áreas (1 fila afectada)",
    )
    report.check_succeeds(
        ctx.app,
        in_user_context(ADMIN, "INSERT INTO app.areas (name) VALUES ('Area C')"),
        "un administrador crea áreas",
    )
    report.check_fails(
        ctx.app,
        in_user_context(MANAGER, "INSERT INTO app.areas (name) VALUES ('Area D')"),
        "un gerente no crea áreas",
        containing=RLS_VIOLATION,
    )


def _check_manager_areas(ctx: Context, report: Report) -> None:
    print("\n-- Áreas gestionadas --")

    query = "SELECT manager_id FROM app.manager_areas"
    expected = {
        MANAGER: {MANAGER},
        ADMIN: {MANAGER},
        STANDARD_A: set(),
        HEAD_A: set(),
    }

    for user_id, wanted in expected.items():
        obtained = column_of(ctx, user_id, query, "manager_id")
        check_set(report, f"{user_id} ve las asignaciones esperadas", obtained, wanted)

    report.check_fails(
        ctx.app,
        in_user_context(
            MANAGER,
            f"INSERT INTO app.manager_areas (manager_id, area_id) "
            f"VALUES ('{MANAGER}', '{AREA_B}')",
        ),
        "un gerente no se asigna áreas a sí mismo",
        containing=RLS_VIOLATION,
    )
    report.check_succeeds(
        ctx.app,
        in_user_context(
            ADMIN,
            f"INSERT INTO app.manager_areas (manager_id, area_id) "
            f"VALUES ('{MANAGER}', '{AREA_B}')",
        ),
        "un administrador asigna áreas a un gerente",
    )


# ---------------------------------------------------------------------------
# Historial y límite de intentos
# ---------------------------------------------------------------------------


def _check_audit(ctx: Context, report: Report) -> None:
    print("\n-- Historial visible por rol --")

    query = "SELECT request_id FROM audit.action_logs"
    expected = {
        ADMIN: {"seed-1", "seed-2", "seed-3", "seed-4"},
        MANAGER: {"seed-1"},
        STANDARD_A: set(),
        HEAD_A: set(),
    }

    for user_id, wanted in expected.items():
        obtained = column_of(ctx, user_id, query, "request_id")
        check_set(report, f"{user_id} ve los registros esperados", obtained, wanted)

    report.check_fails(
        ctx.app,
        in_user_context(ADMIN, "DELETE FROM audit.action_logs WHERE true"),
        "nadie puede borrar del historial",
        containing="permission denied",
    )
    report.check_fails(
        ctx.app,
        in_user_context(ADMIN, "UPDATE audit.action_logs SET action = 'logout'"),
        "nadie puede modificar el historial",
        containing="permission denied",
    )
    report.check_fails(
        ctx.app,
        in_user_context(
            ADMIN,
            "INSERT INTO audit.action_logs (action, ip_address, request_id) "
            "VALUES ('logout', '127.0.0.1', 'nuevo')",
        ),
        "el historial solo se escribe con append_event",
        containing="permission denied",
    )


def _check_rate_limits(ctx: Context, report: Report) -> None:
    print("\n-- Límite de intentos de inicio de sesión --")

    statement = (
        "INSERT INTO app.login_rate_limits (key_hash, failed_count) VALUES ('k1', 1);\n"
        "UPDATE app.login_rate_limits SET failed_count = 2 WHERE key_hash = 'k1';\n"
        "SELECT failed_count AS total FROM app.login_rate_limits WHERE key_hash = 'k1';\n"
        "DELETE FROM app.login_rate_limits WHERE key_hash = 'k1';"
    )
    rows = select_as(ctx, STANDARD_A, statement)
    report.check(
        rows and rows[0]["total"] == "2",
        "la API puede registrar y actualizar intentos fallidos",
        f"obtenido {rows}",
    )


if __name__ == "__main__":
    main(TITLE, run)

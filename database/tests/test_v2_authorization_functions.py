"""Pruebas de la migración V2 (funciones de autorización y historial).

Siembra datos ficticios (dos áreas, un usuario por rol y un segundo estándar en
otra área) y comprueba la matriz de permisos, el registro controlado del
historial y las guardas del administrador.

Ejecución:
    python3 tests/test_v2_authorization_functions.py --container <nombre>
"""

from __future__ import annotations

import fixtures
from common import Context, Report, TestFailure, in_user_context, main

TITLE = "V2 - funciones de autorización"

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
F_DELETED = fixtures.F_DELETED


def run(ctx: Context, report: Report) -> None:
    fixtures.seed(ctx)
    print("\n(datos de prueba sembrados con el rol propietario)")

    _check_scope(ctx, report)
    _check_fail_closed(ctx, report)
    _check_history_scope(ctx, report)
    _check_append_event(ctx, report)
    _check_atomic_snapshots(ctx, report)
    _check_admin_guard(ctx, report)
    _check_deactivation(ctx, report)


# ---------------------------------------------------------------------------
# Utilidades
# ---------------------------------------------------------------------------


def scope(ctx: Context, user_id: str, expressions: dict) -> dict:
    """Evalúa varias expresiones booleanas con el usuario indicado.

    El contexto de V2 es local a la transacción, así que todo se ejecuta dentro
    de una única transacción. SET LOCAL no produce salida, por lo que el
    resultado es un único conjunto de datos con el JSON de las comprobaciones.
    """
    items = ", ".join(f"'{name}', {expr}" for name, expr in expressions.items())
    sql = in_user_context(user_id, f"SELECT jsonb_build_object({items})::text AS resultado")
    return ctx.app.json(sql)


def check_matrix(report: Report, prefix: str, obtained: dict, expected: dict) -> None:
    for description, wanted in expected.items():
        got = obtained.get(description)
        report.check(
            got == wanted,
            f"{prefix}: {description} = {wanted}",
            f"obtenido {got}",
        )


# ---------------------------------------------------------------------------
# Matriz de permisos
# ---------------------------------------------------------------------------


def _check_scope(ctx: Context, report: Report) -> None:
    print("\n-- Matriz de permisos --")

    standard_a = scope(ctx, STANDARD_A, {
        "lee lo propio": f"app.can_read_file('{F_STANDARD_A}')",
        "elimina lo propio": f"app.can_delete_file('{F_STANDARD_A}')",
        "lee otra área": f"app.can_read_file('{F_STANDARD_B}')",
        "lee al jefe de área": f"app.can_read_file('{F_HEAD_A}')",
        "elimina de otra área": f"app.can_delete_file('{F_STANDARD_B}')",
        "lee un eliminado": f"app.can_read_file('{F_DELETED}')",
        "carga en su área": f"app.can_upload_for_area('{AREA_A}')",
        "carga en otra área": f"app.can_upload_for_area('{AREA_B}')",
        "carga sin área": "app.can_upload_for_area(NULL)",
    })
    check_matrix(report, STANDARD_A, standard_a, {
        "lee lo propio": True,
        "elimina lo propio": True,
        "lee otra área": False,
        "lee al jefe de área": False,
        "elimina de otra área": False,
        "lee un eliminado": False,
        "carga en su área": True,
        "carga en otra área": False,
        "carga sin área": False,
    })

    standard_b = scope(ctx, STANDARD_B, {
        "lee lo propio": f"app.can_read_file('{F_STANDARD_B}')",
        "lee el área A": f"app.can_read_file('{F_STANDARD_A}')",
    })
    check_matrix(report, STANDARD_B, standard_b, {
        "lee lo propio": True,
        "lee el área A": False,
    })

    head_a = scope(ctx, HEAD_A, {
        "lee lo propio": f"app.can_read_file('{F_HEAD_A}')",
        "lee al estándar de su área": f"app.can_read_file('{F_STANDARD_A}')",
        "elimina lo propio": f"app.can_delete_file('{F_HEAD_A}')",
        "elimina al estándar de su área": f"app.can_delete_file('{F_STANDARD_A}')",
        "lee otra área": f"app.can_read_file('{F_STANDARD_B}')",
        "lee al gerente": f"app.can_read_file('{F_MANAGER}')",
        "lee al administrador": f"app.can_read_file('{F_ADMIN}')",
        "carga en su área": f"app.can_upload_for_area('{AREA_A}')",
    })
    check_matrix(report, HEAD_A, head_a, {
        "lee lo propio": True,
        "lee al estándar de su área": True,
        "elimina lo propio": True,
        "elimina al estándar de su área": False,
        "lee otra área": False,
        "lee al gerente": False,
        "lee al administrador": False,
        "carga en su área": True,
    })

    manager = scope(ctx, MANAGER, {
        "lee lo propio": f"app.can_read_file('{F_MANAGER}')",
        "elimina lo propio": f"app.can_delete_file('{F_MANAGER}')",
        "lee al estándar de su área": f"app.can_read_file('{F_STANDARD_A}')",
        "lee al jefe de área de su área": f"app.can_read_file('{F_HEAD_A}')",
        "elimina en su área": f"app.can_delete_file('{F_STANDARD_A}')",
        "lee otra área": f"app.can_read_file('{F_STANDARD_B}')",
        "elimina en otra área": f"app.can_delete_file('{F_STANDARD_B}')",
        "lee al administrador": f"app.can_read_file('{F_ADMIN}')",
        "carga sin área": "app.can_upload_for_area(NULL)",
        "carga en su área gestionada": f"app.can_upload_for_area('{AREA_A}')",
    })
    check_matrix(report, MANAGER, manager, {
        "lee lo propio": True,
        "elimina lo propio": True,
        "lee al estándar de su área": True,
        "lee al jefe de área de su área": True,
        "elimina en su área": True,
        "lee otra área": False,
        "elimina en otra área": False,
        "lee al administrador": False,
        "carga sin área": True,
        "carga en su área gestionada": False,
    })

    admin = scope(ctx, ADMIN, {
        "lee cualquier archivo": f"app.can_read_file('{F_STANDARD_B}')",
        "elimina cualquier archivo": f"app.can_delete_file('{F_STANDARD_B}')",
        "lee un eliminado": f"app.can_read_file('{F_DELETED}')",
        "carga sin área": "app.can_upload_for_area(NULL)",
    })
    check_matrix(report, ADMIN, admin, {
        "lee cualquier archivo": True,
        "elimina cualquier archivo": True,
        "lee un eliminado": False,
        "carga sin área": True,
    })


def _check_fail_closed(ctx: Context, report: Report) -> None:
    print("\n-- Sin contexto (fallo cerrado) --")

    report.check(
        ctx.app.scalar("SELECT app.current_user_id() IS NULL") == "t",
        "sin contexto no hay usuario",
    )
    report.check(
        ctx.app.scalar("SELECT app.current_user_is_active()") == "f",
        "sin contexto no hay usuario activo",
    )
    report.check(
        ctx.app.scalar(f"SELECT app.can_read_file('{F_STANDARD_A}')") == "f",
        "sin contexto no se lee ningún archivo",
    )
    report.check(
        ctx.app.scalar("SELECT app.can_upload_for_area(NULL)") == "f",
        "sin contexto no se puede cargar",
    )


# ---------------------------------------------------------------------------
# Historial
# ---------------------------------------------------------------------------


def _check_history_scope(ctx: Context, report: Report) -> None:
    print("\n-- Alcance del historial --")

    for user_id, label, expected in (
        (STANDARD_A, "estándar", False),
        (HEAD_A, "jefe de área", False),
        (ADMIN, "administrador", True),
    ):
        obtained = scope(ctx, user_id, {
            "ve historial": f"app.can_view_history('{STANDARD_A}', '{AREA_A}')"
        })["ve historial"]
        report.check(
            obtained == expected,
            f"{label}: ve historial = {expected}",
            f"obtenido {obtained}",
        )

    manager = scope(ctx, MANAGER, {
        "ve su propio historial": "app.can_view_history('u-manager', NULL)",
        "ve su área gestionada": f"app.can_view_history('{STANDARD_A}', '{AREA_A}')",
        "ve otra área": f"app.can_view_history('{STANDARD_B}', '{AREA_B}')",
    })
    check_matrix(report, MANAGER, manager, {
        "ve su propio historial": True,
        "ve su área gestionada": True,
        "ve otra área": False,
    })


def _check_append_event(ctx: Context, report: Report) -> None:
    print("\n-- Registro del historial --")

    report.check_succeeds(
        ctx.app,
        """
        BEGIN;
        SELECT app.set_context('u-standard-a', 'req-v2-1', '203.0.113.15');
        SELECT audit.append_event(
          'file_downloaded',
          p_actor_user_id => 'u-standard-a',
          p_actor_email   => 'standard.a@example.com',
          p_file_id       => 'a0000000-0000-0000-0000-000000000001',
          p_file_name     => 'sa.pdf');
        COMMIT;
        """,
        "se registra una descarga con el contexto de la petición",
    )

    report.check_succeeds(
        ctx.app,
        """
        BEGIN;
        SELECT app.set_context(NULL, 'req-v2-2', '203.0.113.16');
        SELECT audit.append_event(
          'login_failed',
          p_actor_email => 'desconocido@example.com',
          p_metadata    => '{"motivo":"credenciales"}'::jsonb);
        COMMIT;
        """,
        "se registra un inicio de sesión fallido sin usuario",
    )

    report.check_fails(
        ctx.app,
        """
        BEGIN;
        SELECT app.set_context('u-standard-a', 'req-v2-3', '203.0.113.17');
        SELECT audit.append_event('file_deleted', p_actor_user_id => 'u-standard-a');
        COMMIT;
        """,
        "se exige p_file_id en las acciones sobre archivos",
        containing="p_file_id",
    )

    report.check_fails(
        ctx.app,
        """
        BEGIN;
        SELECT app.set_context('u-standard-a', 'req-v2-4', '203.0.113.18');
        SELECT audit.append_event('logout', p_metadata => '[1,2]'::jsonb);
        COMMIT;
        """,
        "se rechazan metadatos que no sean un objeto JSON",
        containing="objeto JSON",
    )

    report.check_fails(
        ctx.app,
        "SELECT audit.append_event('logout')",
        "no se puede registrar sin contexto de petición",
        containing="app.request_id",
    )


def _check_atomic_snapshots(ctx: Context, report: Report) -> None:
    print("\n-- Contenido del historial --")

    rows = ctx.migrator.query(
        """
        SELECT action, actor_user_id, actor_email, actor_role, actor_area_name,
               file_name, request_id
          FROM audit.action_logs
         ORDER BY id
        """
    )
    by_action = {row["action"]: row for row in rows}

    download = by_action.get("file_downloaded")
    if download is None:
        raise TestFailure("no se encontró el registro de descarga")

    report.check(
        download["actor_user_id"] == STANDARD_A, "el registro guarda el usuario"
    )
    report.check(
        download["actor_role"] == "standard",
        "el registro deriva el rol del usuario desde la base de datos",
        f"obtenido {download['actor_role']}",
    )
    report.check(
        download["actor_area_name"] == "Area A",
        "el registro guarda el nombre del área al momento de la acción",
        f"obtenido {download['actor_area_name']}",
    )
    report.check(
        download["request_id"] == "req-v2-1", "el registro guarda el identificador de petición"
    )

    failed = by_action.get("login_failed")
    if failed is None:
        raise TestFailure("no se encontró el registro del inicio de sesión fallido")

    report.check(
        not failed["actor_user_id"] and not failed["actor_role"],
        "un inicio fallido sin usuario no inventa usuario ni rol",
    )
    report.check(
        failed["actor_email"] == "desconocido@example.com",
        "un inicio fallido conserva el correo intentado",
    )


# ---------------------------------------------------------------------------
# Guardas
# ---------------------------------------------------------------------------


def _check_admin_guard(ctx: Context, report: Report) -> None:
    print("\n-- Guardas del administrador --")

    report.check_fails(
        ctx.migrator,
        """
        BEGIN;
        SET LOCAL app.user_id = 'u-admin';
        UPDATE app.user_profiles SET role = 'manager' WHERE user_id = 'u-admin';
        ROLLBACK;
        """,
        "un administrador no puede cambiar su propio rol",
        containing="su propio rol",
    )

    report.check_fails(
        ctx.migrator,
        """
        BEGIN;
        SET LOCAL app.user_id = 'u-admin';
        UPDATE app.user_profiles SET is_active = false, deactivated_at = now()
         WHERE user_id = 'u-admin';
        ROLLBACK;
        """,
        "un administrador no puede desactivarse a sí mismo",
        containing="desactivarse",
    )

    report.check_succeeds(
        ctx.migrator,
        """
        BEGIN;
        SET LOCAL app.user_id = 'u-admin';
        UPDATE app.user_profiles SET is_active = false, deactivated_at = now()
         WHERE user_id = 'u-standard-b';
        ROLLBACK;
        """,
        "un administrador sí puede modificar a otros usuarios",
    )


def _check_deactivation(ctx: Context, report: Report) -> None:
    print("\n-- Usuario desactivado --")

    ctx.owner.run(
        f"""
        UPDATE app.user_profiles
           SET is_active = false, deactivated_at = now()
         WHERE user_id = '{STANDARD_B}'
        """
    )

    obtained = scope(ctx, STANDARD_B, {
        "está activo": "app.current_user_is_active()",
        "lee lo propio": f"app.can_read_file('{F_STANDARD_B}')",
    })
    check_matrix(report, "usuario desactivado", obtained, {
        "está activo": False,
        "lee lo propio": False,
    })

    # Se restaura para dejar los datos de prueba en su estado inicial.
    ctx.owner.run(
        f"""
        UPDATE app.user_profiles
           SET is_active = true, deactivated_at = NULL
         WHERE user_id = '{STANDARD_B}'
        """
    )


if __name__ == "__main__":
    main(TITLE, run)

"""Pruebas de la migración V5 (revocación de una sesión por su identificador).

Comprueba que una sesión solo la puede revocar su propietario o un
administrador, que la revocación es real (el token deja de valer) y que sin
contexto se deniega.

Ejecución:
    python3 tests/test_v5_revoke_session_by_id.py --container <nombre>
"""

from __future__ import annotations

import fixtures
from common import Context, Report, in_user_context, main

TITLE = "V5 - revocación de sesión por id"

STANDARD_A = fixtures.STANDARD_A
STANDARD_B = fixtures.STANDARD_B
ADMIN = fixtures.ADMIN

TOKEN_A = "ab" * 32
TOKEN_B = "cd" * 32


def run(ctx: Context, report: Report) -> None:
    fixtures.seed(ctx)
    print("\n(datos de prueba sembrados con el rol propietario)")

    _check_privileges(ctx, report)
    _check_scope(ctx, report)


# ---------------------------------------------------------------------------
# Utilidades
# ---------------------------------------------------------------------------


def create_session(ctx: Context, user_id: str, token: str) -> None:
    ctx.app.run(
        f"SELECT app.create_session('{user_id}'::uuid, '{token}', "
        "'203.0.113.9'::inet, 'prueba/1.0', interval '1 hour')"
    )


def session_id(ctx: Context, token: str) -> str:
    # app_runtime no tiene privilegio sobre app.sessions: se consulta con el rol
    # de migración, que sí puede, solo para conocer el identificador.
    return ctx.migrator.scalar(f"SELECT id FROM app.sessions WHERE token_hash = '{token}'")


def revoke_as(ctx: Context, user_id: str, target_id: str) -> str:
    """Devuelve 't'/'f' tras intentar revocar como el usuario indicado."""
    sql = in_user_context(
        user_id,
        f"SELECT app.revoke_session_by_id('{target_id}'::uuid) AS revocada",
        commit=True,
    )
    return ctx.app.scalar(sql)


def is_valid(ctx: Context, token: str) -> bool:
    return ctx.app.scalar(f"SELECT app.validate_session('{token}') IS NOT NULL") == "t"


# ---------------------------------------------------------------------------
# Privilegios
# ---------------------------------------------------------------------------


def _check_privileges(ctx: Context, report: Report) -> None:
    print("\n-- Privilegios --")

    report.check(
        ctx.app.scalar(
            "SELECT has_function_privilege("
            "'app_runtime', 'app.revoke_session_by_id(uuid)', 'EXECUTE')"
        )
        == "t",
        "app_runtime puede ejecutar la función",
    )

    # La concesión se retiró de PUBLIC: ninguna entrada del ACL debe empezar
    # por '=' (que es como aparece PUBLIC como beneficiario).
    acl = ctx.app.scalar(
        "SELECT coalesce(proacl::text, '') "
        "FROM pg_proc WHERE proname = 'revoke_session_by_id'"
    )
    entradas = acl.strip("{}").split(",")
    concede_a_runtime = any(entry.startswith("app_runtime=") for entry in entradas)
    concede_a_publico = any(entry.startswith("=") for entry in entradas)
    report.check(
        concede_a_runtime and not concede_a_publico,
        "el permiso está concedido solo a app_runtime",
        f"ACL obtenido: {acl}",
    )

    report.check(
        ctx.app.scalar(
            "SELECT has_function_privilege("
            "'app_runtime', 'app.session_id_for_token(text)', 'EXECUTE')"
        )
        == "t",
        "app_runtime puede resolver la sesión actual",
    )


# ---------------------------------------------------------------------------
# Alcance
# ---------------------------------------------------------------------------


def _check_scope(ctx: Context, report: Report) -> None:
    print("\n-- Alcance de la revocación --")

    create_session(ctx, STANDARD_A, TOKEN_A)
    create_session(ctx, STANDARD_B, TOKEN_B)

    own_id = session_id(ctx, TOKEN_A)
    other_id = session_id(ctx, TOKEN_B)

    report.check(own_id is not None and other_id is not None, "las sesiones se crearon")

    # La sesión actual se puede resolver a partir del token.
    report.check(
        ctx.app.scalar(f"SELECT app.session_id_for_token('{TOKEN_A}')") == own_id,
        "se resuelve la sesión actual a partir del token",
    )
    report.check(
        ctx.app.scalar("SELECT app.session_id_for_token('" + "ff" * 32 + "') IS NULL") == "t",
        "un token desconocido no resuelve ninguna sesión",
    )

    # Un usuario no puede revocar la sesión de otro.
    report.check(
        revoke_as(ctx, STANDARD_A, other_id) == "f",
        "un usuario no revoca la sesión de otro",
    )
    report.check(
        is_valid(ctx, TOKEN_B),
        "la sesión ajena sigue válida tras el intento",
    )

    # Sí puede revocar la propia.
    report.check(
        revoke_as(ctx, STANDARD_A, own_id) == "t",
        "un usuario revoca su propia sesión",
    )
    report.check(
        not is_valid(ctx, TOKEN_A),
        "la sesión propia deja de valer tras revocarla",
    )

    # Revocar dos veces no cuenta como éxito.
    report.check(
        revoke_as(ctx, STANDARD_A, own_id) == "f",
        "revocar de nuevo devuelve false",
    )

    # Un administrador puede revocar la de cualquier usuario.
    report.check(
        revoke_as(ctx, ADMIN, other_id) == "t",
        "un administrador revoca la sesión de otro",
    )
    report.check(
        not is_valid(ctx, TOKEN_B),
        "la sesión revocada por el administrador deja de valer",
    )

    # Sin contexto de usuario, se deniega.
    report.check(
        ctx.app.scalar(f"SELECT app.revoke_session_by_id('{other_id}'::uuid)") == "f",
        "sin contexto de usuario no se revoca nada",
    )


if __name__ == "__main__":
    main(TITLE, run)

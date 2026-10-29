"""Pruebas de la migración V4 (índices).

Verifica la definición exacta de cada índice: tabla, método, unicidad, columnas
en orden y predicado parcial. Comprueba además el comportamiento del índice
único sobre el nombre de área.

Sobre los planes de ejecución: no se comprueban a propósito. La elección de
índice depende del volumen de datos, de las estadísticas y de RLS (las políticas
añaden un security barrier con funciones no leakproof, que puede impedir que la
condición del usuario llegue al índice). Una aserción sobre el plan sería
frágil: cambiaría al crecer la tabla sin que el índice haya empeorado. Lo que la
migración garantiza es la definición del índice, y eso es lo que se verifica.

Ejecución:
    python3 tests/test_v4_indexes.py --container <nombre>
"""

from __future__ import annotations

import fixtures
from common import Context, Report, main

TITLE = "V4 - índices"

# nombre -> (tabla, método, único, parcial, columnas, predicado)
EXPECTED_INDEXES = {
    "areas_name_lower_uq": (
        "areas", "btree", True, False, ("lower(name)",), None
    ),
    "users_email_lower_uq": (
        "users", "btree", True, False, ("lower(email)",), None
    ),
    "users_area_role_idx": (
        "users", "btree", False, False, ("area_id", "role"), None
    ),
    "sessions_token_hash_uq": (
        "sessions", "btree", True, False, ("token_hash",), None
    ),
    "sessions_user_active_idx": (
        "sessions", "btree", False, True, ("user_id",), "(revoked_at IS NULL)"
    ),
    "sessions_expires_idx": (
        "sessions", "btree", False, False, ("expires_at",), None
    ),
    "manager_areas_area_manager_idx": (
        "manager_areas", "btree", False, False, ("area_id", "manager_id"), None
    ),
    "files_owner_uploaded_idx": (
        "files", "btree", False, True,
        ("owner_id", "uploaded_at", "id"), "(deleted_at IS NULL)",
    ),
    "files_area_uploaded_idx": (
        "files", "btree", False, True,
        ("area_id", "uploaded_at", "id"), "(deleted_at IS NULL)",
    ),
    "files_name_trgm_idx": (
        "files", "gin", False, True, ("original_name",), "(deleted_at IS NULL)"
    ),
    "action_logs_occurred_idx": (
        "action_logs", "btree", False, False, ("occurred_at", "id"), None
    ),
    "action_logs_action_occurred_idx": (
        "action_logs", "btree", False, False, ("action", "occurred_at", "id"), None
    ),
    "action_logs_actor_area_occurred_idx": (
        "action_logs", "btree", False, False,
        ("actor_area_id", "occurred_at", "id"), None,
    ),
    "action_logs_actor_user_occurred_idx": (
        "action_logs", "btree", False, False,
        ("actor_user_id", "occurred_at", "id"), None,
    ),
}


def run(ctx: Context, report: Report) -> None:
    fixtures.seed(ctx)
    print("\n(datos de prueba sembrados con el rol propietario)")

    _check_extension(ctx, report)
    _check_definitions(ctx, report)
    _check_trigram_opclass(ctx, report)
    _check_unique_area_name(ctx, report)
    _check_unique_email(ctx, report)


# ---------------------------------------------------------------------------
# Definiciones
# ---------------------------------------------------------------------------


def _check_extension(ctx: Context, report: Report) -> None:
    print("\n-- Extensión de búsqueda --")

    report.check(
        ctx.owner.scalar("SELECT count(*) FROM pg_extension WHERE extname = 'pg_trgm'") == "1",
        "la extensión pg_trgm está instalada",
    )


def _check_definitions(ctx: Context, report: Report) -> None:
    print("\n-- Definición de los índices --")

    rows = ctx.owner.query(
        """
        SELECT c.relname                              AS index_name,
               t.relname                              AS table_name,
               am.amname                              AS method,
               i.indisunique                          AS is_unique,
               i.indpred IS NOT NULL                  AS is_partial,
               coalesce(pg_get_expr(i.indpred, i.indrelid), '') AS predicate,
               pg_get_indexdef(i.indexrelid, 1, true) AS k1,
               CASE WHEN i.indnkeyatts >= 2
                    THEN pg_get_indexdef(i.indexrelid, 2, true) END AS k2,
               CASE WHEN i.indnkeyatts >= 3
                    THEN pg_get_indexdef(i.indexrelid, 3, true) END AS k3
          FROM pg_index i
          JOIN pg_class c     ON c.oid = i.indexrelid
          JOIN pg_class t     ON t.oid = i.indrelid
          JOIN pg_namespace n ON n.oid = t.relnamespace
          JOIN pg_am am       ON am.oid = c.relam
         WHERE n.nspname IN ('app', 'audit')
        """
    )

    found = {}
    for row in rows:
        columns = tuple(
            value for value in (row["k1"], row["k2"], row["k3"]) if value
        )
        found[row["index_name"]] = (
            row["table_name"],
            row["method"],
            row["is_unique"] == "t",
            row["is_partial"] == "t",
            columns,
            row["predicate"] or None,
        )

    for name, expected in EXPECTED_INDEXES.items():
        obtained = found.get(name)
        report.check(
            obtained == expected,
            f"{name} tiene la definición esperada",
            f"esperado {expected}, obtenido {obtained}",
        )


def _check_trigram_opclass(ctx: Context, report: Report) -> None:
    print("\n-- Clase de operadores del índice de texto --")

    opclass = ctx.owner.scalar(
        """
        SELECT oc.opcname
          FROM pg_index i
          JOIN pg_class c    ON c.oid = i.indexrelid
          JOIN pg_opclass oc ON oc.oid = i.indclass[0]
         WHERE c.relname = 'files_name_trgm_idx'
        """
    )
    report.check(
        opclass == "gin_trgm_ops",
        "el índice de nombre usa la clase de operadores de trigramas",
        f"obtenido {opclass!r}",
    )


def _check_unique_area_name(ctx: Context, report: Report) -> None:
    print("\n-- Nombres de área únicos --")

    report.check_succeeds(
        ctx.owner,
        "INSERT INTO app.areas (name) VALUES ('Area Nova')",
        "se acepta un área con nombre nuevo",
    )
    report.check_fails(
        ctx.owner,
        "INSERT INTO app.areas (name) VALUES ('AREA NOVA')",
        "se rechaza el mismo nombre con otras mayúsculas",
        containing="areas_name_lower_uq",
    )


def _check_unique_email(ctx: Context, report: Report) -> None:
    print("\n-- Correos únicos --")

    report.check_fails(
        ctx.owner,
        "INSERT INTO app.users (email, password_hash, name, role) VALUES "
        f"('{fixtures.EMAIL_STANDARD_A.upper()}', '{fixtures.PASSWORD_HASH}', 'Duplicado', 'admin')",
        "se rechaza un correo ya registrado, sin distinguir mayúsculas",
        containing="users_email_lower_uq",
    )


if __name__ == "__main__":
    main(TITLE, run)

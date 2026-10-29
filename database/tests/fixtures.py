"""Datos de prueba compartidos por las pruebas de las migraciones.

Siempre son datos ficticios. Se siembran con el rol propietario, porque
app_migrator no tiene permiso para insertar en neon_auth."user": en un ambiente
real los usuarios se crean con el API de administración de Neon Auth.

El conjunto reproduce el escenario del enunciado: dos áreas, un usuario por rol
y un segundo usuario estándar en otra área para comprobar el aislamiento.
"""

from __future__ import annotations

from common import Context

AREA_A = "11111111-1111-1111-1111-111111111111"
AREA_B = "22222222-2222-2222-2222-222222222222"

STANDARD_A = "u-standard-a"
STANDARD_B = "u-standard-b"
HEAD_A = "u-head-a"
MANAGER = "u-manager"
ADMIN = "u-admin"

USERS = (STANDARD_A, STANDARD_B, HEAD_A, MANAGER, ADMIN)

F_STANDARD_A = "a0000000-0000-0000-0000-000000000001"
F_STANDARD_B = "a0000000-0000-0000-0000-000000000002"
F_HEAD_A = "a0000000-0000-0000-0000-000000000003"
F_MANAGER = "a0000000-0000-0000-0000-000000000004"
F_ADMIN = "a0000000-0000-0000-0000-000000000005"
F_DELETED = "a0000000-0000-0000-0000-000000000006"

# Nombres legibles, para comprobar visibilidad sin depender de los UUID.
NAME_STANDARD_A = "sa.pdf"
NAME_STANDARD_B = "sb.pdf"
NAME_HEAD_A = "ha.pdf"
NAME_MANAGER = "mg.pdf"
NAME_ADMIN = "ad.pdf"
NAME_DELETED = "borrado.pdf"

TRUNCATE_SQL = """
TRUNCATE neon_auth."user", app.areas, app.user_profiles, app.manager_areas,
         app.files, app.login_rate_limits, audit.action_logs
  RESTART IDENTITY CASCADE;
"""

SEED_SQL = f"""
INSERT INTO app.areas (id, name) VALUES
  ('{AREA_A}', 'Area A'),
  ('{AREA_B}', 'Area B');

INSERT INTO neon_auth."user" (id, email) VALUES
  ('{STANDARD_A}', 'standard.a@example.com'),
  ('{STANDARD_B}', 'standard.b@example.com'),
  ('{HEAD_A}',     'head.a@example.com'),
  ('{MANAGER}',    'manager@example.com'),
  ('{ADMIN}',      'admin@example.com');

INSERT INTO app.user_profiles (user_id, role, area_id) VALUES
  ('{STANDARD_A}', 'standard',  '{AREA_A}'),
  ('{STANDARD_B}', 'standard',  '{AREA_B}'),
  ('{HEAD_A}',     'area_head', '{AREA_A}'),
  ('{MANAGER}',    'manager',   NULL),
  ('{ADMIN}',      'admin',     NULL);

INSERT INTO app.manager_areas (manager_id, area_id) VALUES ('{MANAGER}', '{AREA_A}');

INSERT INTO app.files
  (id, owner_id, owner_email, area_id, storage_key, original_name, mime_type, size_bytes)
VALUES
  ('{F_STANDARD_A}', '{STANDARD_A}', 'standard.a@example.com', '{AREA_A}',
   'tests/f1', '{NAME_STANDARD_A}', 'application/pdf', 10),
  ('{F_STANDARD_B}', '{STANDARD_B}', 'standard.b@example.com', '{AREA_B}',
   'tests/f2', '{NAME_STANDARD_B}', 'application/pdf', 10),
  ('{F_HEAD_A}', '{HEAD_A}', 'head.a@example.com', '{AREA_A}',
   'tests/f3', '{NAME_HEAD_A}', 'application/pdf', 10),
  ('{F_MANAGER}', '{MANAGER}', 'manager@example.com', NULL,
   'tests/f4', '{NAME_MANAGER}', 'application/pdf', 10),
  ('{F_ADMIN}', '{ADMIN}', 'admin@example.com', NULL,
   'tests/f5', '{NAME_ADMIN}', 'application/pdf', 10),
  ('{F_DELETED}', '{STANDARD_A}', 'standard.a@example.com', '{AREA_A}',
   'tests/f6', '{NAME_DELETED}', 'application/pdf', 10);

UPDATE app.files
   SET deleted_at = now(),
       deleted_by_id = '{STANDARD_A}',
       deleted_by_email = 'standard.a@example.com'
 WHERE id = '{F_DELETED}';
"""

# Eventos del historial. Se insertan directamente como propietario para poder
# fijar el área del actor y probar el alcance del gerente.
AUDIT_SEED_SQL = f"""
INSERT INTO audit.action_logs
  (action, actor_user_id, actor_email, actor_role, actor_area_id, actor_area_name,
   file_id, file_name, ip_address, request_id)
VALUES
  ('file_downloaded', '{STANDARD_A}', 'standard.a@example.com', 'standard',
   '{AREA_A}', 'Area A', '{F_STANDARD_A}', '{NAME_STANDARD_A}',
   '203.0.113.10', 'seed-1'),
  ('file_uploaded', '{STANDARD_B}', 'standard.b@example.com', 'standard',
   '{AREA_B}', 'Area B', '{F_STANDARD_B}', '{NAME_STANDARD_B}',
   '203.0.113.11', 'seed-2'),
  ('logout', '{ADMIN}', 'admin@example.com', 'admin',
   NULL, NULL, NULL, NULL,
   '203.0.113.12', 'seed-3'),
  ('login_failed', NULL, 'desconocido@example.com', NULL,
   NULL, NULL, NULL, NULL,
   '203.0.113.13', 'seed-4');
"""


def seed(ctx: Context, *, with_audit: bool = False) -> None:
    """Deja el ambiente con el conjunto de datos de prueba."""
    ctx.owner.run(TRUNCATE_SQL)
    ctx.owner.run(SEED_SQL)
    if with_audit:
        ctx.owner.run(AUDIT_SEED_SQL)

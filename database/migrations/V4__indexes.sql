-- V4: índices.
--
-- Hasta aquí el modelo funciona, pero cada consulta recorre las tablas
-- completas. Esta migración añade los índices que sostienen las consultas que
-- la aplicación realiza de verdad:
--
--   - Listado de archivos por propietario, ordenado por fecha y paginado.
--   - Listado de archivos por área, para jefes y gerentes.
--   - Búsqueda por nombre.
--   - Historial filtrado por tipo de acción y por actor, ordenado y paginado.
--   - Resolución del alcance: usuarios de un área y áreas de un gerente.
--
-- Decisiones:
--
--   - Los índices de archivos son parciales (WHERE deleted_at IS NULL). Un
--     archivo eliminado no se lista nunca, así que no aporta nada indexarlo y
--     el índice ocupa menos.
--
--   - Se añaden índices descendentes por (fecha, id) para poder paginar por
--     cursor en lugar de con OFFSET.
--
--   - No se crean índices para las claves foráneas files.owner_id, files.area_id
--     ni users.area_id. Solo harían falta al borrar físicamente un área o un
--     usuario, y esos borrados no forman parte del sistema: las áreas y los
--     usuarios se desactivan. Añadirlos costaría escrituras en cada carga sin
--     beneficio real.
--
--   - No se indexa users.is_active por separado: es un booleano de muy
--     baja cardinalidad y las comprobaciones de estado usan la clave primaria.
--
--   - Las sesiones se indexan por su hash de token, que es la clave de búsqueda
--     en cada petición, y por usuario para poder revocarlas en bloque.


-- ---------------------------------------------------------------------------
-- Búsqueda por texto
-- ---------------------------------------------------------------------------
-- pg_trgm permite que ILIKE '%texto%' use un índice GIN.
--
-- app_migrator no puede crear objetos en el esquema public (PostgreSQL 15+
-- retiró ese permiso), así que la extensión se instala en app. Si el proyecto
-- ya la tuviera instalada en otro esquema, se reutiliza el que exista.

DO $do$
DECLARE
  extension_schema text;
BEGIN
  SELECT n.nspname
    INTO extension_schema
    FROM pg_extension e
    JOIN pg_namespace n ON n.oid = e.extnamespace
   WHERE e.extname = 'pg_trgm';

  IF extension_schema IS NULL THEN
    CREATE EXTENSION pg_trgm SCHEMA app;
    extension_schema := 'app';
  END IF;

  EXECUTE format(
    'CREATE INDEX files_name_trgm_idx ON app.files '
    'USING gin (original_name %I.gin_trgm_ops) WHERE deleted_at IS NULL',
    extension_schema
  );

  RAISE NOTICE 'Índice de búsqueda por nombre creado (pg_trgm en %).', extension_schema;
END
$do$;

COMMENT ON INDEX app.files_name_trgm_idx IS
  'Búsqueda por nombre con ILIKE sobre archivos no eliminados.';

COMMENT ON EXTENSION pg_trgm IS
  'Trigramas para búsquedas por texto parcial.';


-- ---------------------------------------------------------------------------
-- Áreas
-- ---------------------------------------------------------------------------

CREATE UNIQUE INDEX areas_name_lower_uq
  ON app.areas (lower(name));

COMMENT ON INDEX app.areas_name_lower_uq IS
  'Evita áreas con el mismo nombre ignorando mayúsculas y minúsculas.';


-- ---------------------------------------------------------------------------
-- Usuarios
-- ---------------------------------------------------------------------------

CREATE UNIQUE INDEX users_email_lower_uq
  ON app.users (lower(email));

COMMENT ON INDEX app.users_email_lower_uq IS
  'Evita cuentas con el mismo correo ignorando mayúsculas y minúsculas, y resuelve el login.';


CREATE INDEX users_area_role_idx
  ON app.users (area_id, role);

COMMENT ON INDEX app.users_area_role_idx IS
  'Usuarios de un área, por rol. Sostiene el alcance del jefe de área y del gerente.';


-- ---------------------------------------------------------------------------
-- Sesiones
-- ---------------------------------------------------------------------------

CREATE UNIQUE INDEX sessions_token_hash_uq
  ON app.sessions (token_hash);

COMMENT ON INDEX app.sessions_token_hash_uq IS
  'Clave de búsqueda de la sesión en cada petición.';


CREATE INDEX sessions_user_active_idx
  ON app.sessions (user_id)
  WHERE revoked_at IS NULL;

COMMENT ON INDEX app.sessions_user_active_idx IS
  'Sesiones activas de un usuario, para revocarlas en bloque.';


CREATE INDEX sessions_expires_idx
  ON app.sessions (expires_at);

COMMENT ON INDEX app.sessions_expires_idx IS
  'Para purgar sesiones expiradas.';


-- ---------------------------------------------------------------------------
-- Áreas gestionadas
-- ---------------------------------------------------------------------------
-- La clave primaria ya cubre las búsquedas por gerente. Este índice cubre la
-- dirección contraria: qué gerentes tiene un área.

CREATE INDEX manager_areas_area_manager_idx
  ON app.manager_areas (area_id, manager_id);

COMMENT ON INDEX app.manager_areas_area_manager_idx IS
  'Gerentes de un área.';


-- ---------------------------------------------------------------------------
-- Archivos
-- ---------------------------------------------------------------------------

CREATE INDEX files_owner_uploaded_idx
  ON app.files (owner_id, uploaded_at DESC, id)
  WHERE deleted_at IS NULL;

COMMENT ON INDEX app.files_owner_uploaded_idx IS
  'Archivos de un propietario, del más reciente al más antiguo, para paginar por cursor.';


CREATE INDEX files_area_uploaded_idx
  ON app.files (area_id, uploaded_at DESC, id)
  WHERE deleted_at IS NULL;

COMMENT ON INDEX app.files_area_uploaded_idx IS
  'Archivos de un área, del más reciente al más antiguo. Alcance del jefe y del gerente.';


-- ---------------------------------------------------------------------------
-- Historial
-- ---------------------------------------------------------------------------

CREATE INDEX action_logs_occurred_idx
  ON audit.action_logs (occurred_at DESC, id);

COMMENT ON INDEX audit.action_logs_occurred_idx IS
  'Historial completo ordenado por fecha, para la consulta del administrador.';


CREATE INDEX action_logs_action_occurred_idx
  ON audit.action_logs (action, occurred_at DESC, id);

COMMENT ON INDEX audit.action_logs_action_occurred_idx IS
  'Filtro del historial por tipo de acción.';


CREATE INDEX action_logs_actor_area_occurred_idx
  ON audit.action_logs (actor_area_id, occurred_at DESC, id);

COMMENT ON INDEX audit.action_logs_actor_area_occurred_idx IS
  'Historial de un área, para el alcance del gerente.';


CREATE INDEX action_logs_actor_user_occurred_idx
  ON audit.action_logs (actor_user_id, occurred_at DESC, id);

COMMENT ON INDEX audit.action_logs_actor_user_occurred_idx IS
  'Historial de un usuario concreto.';

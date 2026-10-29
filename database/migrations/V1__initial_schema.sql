-- V1: esquemas, tipos y tablas base.
--
-- Esta migración crea el modelo de datos del proyecto.
--
-- Requisitos previos:
--   - El ambiente debe haber sido aprovisionado con
--     database/provision-environment.sh, que crea los roles app_migrator y
--     app_runtime y otorga a app_migrator los privilegios necesarios sobre
--     neon_auth. La creación y configuración de roles NO es tarea de Flyway.
--   - Neon Auth habilitado; debe existir el esquema neon_auth.
--   - Flyway conectado como app_migrator.
--
-- Resultado de esta migración:
--   - Esquemas, tipos, tablas, restricciones y triggers de integridad.
--   - RLS habilitado en las tablas sensibles con denegación por defecto.
--     Las políticas concretas se crean en V3. Hasta entonces, app_runtime
--     no puede leer ni escribir ninguna fila.
--   - Privilegios de app_runtime sobre los esquemas app y audit, que esta
--     migración crea.


-- ---------------------------------------------------------------------------
-- Validación previa
-- ---------------------------------------------------------------------------
-- Comprueba que Neon Auth ya exista para no fallar de forma poco clara al
-- crear las claves foráneas. La consulta al catálogo no requiere privilegios
-- sobre neon_auth.

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = 'neon_auth'
       AND c.relname = 'user'
  ) THEN
    RAISE EXCEPTION
      'No existe neon_auth."user". Habilita Neon Auth antes de migrar.';
  END IF;
END;
$$;


CREATE SCHEMA IF NOT EXISTS app;
CREATE SCHEMA IF NOT EXISTS audit;

COMMENT ON SCHEMA app IS
  'Datos de negocio: perfiles, áreas, archivos y control de intentos de acceso.';

COMMENT ON SCHEMA audit IS
  'Historial append-only de acciones de la aplicación.';


-- ---------------------------------------------------------------------------
-- Tipos enumerados
-- ---------------------------------------------------------------------------

CREATE TYPE app.app_role AS ENUM (
  'standard',
  'area_head',
  'manager',
  'admin'
);

COMMENT ON TYPE app.app_role IS
  'Rol de negocio. Cada usuario tiene exactamente un rol.';

CREATE TYPE audit.audit_action AS ENUM (
  'login_succeeded',
  'login_failed',
  'logout',
  'file_uploaded',
  'file_downloaded',
  'file_deleted',
  'access_denied'
);

COMMENT ON TYPE audit.audit_action IS
  'Acciones registradas en el historial funcional.';


-- ---------------------------------------------------------------------------
-- Áreas
-- ---------------------------------------------------------------------------

CREATE TABLE app.areas (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL,
  description text,
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),

  CONSTRAINT areas_name_not_blank
    CHECK (btrim(name) <> '')
);

COMMENT ON TABLE app.areas IS
  'Áreas de la organización. Se desactivan; no se eliminan desde la aplicación.';

COMMENT ON COLUMN app.areas.is_active IS
  'Indica si el área admite nuevas asignaciones o cargas.';


-- ---------------------------------------------------------------------------
-- Perfiles de aplicación
-- ---------------------------------------------------------------------------

CREATE TABLE app.user_profiles (
  user_id text PRIMARY KEY,
  role app.app_role NOT NULL,
  area_id uuid,
  is_active boolean NOT NULL DEFAULT true,
  deactivated_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),

  CONSTRAINT user_profiles_user_fk
    FOREIGN KEY (user_id)
    REFERENCES neon_auth."user" (id)
    ON DELETE CASCADE,

  CONSTRAINT user_profiles_area_fk
    FOREIGN KEY (area_id)
    REFERENCES app.areas (id)
    ON DELETE RESTRICT,

  CONSTRAINT user_profiles_role_area_ck
    CHECK (
      (role IN ('standard', 'area_head') AND area_id IS NOT NULL)
      OR
      (role IN ('manager', 'admin') AND area_id IS NULL)
    ),

  CONSTRAINT user_profiles_active_ck
    CHECK (
      (is_active AND deactivated_at IS NULL)
      OR
      (NOT is_active AND deactivated_at IS NOT NULL)
    )
);

COMMENT ON TABLE app.user_profiles IS
  'Datos de autorización asociados a un usuario de neon_auth.';

COMMENT ON COLUMN app.user_profiles.user_id IS
  'Identificador de neon_auth."user".id, almacenado como texto.';

COMMENT ON COLUMN app.user_profiles.area_id IS
  'Área propia para standard y area_head; NULL para manager y admin.';

COMMENT ON COLUMN app.user_profiles.is_active IS
  'Barrera de autorización de la aplicación, independiente del ban de Auth.';


-- ---------------------------------------------------------------------------
-- Áreas gestionadas por gerentes
-- ---------------------------------------------------------------------------

CREATE TABLE app.manager_areas (
  manager_id text NOT NULL,
  area_id uuid NOT NULL,
  assigned_at timestamptz NOT NULL DEFAULT now(),
  assigned_by text,

  CONSTRAINT manager_areas_pk
    PRIMARY KEY (manager_id, area_id),

  CONSTRAINT manager_areas_manager_fk
    FOREIGN KEY (manager_id)
    REFERENCES app.user_profiles (user_id)
    ON DELETE CASCADE,

  CONSTRAINT manager_areas_area_fk
    FOREIGN KEY (area_id)
    REFERENCES app.areas (id)
    ON DELETE RESTRICT,

  CONSTRAINT manager_areas_assigned_by_not_blank
    CHECK (assigned_by IS NULL OR btrim(assigned_by) <> '')
);

COMMENT ON TABLE app.manager_areas IS
  'Relación muchos-a-muchos entre gerentes y las áreas que gestionan.';

COMMENT ON COLUMN app.manager_areas.assigned_by IS
  'Identificador lógico del administrador que realizó la asignación.';


-- ---------------------------------------------------------------------------
-- Archivos
-- ---------------------------------------------------------------------------

CREATE TABLE app.files (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id text,
  owner_email text NOT NULL,
  area_id uuid,
  storage_key text NOT NULL,
  original_name text NOT NULL,
  mime_type text NOT NULL,
  size_bytes bigint NOT NULL,
  sha256_hex char(64),
  uploaded_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz,
  deleted_by_id text,
  deleted_by_email text,

  CONSTRAINT files_owner_fk
    FOREIGN KEY (owner_id)
    REFERENCES neon_auth."user" (id)
    ON DELETE SET NULL,

  CONSTRAINT files_area_fk
    FOREIGN KEY (area_id)
    REFERENCES app.areas (id)
    ON DELETE RESTRICT,

  CONSTRAINT files_storage_key_uq
    UNIQUE (storage_key),

  CONSTRAINT files_size_bytes_ck
    CHECK (size_bytes BETWEEN 1 AND 20971520),

  CONSTRAINT files_owner_email_not_blank
    CHECK (btrim(owner_email) <> ''),

  CONSTRAINT files_storage_key_not_blank
    CHECK (btrim(storage_key) <> ''),

  CONSTRAINT files_original_name_not_blank
    CHECK (btrim(original_name) <> ''),

  CONSTRAINT files_mime_type_not_blank
    CHECK (btrim(mime_type) <> ''),

  CONSTRAINT files_sha256_format_ck
    CHECK (sha256_hex IS NULL OR sha256_hex ~ '^[0-9a-f]{64}$'),

  CONSTRAINT files_deleted_consistent_ck
    CHECK (
      (deleted_at IS NULL AND deleted_by_id IS NULL AND deleted_by_email IS NULL)
      OR
      (deleted_at IS NOT NULL AND deleted_by_id IS NOT NULL AND deleted_by_email IS NOT NULL)
    )
);

COMMENT ON TABLE app.files IS
  'Metadatos de archivos; el contenido reside en Neon Object Storage.';

COMMENT ON COLUMN app.files.id IS
  'UUID público y aleatorio del archivo.';

COMMENT ON COLUMN app.files.owner_id IS
  'Propietario actual; se vuelve NULL si se elimina la cuenta de Auth.';

COMMENT ON COLUMN app.files.owner_email IS
  'Snapshot del correo del propietario al momento de la carga.';

COMMENT ON COLUMN app.files.area_id IS
  'Área del propietario al momento de la carga; no cambia después.';

COMMENT ON COLUMN app.files.storage_key IS
  'Clave interna en Object Storage; nunca se expone al cliente.';

COMMENT ON COLUMN app.files.size_bytes IS
  'Tamaño en bytes; máximo 20 MB (20 971 520 bytes).';

COMMENT ON COLUMN app.files.deleted_at IS
  'Eliminación lógica. Un archivo eliminado no se ofrece a ningún rol.';

COMMENT ON COLUMN app.files.deleted_by_id IS
  'Identificador histórico del usuario que eliminó el archivo.';


-- ---------------------------------------------------------------------------
-- Historial de acciones
-- ---------------------------------------------------------------------------

CREATE TABLE audit.action_logs (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  occurred_at timestamptz NOT NULL DEFAULT now(),
  action audit.audit_action NOT NULL,
  actor_user_id text,
  actor_email text,
  actor_role app.app_role,
  actor_area_id uuid,
  actor_area_name text,
  file_id uuid,
  file_name text,
  ip_address inet NOT NULL,
  request_id text NOT NULL,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,

  CONSTRAINT action_logs_request_id_not_blank
    CHECK (btrim(request_id) <> ''),

  CONSTRAINT action_logs_metadata_object_ck
    CHECK (jsonb_typeof(metadata) = 'object')
);

COMMENT ON TABLE audit.action_logs IS
  'Historial append-only. Las referencias son lógicas para conservar la trazabilidad.';

COMMENT ON COLUMN audit.action_logs.actor_user_id IS
  'ID histórico del actor; no tiene FK para sobrevivir a la eliminación del usuario.';

COMMENT ON COLUMN audit.action_logs.actor_area_id IS
  'Área del actor al momento de la acción; delimita el alcance histórico.';

COMMENT ON COLUMN audit.action_logs.file_id IS
  'ID histórico del archivo; no tiene FK para sobrevivir a su eliminación.';

COMMENT ON COLUMN audit.action_logs.metadata IS
  'Datos mínimos de contexto; nunca contiene secretos, tokens ni contenido de archivos.';


-- ---------------------------------------------------------------------------
-- Límite de intentos de inicio de sesión
-- ---------------------------------------------------------------------------

CREATE TABLE app.login_rate_limits (
  key_hash text PRIMARY KEY,
  failed_count integer NOT NULL DEFAULT 0,
  window_started_at timestamptz NOT NULL DEFAULT now(),
  blocked_until timestamptz,
  updated_at timestamptz NOT NULL DEFAULT now(),

  CONSTRAINT login_rate_limits_key_hash_not_blank
    CHECK (btrim(key_hash) <> ''),

  CONSTRAINT login_rate_limits_failed_count_ck
    CHECK (failed_count >= 0)
);

COMMENT ON TABLE app.login_rate_limits IS
  'Estado operativo y mutable para limitar intentos de inicio de sesión.';

COMMENT ON COLUMN app.login_rate_limits.key_hash IS
  'HMAC de correo e IP; no se almacenan los valores originales.';


-- ---------------------------------------------------------------------------
-- Actualización automática de updated_at
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION app.set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog
AS $$
BEGIN
  NEW.updated_at := now();
  RETURN NEW;
END;
$$;

COMMENT ON FUNCTION app.set_updated_at() IS
  'Trigger genérico que actualiza updated_at antes de cada modificación.';

CREATE TRIGGER areas_set_updated_at
BEFORE UPDATE ON app.areas
FOR EACH ROW
EXECUTE FUNCTION app.set_updated_at();

CREATE TRIGGER user_profiles_set_updated_at
BEFORE UPDATE ON app.user_profiles
FOR EACH ROW
EXECUTE FUNCTION app.set_updated_at();

CREATE TRIGGER login_rate_limits_set_updated_at
BEFORE UPDATE ON app.login_rate_limits
FOR EACH ROW
EXECUTE FUNCTION app.set_updated_at();


-- ---------------------------------------------------------------------------
-- Inmutabilidad del historial
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION audit.reject_mutation()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog
AS $$
BEGIN
  RAISE EXCEPTION 'audit.action_logs es append-only'
    USING ERRCODE = 'insufficient_privilege';
END;
$$;

COMMENT ON FUNCTION audit.reject_mutation() IS
  'Impide UPDATE y DELETE sobre el historial desde la aplicación.';

CREATE TRIGGER action_logs_append_only
BEFORE UPDATE OR DELETE ON audit.action_logs
FOR EACH ROW
EXECUTE FUNCTION audit.reject_mutation();


-- ---------------------------------------------------------------------------
-- Protección por defecto de las tablas sensibles
-- ---------------------------------------------------------------------------
-- Se habilita RLS sin políticas: la denegación es el estado por defecto para
-- cualquier rol que no sea propietario (app_runtime). Las políticas se crean
-- en V3; a partir de ese momento app_runtime comienza a ver filas.
--
-- No se usa FORCE ROW LEVEL SECURITY: app_migrator (propietario) conserva el
-- bypass únicamente para migraciones y datos de prueba. La aplicación nunca
-- se conecta con ese rol. Se reevaluará al definir la estrategia de seeds.

ALTER TABLE app.areas             ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.user_profiles     ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.manager_areas     ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.files             ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.login_rate_limits ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit.action_logs     ENABLE ROW LEVEL SECURITY;


-- ---------------------------------------------------------------------------
-- Privilegios de app_runtime sobre app y audit
-- NOTA: la creación y configuración de roles pertenece a
-- database/provision-environment.sh; aquí solo se otorgan privilegios
-- sobre los esquemas que esta migración crea.
-- ---------------------------------------------------------------------------
-- Se otorga lo mínimo necesario. Los INSERT/UPDATE/SELECT quedan además
-- acotados a las filas permitidas por las políticas RLS de V3.
-- No se conceden privilegios de DDL, ni DELETE sobre archivos o áreas.

GRANT USAGE ON SCHEMA app   TO app_runtime;
GRANT USAGE ON SCHEMA audit TO app_runtime;

-- Áreas: lectura, alta y edición. Sin borrado físico.
GRANT SELECT, INSERT, UPDATE ON app.areas TO app_runtime;

-- Perfiles: lectura, alta y edición. Sin borrado físico.
GRANT SELECT, INSERT, UPDATE ON app.user_profiles TO app_runtime;

-- Áreas gestionadas: se asignan y se retiran, por lo que se permite DELETE.
GRANT SELECT, INSERT, DELETE ON app.manager_areas TO app_runtime;

-- Archivos: el alta usa todas las columnas, pero la edición se limita
-- exclusivamente a los campos de eliminación lógica.
GRANT SELECT, INSERT ON app.files TO app_runtime;
GRANT UPDATE (deleted_at, deleted_by_id, deleted_by_email)
  ON app.files TO app_runtime;

-- Historial: solo lectura desde la API. La inserción se hará mediante la
-- función controlada audit.append_event (V2), nunca con INSERT directo.
GRANT SELECT ON audit.action_logs TO app_runtime;

-- Límite de intentos: tabla operativa de uso exclusivo de la API.
GRANT SELECT, INSERT, UPDATE, DELETE ON app.login_rate_limits TO app_runtime;

-- Las funciones de trigger no deben ser ejecutables por otros roles.
REVOKE ALL ON FUNCTION app.set_updated_at() FROM PUBLIC;
REVOKE ALL ON FUNCTION audit.reject_mutation() FROM PUBLIC;


-- V2: funciones de autorización y registro del historial.
--
-- Esta migración centraliza las reglas de permisos en funciones de la base de
-- datos, de modo que la API y las políticas RLS de V3 compartan exactamente la
-- misma lógica. Añadir un rol nuevo o cambiar el alcance de uno existente
-- debería implicar tocar solo este archivo.
--
-- Contrato con la API:
--   1. Al abrir una transacción, la API llama a app.set_context() con el
--      usuario verificado por Neon Auth, el identificador de petición y la IP.
--   2. Esas funciones leen el contexto de la transacción mediante
--      current_setting(..., true), por lo que el valor desaparece al terminar
--      la transacción. Esto es imprescindible con el pooler de Neon, que
--      reutiliza conexiones entre peticiones.
--   3. El historial se escribe únicamente con audit.append_event(), nunca con
--      INSERT directo. V1 no otorgó privilegios de INSERT sobre la tabla.
--
-- Decisiones de seguridad:
--   - Las funciones que consultan tablas son SECURITY DEFINER y pertenecen a
--     app_migrator. Así pueden leer app.user_profiles y app.files sin quedar
--     sujetas a RLS y sin provocar recursión al ser usadas dentro de políticas.
--   - Todas fijan search_path para evitar ataques de búsqueda de esquema.
--   - Si no hay contexto o no existe un perfil activo, resuelven a false
--     (fallo cerrado).


-- ---------------------------------------------------------------------------
-- 1. Contexto de la petición
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION app.set_context(
  p_user_id    text,
  p_request_id text,
  p_ip_address inet
)
RETURNS void
LANGUAGE plpgsql
SET search_path = pg_catalog
AS $$
BEGIN
  IF p_request_id IS NULL THEN
    RAISE EXCEPTION 'p_request_id es obligatorio'
      USING ERRCODE = 'null_value_not_allowed';
  END IF;

  IF p_ip_address IS NULL THEN
    RAISE EXCEPTION 'p_ip_address es obligatorio'
      USING ERRCODE = 'null_value_not_allowed';
  END IF;

  -- p_user_id puede ser NULL: un inicio de sesión fallido no tiene usuario.
  PERFORM set_config('app.user_id', coalesce(p_user_id, ''), true);
  PERFORM set_config('app.request_id', p_request_id, true);
  PERFORM set_config('app.ip_address', host(p_ip_address), true);
END;
$$;

COMMENT ON FUNCTION app.set_context(text, text, inet) IS
  'Establece el contexto de la transacción: usuario, petición e IP. Local a la transacción.';


CREATE OR REPLACE FUNCTION app.current_user_id()
RETURNS text
LANGUAGE sql
STABLE
AS $$
  SELECT NULLIF(current_setting('app.user_id', true), '')
$$;

COMMENT ON FUNCTION app.current_user_id() IS
  'Identificador del usuario autenticado en la transacción actual, o NULL si no hay contexto.';


CREATE OR REPLACE FUNCTION app.current_request_id()
RETURNS text
LANGUAGE sql
STABLE
AS $$
  SELECT current_setting('app.request_id', true)
$$;

COMMENT ON FUNCTION app.current_request_id() IS
  'Identificador de la petición en curso, para correlacionar con los registros de la aplicación.';


CREATE OR REPLACE FUNCTION app.current_ip_address()
RETURNS text
LANGUAGE sql
STABLE
AS $$
  SELECT current_setting('app.ip_address', true)
$$;

COMMENT ON FUNCTION app.current_ip_address() IS
  'Dirección IP de origen de la petición en curso.';


-- ---------------------------------------------------------------------------
-- 2. Datos de autorización del usuario actual
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION app.current_user_role()
RETURNS app.app_role
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
  SELECT p.role
    FROM app.user_profiles p
   WHERE p.user_id = app.current_user_id()
$$;

COMMENT ON FUNCTION app.current_user_role() IS
  'Rol de negocio del usuario actual, o NULL si no tiene perfil.';


CREATE OR REPLACE FUNCTION app.current_user_area_id()
RETURNS uuid
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
  SELECT p.area_id
    FROM app.user_profiles p
   WHERE p.user_id = app.current_user_id()
$$;

COMMENT ON FUNCTION app.current_user_area_id() IS
  'Área propia del usuario actual (standard y area_head). NULL para manager y admin.';


CREATE OR REPLACE FUNCTION app.current_user_is_active()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
  SELECT coalesce((
    SELECT p.is_active
      FROM app.user_profiles p
     WHERE p.user_id = app.current_user_id()
  ), false)
$$;

COMMENT ON FUNCTION app.current_user_is_active() IS
  'True solo si hay contexto y el perfil del usuario existe y está activo.';


CREATE OR REPLACE FUNCTION app.is_admin()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
  SELECT app.current_user_is_active()
     AND app.current_user_role() = 'admin'
$$;

COMMENT ON FUNCTION app.is_admin() IS
  'True solo si el usuario actual está activo y tiene rol admin.';


CREATE OR REPLACE FUNCTION app.is_manager_of(p_area_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
  SELECT p_area_id IS NOT NULL
     AND app.current_user_is_active()
     AND app.current_user_role() = 'manager'
     AND EXISTS (
       SELECT 1
         FROM app.manager_areas ma
        WHERE ma.manager_id = app.current_user_id()
          AND ma.area_id = p_area_id
     )
$$;

COMMENT ON FUNCTION app.is_manager_of(uuid) IS
  'True solo si el usuario actual tiene rol manager y el área indicada está entre las que gestiona.';


-- ---------------------------------------------------------------------------
-- 3. Alcance sobre archivos
-- ---------------------------------------------------------------------------
-- Las funciones *_row reciben las columnas relevantes para poder usarse en
-- políticas RLS sin volver a consultar app.files. Las funciones *_file
-- resuelven el archivo por su identificador y son las que utiliza la API.

CREATE OR REPLACE FUNCTION app.can_read_file_row(
  p_owner_id   text,
  p_area_id    uuid,
  p_deleted_at timestamptz
)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
  SELECT
    -- Un archivo eliminado no está disponible para ningún rol.
    p_deleted_at IS NULL
    AND app.current_user_is_active()
    AND (
      -- Todos los roles ven sus propios archivos.
      p_owner_id = app.current_user_id()

      -- Administrador: todos los archivos.
      OR app.current_user_role() = 'admin'

      -- Gerente: todos los archivos de las áreas que gestiona, incluidos los
      -- de los jefes de área de esas áreas.
      OR (
        p_area_id IS NOT NULL
        AND app.is_manager_of(p_area_id)
      )

      -- Jefe de área: los archivos de usuarios estándar de su propia área.
      -- No accede a los archivos del gerente.
      OR (
        app.current_user_role() = 'area_head'
        AND p_area_id IS NOT NULL
        AND p_area_id = app.current_user_area_id()
        AND EXISTS (
          SELECT 1
            FROM app.user_profiles o
           WHERE o.user_id = p_owner_id
             AND o.role = 'standard'
             AND o.area_id = app.current_user_area_id()
        )
      )
    )
$$;

COMMENT ON FUNCTION app.can_read_file_row(text, uuid, timestamptz) IS
  'Alcance de lectura/descarga para los valores de una fila de app.files.';


CREATE OR REPLACE FUNCTION app.can_delete_file_row(
  p_owner_id   text,
  p_area_id    uuid,
  p_deleted_at timestamptz
)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
  SELECT
    p_deleted_at IS NULL
    AND app.current_user_is_active()
    AND (
      -- Standard y area_head: solo los propios.
      p_owner_id = app.current_user_id()

      -- Administrador: todos.
      OR app.current_user_role() = 'admin'

      -- Gerente: los propios y los de sus áreas gestionadas.
      OR (
        p_area_id IS NOT NULL
        AND app.is_manager_of(p_area_id)
      )
    )
$$;

COMMENT ON FUNCTION app.can_delete_file_row(text, uuid, timestamptz) IS
  'Alcance de eliminación lógica para los valores de una fila de app.files.';


CREATE OR REPLACE FUNCTION app.can_upload_for_area(p_area_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
  SELECT
    app.current_user_is_active()
    AND (
      -- Admin y manager no tienen área propia: sus archivos quedan sin área.
      (
        app.current_user_role() IN ('admin', 'manager')
        AND p_area_id IS NULL
      )
      OR
      -- Standard y area_head: siempre en su propia área, y debe estar activa.
      (
        app.current_user_role() IN ('standard', 'area_head')
        AND p_area_id IS NOT NULL
        AND p_area_id = app.current_user_area_id()
        AND EXISTS (
          SELECT 1
            FROM app.areas a
           WHERE a.id = p_area_id
             AND a.is_active
        )
      )
    )
$$;

COMMENT ON FUNCTION app.can_upload_for_area(uuid) IS
  'Valida el área que se asignará a un archivo nuevo. Único lugar donde cambiar la política de carga si los gerentes debieran cargar en sus áreas.';


CREATE OR REPLACE FUNCTION app.can_read_file(p_file_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
  SELECT coalesce((
    SELECT app.can_read_file_row(f.owner_id, f.area_id, f.deleted_at)
      FROM app.files f
     WHERE f.id = p_file_id
  ), false)
$$;

COMMENT ON FUNCTION app.can_read_file(uuid) IS
  'True si el usuario actual puede ver o descargar el archivo indicado.';


CREATE OR REPLACE FUNCTION app.can_delete_file(p_file_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
  SELECT coalesce((
    SELECT app.can_delete_file_row(f.owner_id, f.area_id, f.deleted_at)
      FROM app.files f
     WHERE f.id = p_file_id
  ), false)
$$;

COMMENT ON FUNCTION app.can_delete_file(uuid) IS
  'True si el usuario actual puede eliminar lógicamente el archivo indicado.';


-- ---------------------------------------------------------------------------
-- 4. Alcance del historial
-- ---------------------------------------------------------------------------
-- El alcance se calcula con el área que tenía el actor cuando ejecutó la
-- acción (actor_area_id), no con su área actual. Así, cambiar de área a un
-- usuario no reescribe retroactivamente el historial.

CREATE OR REPLACE FUNCTION app.can_view_history(
  p_actor_user_id text,
  p_actor_area_id uuid
)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
  SELECT
    app.current_user_is_active()
    AND (
      -- Administrador: todo el historial.
      app.current_user_role() = 'admin'
      OR (
        app.current_user_role() = 'manager'
        AND (
          -- Sus propias acciones.
          p_actor_user_id = app.current_user_id()
          -- O las acciones registradas dentro de sus áreas gestionadas.
          OR app.is_manager_of(p_actor_area_id)
        )
      )
    )
$$;

COMMENT ON FUNCTION app.can_view_history(text, uuid) IS
  'Alcance de lectura del historial. Standard y area_head no tienen acceso.';


-- ---------------------------------------------------------------------------
-- 5. Registro controlado del historial
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION audit.append_event(
  p_action        audit.audit_action,
  p_actor_user_id text  DEFAULT NULL,
  p_actor_email   text  DEFAULT NULL,
  p_file_id       uuid  DEFAULT NULL,
  p_file_name     text  DEFAULT NULL,
  p_metadata      jsonb DEFAULT '{}'::jsonb
)
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
DECLARE
  v_request_id text := app.current_request_id();
  v_ip_address text := app.current_ip_address();
  v_role       app.app_role;
  v_area_id    uuid;
  v_area_name  text;
  v_id         bigint;
BEGIN
  IF v_request_id IS NULL THEN
    RAISE EXCEPTION 'Falta el contexto de la petición (app.request_id)'
      USING ERRCODE = 'invalid_parameter_value';
  END IF;

  IF v_ip_address IS NULL THEN
    RAISE EXCEPTION 'Falta el contexto de la petición (app.ip_address)'
      USING ERRCODE = 'invalid_parameter_value';
  END IF;

  IF p_action IS NULL THEN
    RAISE EXCEPTION 'p_action es obligatorio'
      USING ERRCODE = 'null_value_not_allowed';
  END IF;

  IF p_metadata IS NULL OR jsonb_typeof(p_metadata) <> 'object' THEN
    RAISE EXCEPTION 'p_metadata debe ser un objeto JSON'
      USING ERRCODE = 'invalid_parameter_value';
  END IF;

  IF pg_column_size(p_metadata) > 8192 THEN
    RAISE EXCEPTION 'p_metadata excede el tamaño permitido'
      USING ERRCODE = 'invalid_parameter_value';
  END IF;

  IF p_action IN ('file_uploaded', 'file_downloaded', 'file_deleted')
     AND p_file_id IS NULL THEN
    RAISE EXCEPTION 'La acción % requiere p_file_id', p_action
      USING ERRCODE = 'null_value_not_allowed';
  END IF;

  -- El rol y el área se derivan de la base de datos, no del cliente.
  IF p_actor_user_id IS NOT NULL THEN
    SELECT p.role, p.area_id
      INTO v_role, v_area_id
      FROM app.user_profiles p
     WHERE p.user_id = p_actor_user_id;

    IF v_area_id IS NOT NULL THEN
      SELECT a.name
        INTO v_area_name
        FROM app.areas a
       WHERE a.id = v_area_id;
    END IF;
  END IF;

  INSERT INTO audit.action_logs (
    action, actor_user_id, actor_email, actor_role,
    actor_area_id, actor_area_name, file_id, file_name,
    ip_address, request_id, metadata
  ) VALUES (
    p_action, p_actor_user_id, p_actor_email, v_role,
    v_area_id, v_area_name, p_file_id, p_file_name,
    v_ip_address::inet, v_request_id, p_metadata
  )
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

COMMENT ON FUNCTION audit.append_event(
  audit.audit_action, text, text, uuid, text, jsonb
) IS
  'Única vía de escritura del historial. Deriva rol y área del actor desde la base de datos.';


-- ---------------------------------------------------------------------------
-- 6. Guardas de integridad
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION app.protect_admin_self_update()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog
AS $$
BEGIN
  -- Solo protege al propio administrador. Si no hay contexto (migraciones o
  -- datos de prueba) la comparación es NULL y no se bloquea nada.
  IF OLD.role = 'admin'
     AND OLD.user_id = app.current_user_id()
  THEN
    IF NEW.role <> OLD.role THEN
      RAISE EXCEPTION 'Un administrador no puede cambiar su propio rol'
        USING ERRCODE = 'insufficient_privilege';
    END IF;

    IF OLD.is_active AND NOT NEW.is_active THEN
      RAISE EXCEPTION 'Un administrador no puede desactivarse a sí mismo'
        USING ERRCODE = 'insufficient_privilege';
    END IF;
  END IF;

  RETURN NEW;
END;
$$;

COMMENT ON FUNCTION app.protect_admin_self_update() IS
  'Impide que un administrador se quite su propio rol o se desactive.';

CREATE TRIGGER user_profiles_protect_admin_self
BEFORE UPDATE ON app.user_profiles
FOR EACH ROW
EXECUTE FUNCTION app.protect_admin_self_update();


-- ---------------------------------------------------------------------------
-- 7. Privilegios
-- ---------------------------------------------------------------------------
-- PostgreSQL otorga EXECUTE a PUBLIC en las funciones nuevas. Se retira y se
-- concede únicamente a app_runtime, que es el rol de la API.

REVOKE ALL ON FUNCTION app.set_context(text, text, inet)              FROM PUBLIC;
REVOKE ALL ON FUNCTION app.current_user_id()                          FROM PUBLIC;
REVOKE ALL ON FUNCTION app.current_request_id()                       FROM PUBLIC;
REVOKE ALL ON FUNCTION app.current_ip_address()                       FROM PUBLIC;
REVOKE ALL ON FUNCTION app.current_user_role()                        FROM PUBLIC;
REVOKE ALL ON FUNCTION app.current_user_area_id()                     FROM PUBLIC;
REVOKE ALL ON FUNCTION app.current_user_is_active()                   FROM PUBLIC;
REVOKE ALL ON FUNCTION app.is_admin()                                 FROM PUBLIC;
REVOKE ALL ON FUNCTION app.is_manager_of(uuid)                        FROM PUBLIC;
REVOKE ALL ON FUNCTION app.can_read_file_row(text, uuid, timestamptz)  FROM PUBLIC;
REVOKE ALL ON FUNCTION app.can_delete_file_row(text, uuid, timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION app.can_upload_for_area(uuid)                  FROM PUBLIC;
REVOKE ALL ON FUNCTION app.can_read_file(uuid)                        FROM PUBLIC;
REVOKE ALL ON FUNCTION app.can_delete_file(uuid)                      FROM PUBLIC;
REVOKE ALL ON FUNCTION app.can_view_history(text, uuid)               FROM PUBLIC;
REVOKE ALL ON FUNCTION app.protect_admin_self_update()                FROM PUBLIC;
REVOKE ALL ON FUNCTION audit.append_event(
  audit.audit_action, text, text, uuid, text, jsonb
) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION app.set_context(text, text, inet)              TO app_runtime;
GRANT EXECUTE ON FUNCTION app.current_user_id()                          TO app_runtime;
GRANT EXECUTE ON FUNCTION app.current_request_id()                       TO app_runtime;
GRANT EXECUTE ON FUNCTION app.current_ip_address()                       TO app_runtime;
GRANT EXECUTE ON FUNCTION app.current_user_role()                        TO app_runtime;
GRANT EXECUTE ON FUNCTION app.current_user_area_id()                     TO app_runtime;
GRANT EXECUTE ON FUNCTION app.current_user_is_active()                   TO app_runtime;
GRANT EXECUTE ON FUNCTION app.is_admin()                                 TO app_runtime;
GRANT EXECUTE ON FUNCTION app.is_manager_of(uuid)                        TO app_runtime;
GRANT EXECUTE ON FUNCTION app.can_read_file_row(text, uuid, timestamptz)  TO app_runtime;
GRANT EXECUTE ON FUNCTION app.can_delete_file_row(text, uuid, timestamptz) TO app_runtime;
GRANT EXECUTE ON FUNCTION app.can_upload_for_area(uuid)                  TO app_runtime;
GRANT EXECUTE ON FUNCTION app.can_read_file(uuid)                        TO app_runtime;
GRANT EXECUTE ON FUNCTION app.can_delete_file(uuid)                      TO app_runtime;
GRANT EXECUTE ON FUNCTION app.can_view_history(text, uuid)               TO app_runtime;
GRANT EXECUTE ON FUNCTION audit.append_event(
  audit.audit_action, text, text, uuid, text, jsonb
) TO app_runtime;

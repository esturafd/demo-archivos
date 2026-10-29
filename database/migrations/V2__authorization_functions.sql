-- V2: funciones de autorización, identidad y sesiones.
--
-- Esta migración centraliza en funciones de la base de datos las reglas de
-- permisos y el ciclo de vida de la sesión, de modo que la API y las políticas
-- RLS de V3 compartan exactamente la misma lógica.
--
-- Contrato con la API:
--   1. Para autenticar, la API llama a app.login_lookup() y verifica el hash
--      con su biblioteca de bcrypt. La contraseña nunca entra en la base.
--   2. Al iniciar sesión, app.create_session() guarda el hash del token.
--   3. En cada petición, app.validate_session() resuelve el token y comprueba
--      que la sesión y el usuario sigan vigentes.
--   4. Al abrir una transacción de trabajo, la API llama a app.set_context()
--      con el usuario, el identificador de petición y la IP.
--   5. El historial se escribe únicamente con audit.append_event().
--
-- Decisiones de seguridad:
--   - Las funciones que consultan tablas son SECURITY DEFINER y pertenecen a
--     app_migrator. Así pueden leer app.users y app.files sin quedar sujetas a
--     RLS y sin provocar recursión al ser usadas dentro de políticas.
--   - Todas fijan search_path para evitar ataques de búsqueda de esquema.
--   - Si no hay contexto o el usuario no está activo, resuelven a false
--     (fallo cerrado).
--   - app.sessions no tiene privilegios ni políticas para app_runtime: solo se
--     toca desde estas funciones.


-- ---------------------------------------------------------------------------
-- 1. Contexto de la petición
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION app.set_context(
  p_user_id    uuid,
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
  PERFORM set_config('app.user_id', coalesce(p_user_id::text, ''), true);
  PERFORM set_config('app.request_id', p_request_id, true);
  PERFORM set_config('app.ip_address', host(p_ip_address), true);
END;
$$;

COMMENT ON FUNCTION app.set_context(uuid, text, inet) IS
  'Establece el contexto de la transacción: usuario, petición e IP. Local a la transacción.';


CREATE OR REPLACE FUNCTION app.current_user_id()
RETURNS uuid
LANGUAGE sql
STABLE
AS $$
  SELECT NULLIF(current_setting('app.user_id', true), '')::uuid
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
-- 2. Identidad y sesiones
-- ---------------------------------------------------------------------------
-- Estas funciones son la única vía de acceso a app.users para el login y a
-- app.sessions en general.

CREATE OR REPLACE FUNCTION app.login_lookup(p_email text)
RETURNS SETOF app.users
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
  SELECT u.*
    FROM app.users u
   WHERE lower(u.email) = lower(p_email)
$$;

COMMENT ON FUNCTION app.login_lookup(text) IS
  'Resuelve un usuario por correo para el inicio de sesión. Existe porque el login ocurre antes de que haya contexto: sin ella, la política RLS de app.users no devolvería filas.';


CREATE OR REPLACE FUNCTION app.create_session(
  p_user_id    uuid,
  p_token_hash text,
  p_ip_address inet,
  p_user_agent text,
  p_ttl        interval
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
DECLARE
  v_session_id uuid;
BEGIN
  IF p_user_id IS NULL OR p_token_hash IS NULL OR p_ip_address IS NULL THEN
    RAISE EXCEPTION 'p_user_id, p_token_hash y p_ip_address son obligatorios'
      USING ERRCODE = 'null_value_not_allowed';
  END IF;

  IF p_ttl IS NULL OR p_ttl <= interval '0' THEN
    RAISE EXCEPTION 'p_ttl debe ser un intervalo positivo'
      USING ERRCODE = 'invalid_parameter_value';
  END IF;

  -- No se crean sesiones para cuentas inexistentes o desactivadas.
  IF NOT EXISTS (
    SELECT 1 FROM app.users u WHERE u.id = p_user_id AND u.is_active
  ) THEN
    RAISE EXCEPTION 'El usuario no existe o está desactivado'
      USING ERRCODE = 'insufficient_privilege';
  END IF;

  INSERT INTO app.sessions (token_hash, user_id, expires_at, ip_address, user_agent)
  VALUES (p_token_hash, p_user_id, now() + p_ttl, p_ip_address, p_user_agent)
  RETURNING id INTO v_session_id;

  RETURN v_session_id;
END;
$$;

COMMENT ON FUNCTION app.create_session(uuid, text, inet, text, interval) IS
  'Crea una sesión guardando solo el hash del token. Rechaza usuarios inexistentes o desactivados.';


CREATE OR REPLACE FUNCTION app.validate_session(p_token_hash text)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
DECLARE
  v_user_id uuid;
BEGIN
  IF p_token_hash IS NULL THEN
    RETURN NULL;
  END IF;

  SELECT s.user_id
    INTO v_user_id
    FROM app.sessions s
   WHERE s.token_hash = p_token_hash
     AND s.revoked_at IS NULL
     AND s.expires_at > now();

  IF v_user_id IS NULL THEN
    RETURN NULL;
  END IF;

  -- Un usuario desactivado pierde el acceso de inmediato, aunque su sesión
  -- siga vigente.
  IF NOT EXISTS (
    SELECT 1 FROM app.users u WHERE u.id = v_user_id AND u.is_active
  ) THEN
    RETURN NULL;
  END IF;

  -- Se evita escribir en cada petición: como mucho una vez cada cinco minutos.
  UPDATE app.sessions
     SET last_seen_at = now()
   WHERE token_hash = p_token_hash
     AND (last_seen_at IS NULL OR last_seen_at < now() - interval '5 minutes');

  RETURN v_user_id;
END;
$$;

COMMENT ON FUNCTION app.validate_session(text) IS
  'Devuelve el usuario de una sesión válida, o NULL si no existe, está revocada, expiró o el usuario está desactivado.';


CREATE OR REPLACE FUNCTION app.revoke_session(p_token_hash text)
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
  WITH revoked AS (
    UPDATE app.sessions
       SET revoked_at = now()
     WHERE token_hash = p_token_hash
       AND revoked_at IS NULL
    RETURNING 1
  )
  SELECT EXISTS (SELECT 1 FROM revoked)
$$;

COMMENT ON FUNCTION app.revoke_session(text) IS
  'Cierra una sesión concreta. Devuelve false si no existía o ya estaba revocada.';


CREATE OR REPLACE FUNCTION app.revoke_user_sessions(p_user_id uuid)
RETURNS integer
LANGUAGE sql
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
  WITH revoked AS (
    UPDATE app.sessions
       SET revoked_at = now()
     WHERE user_id = p_user_id
       AND revoked_at IS NULL
    RETURNING 1
  )
  SELECT count(*)::integer FROM revoked
$$;

COMMENT ON FUNCTION app.revoke_user_sessions(uuid) IS
  'Revoca todas las sesiones de un usuario. Se usa al desactivarlo o al cambiar su contraseña.';


-- ---------------------------------------------------------------------------
-- 3. Datos de autorización del usuario actual
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION app.current_user_role()
RETURNS app.app_role
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
  SELECT u.role
    FROM app.users u
   WHERE u.id = app.current_user_id()
$$;

COMMENT ON FUNCTION app.current_user_role() IS
  'Rol de negocio del usuario actual, o NULL si no existe.';


CREATE OR REPLACE FUNCTION app.current_user_area_id()
RETURNS uuid
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
  SELECT u.area_id
    FROM app.users u
   WHERE u.id = app.current_user_id()
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
    SELECT u.is_active
      FROM app.users u
     WHERE u.id = app.current_user_id()
  ), false)
$$;

COMMENT ON FUNCTION app.current_user_is_active() IS
  'True solo si hay contexto y el usuario existe y está activo.';


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


CREATE OR REPLACE FUNCTION app.list_sessions(p_user_id uuid)
RETURNS TABLE (
  id uuid,
  created_at timestamptz,
  expires_at timestamptz,
  last_seen_at timestamptz,
  ip_address inet,
  user_agent text
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
  SELECT s.id, s.created_at, s.expires_at, s.last_seen_at, s.ip_address, s.user_agent
    FROM app.sessions s
   WHERE s.user_id = p_user_id
     AND s.revoked_at IS NULL
     AND s.expires_at > now()
     AND (p_user_id = app.current_user_id() OR app.is_admin())
   ORDER BY s.created_at DESC
$$;

COMMENT ON FUNCTION app.list_sessions(uuid) IS
  'Sesiones activas de un usuario. Solo el propio usuario o un administrador.';


-- ---------------------------------------------------------------------------
-- 4. Alcance sobre archivos
-- ---------------------------------------------------------------------------
-- Las funciones *_row reciben las columnas relevantes para poder usarse en
-- políticas RLS sin volver a consultar app.files. Las funciones *_file
-- resuelven el archivo por su identificador y son las que utiliza la API.

CREATE OR REPLACE FUNCTION app.can_read_file_row(
  p_owner_id   uuid,
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
            FROM app.users o
           WHERE o.id = p_owner_id
             AND o.role = 'standard'
             AND o.area_id = app.current_user_area_id()
        )
      )
    )
$$;

COMMENT ON FUNCTION app.can_read_file_row(uuid, uuid, timestamptz) IS
  'Alcance de lectura/descarga para los valores de una fila de app.files.';


CREATE OR REPLACE FUNCTION app.can_delete_file_row(
  p_owner_id   uuid,
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

COMMENT ON FUNCTION app.can_delete_file_row(uuid, uuid, timestamptz) IS
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
-- 5. Alcance del historial
-- ---------------------------------------------------------------------------
-- El alcance se calcula con el área que tenía el actor cuando ejecutó la
-- acción (actor_area_id), no con su área actual. Así, cambiar de área a un
-- usuario no reescribe retroactivamente el historial.

CREATE OR REPLACE FUNCTION app.can_view_history(
  p_actor_user_id uuid,
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

COMMENT ON FUNCTION app.can_view_history(uuid, uuid) IS
  'Alcance de lectura del historial. Standard y area_head no tienen acceso.';


-- ---------------------------------------------------------------------------
-- 6. Registro controlado del historial
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION audit.append_event(
  p_action        audit.audit_action,
  p_actor_user_id uuid  DEFAULT NULL,
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
    SELECT u.role, u.area_id
      INTO v_role, v_area_id
      FROM app.users u
     WHERE u.id = p_actor_user_id;

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
  audit.audit_action, uuid, text, uuid, text, jsonb
) IS
  'Única vía de escritura del historial. Deriva rol y área del actor desde la base de datos.';


-- ---------------------------------------------------------------------------
-- 7. Guardas de integridad
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
     AND OLD.id = app.current_user_id()
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

CREATE TRIGGER users_protect_admin_self
BEFORE UPDATE ON app.users
FOR EACH ROW
EXECUTE FUNCTION app.protect_admin_self_update();


-- ---------------------------------------------------------------------------
-- 8. Privilegios
-- ---------------------------------------------------------------------------
-- PostgreSQL otorga EXECUTE a PUBLIC en las funciones nuevas. Se retira y se
-- concede únicamente a app_runtime, que es el rol de la API.

REVOKE ALL ON FUNCTION app.set_context(uuid, text, inet)                FROM PUBLIC;
REVOKE ALL ON FUNCTION app.current_user_id()                            FROM PUBLIC;
REVOKE ALL ON FUNCTION app.current_request_id()                         FROM PUBLIC;
REVOKE ALL ON FUNCTION app.current_ip_address()                         FROM PUBLIC;
REVOKE ALL ON FUNCTION app.login_lookup(text)                           FROM PUBLIC;
REVOKE ALL ON FUNCTION app.create_session(uuid, text, inet, text, interval) FROM PUBLIC;
REVOKE ALL ON FUNCTION app.validate_session(text)                       FROM PUBLIC;
REVOKE ALL ON FUNCTION app.revoke_session(text)                         FROM PUBLIC;
REVOKE ALL ON FUNCTION app.revoke_user_sessions(uuid)                   FROM PUBLIC;
REVOKE ALL ON FUNCTION app.list_sessions(uuid)                          FROM PUBLIC;
REVOKE ALL ON FUNCTION app.current_user_role()                          FROM PUBLIC;
REVOKE ALL ON FUNCTION app.current_user_area_id()                       FROM PUBLIC;
REVOKE ALL ON FUNCTION app.current_user_is_active()                     FROM PUBLIC;
REVOKE ALL ON FUNCTION app.is_admin()                                   FROM PUBLIC;
REVOKE ALL ON FUNCTION app.is_manager_of(uuid)                          FROM PUBLIC;
REVOKE ALL ON FUNCTION app.can_read_file_row(uuid, uuid, timestamptz)   FROM PUBLIC;
REVOKE ALL ON FUNCTION app.can_delete_file_row(uuid, uuid, timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION app.can_upload_for_area(uuid)                    FROM PUBLIC;
REVOKE ALL ON FUNCTION app.can_read_file(uuid)                          FROM PUBLIC;
REVOKE ALL ON FUNCTION app.can_delete_file(uuid)                        FROM PUBLIC;
REVOKE ALL ON FUNCTION app.can_view_history(uuid, uuid)                 FROM PUBLIC;
REVOKE ALL ON FUNCTION app.protect_admin_self_update()                  FROM PUBLIC;
REVOKE ALL ON FUNCTION audit.append_event(
  audit.audit_action, uuid, text, uuid, text, jsonb
) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION app.set_context(uuid, text, inet)                TO app_runtime;
GRANT EXECUTE ON FUNCTION app.current_user_id()                            TO app_runtime;
GRANT EXECUTE ON FUNCTION app.current_request_id()                         TO app_runtime;
GRANT EXECUTE ON FUNCTION app.current_ip_address()                         TO app_runtime;
GRANT EXECUTE ON FUNCTION app.login_lookup(text)                           TO app_runtime;
GRANT EXECUTE ON FUNCTION app.create_session(uuid, text, inet, text, interval) TO app_runtime;
GRANT EXECUTE ON FUNCTION app.validate_session(text)                       TO app_runtime;
GRANT EXECUTE ON FUNCTION app.revoke_session(text)                         TO app_runtime;
GRANT EXECUTE ON FUNCTION app.revoke_user_sessions(uuid)                   TO app_runtime;
GRANT EXECUTE ON FUNCTION app.list_sessions(uuid)                          TO app_runtime;
GRANT EXECUTE ON FUNCTION app.current_user_role()                          TO app_runtime;
GRANT EXECUTE ON FUNCTION app.current_user_area_id()                       TO app_runtime;
GRANT EXECUTE ON FUNCTION app.current_user_is_active()                     TO app_runtime;
GRANT EXECUTE ON FUNCTION app.is_admin()                                   TO app_runtime;
GRANT EXECUTE ON FUNCTION app.is_manager_of(uuid)                          TO app_runtime;
GRANT EXECUTE ON FUNCTION app.can_read_file_row(uuid, uuid, timestamptz)   TO app_runtime;
GRANT EXECUTE ON FUNCTION app.can_delete_file_row(uuid, uuid, timestamptz) TO app_runtime;
GRANT EXECUTE ON FUNCTION app.can_upload_for_area(uuid)                    TO app_runtime;
GRANT EXECUTE ON FUNCTION app.can_read_file(uuid)                          TO app_runtime;
GRANT EXECUTE ON FUNCTION app.can_delete_file(uuid)                        TO app_runtime;
GRANT EXECUTE ON FUNCTION app.can_view_history(uuid, uuid)                 TO app_runtime;
GRANT EXECUTE ON FUNCTION audit.append_event(
  audit.audit_action, uuid, text, uuid, text, jsonb
) TO app_runtime;

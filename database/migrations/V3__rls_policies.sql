-- V3: políticas de seguridad a nivel de fila (RLS).
--
-- V1 habilitó RLS con denegación por defecto en las tablas sensibles.
-- Esta migración añade las políticas que determinan qué filas puede ver o
-- modificar cada usuario.
--
-- Las políticas no duplican la lógica de permisos: llaman a las funciones
-- centralizadas de V2, de modo que el alcance de cada rol sigue viviendo en un
-- único lugar. La API y RLS comparten exactamente las mismas reglas.
--
-- Todas las políticas se conceden únicamente al rol app_runtime:
--   - app_migrator es propietario de las tablas y no tiene FORCE RLS, por lo
--     que conserva el bypass solo para migraciones y datos de prueba.
--   - Cualquier rol nuevo queda denegado por defecto hasta que se le conceda
--     una política de forma explícita.
--
-- Nota sobre app.sessions: no tiene políticas a propósito. La búsqueda del
-- token ocurre antes de conocer al usuario, así que el acceso pasa siempre por
-- las funciones controladas de V2. No se añade ninguna política para que
-- app_runtime no pueda consultarla con SQL directo.
--
-- Nota sobre cómo deniegan las políticas: INSERT viola su WITH CHECK y lanza
-- un error, pero UPDATE y DELETE simplemente no encuentran filas. Es decir,
-- deniegan en silencio con cero filas afectadas. Por eso la API debe comprobar
-- las filas afectadas (por ejemplo con RETURNING) y no dar por hecho que una
-- operación se aplicó solo porque no hubo error.
--
-- Nota sobre la eliminación lógica: no se hace con UPDATE. PostgreSQL exige
-- que la fila resultante de un UPDATE siga cumpliendo las políticas de SELECT,
-- y como los archivos eliminados son invisibles para todos, un UPDATE que
-- establezca deleted_at sería rechazado siempre. La eliminación se encapsula
-- en app.soft_delete_file, y V1 no concede privilegio de UPDATE sobre
-- app.files para que no exista ninguna otra vía.
--
-- Nota de rendimiento: las políticas evalúan funciones que consultan
-- app.users, app.areas y app.manager_areas. Es un coste aceptable para
-- el tamaño de este proyecto. Si el volumen creciera, el siguiente paso sería
-- resolver el alcance del usuario una sola vez por transacción y cachearlo.


-- ---------------------------------------------------------------------------
-- app.areas
-- ---------------------------------------------------------------------------
-- Lectura: el administrador todo; cada usuario su propia área; el gerente las
-- que gestiona.
-- Escritura: solo el administrador.

CREATE POLICY areas_select ON app.areas
  FOR SELECT TO app_runtime
  USING (
    app.is_admin()
    OR id = app.current_user_area_id()
    OR app.is_manager_of(id)
  );

CREATE POLICY areas_insert ON app.areas
  FOR INSERT TO app_runtime
  WITH CHECK (app.is_admin());

CREATE POLICY areas_update ON app.areas
  FOR UPDATE TO app_runtime
  USING (app.is_admin())
  WITH CHECK (app.is_admin());

COMMENT ON POLICY areas_select ON app.areas IS
  'Admin: todas. Usuario: la propia. Gerente: las que gestiona.';
COMMENT ON POLICY areas_insert ON app.areas IS
  'Solo el administrador crea áreas.';
COMMENT ON POLICY areas_update ON app.areas IS
  'Solo el administrador edita o desactiva áreas. No hay política de DELETE: no se borran áreas.';


-- ---------------------------------------------------------------------------
-- app.users
-- ---------------------------------------------------------------------------
-- Lectura: el administrador todo; cada usuario su propia cuenta; el gerente las
-- cuentas de sus áreas; el jefe de área los usuarios estándar de su área.
-- Escritura: solo el administrador. La protección del propio administrador la
-- aplica el trigger creado en V2.

CREATE POLICY users_select ON app.users
  FOR SELECT TO app_runtime
  USING (
    app.is_admin()
    OR id = app.current_user_id()
    OR app.is_manager_of(area_id)
    OR (
      app.current_user_role() = 'area_head'
      AND role = 'standard'
      AND area_id = app.current_user_area_id()
    )
  );

CREATE POLICY users_insert ON app.users
  FOR INSERT TO app_runtime
  WITH CHECK (app.is_admin());

CREATE POLICY users_update ON app.users
  FOR UPDATE TO app_runtime
  USING (app.is_admin())
  WITH CHECK (app.is_admin());

COMMENT ON POLICY users_select ON app.users IS
  'Admin: todos. Usuario: el propio. Gerente: los de sus áreas. Jefe de área: los estándar de su área.';
COMMENT ON POLICY users_insert ON app.users IS
  'Solo el administrador crea usuarios.';
COMMENT ON POLICY users_update ON app.users IS
  'Solo el administrador edita usuarios. La API no puede desactivar ni cambiar el rol del propio administrador.';


-- ---------------------------------------------------------------------------
-- app.manager_areas
-- ---------------------------------------------------------------------------
-- Lectura: el administrador todo; el gerente sus propias asignaciones.
-- Escritura: solo el administrador asigna o retira áreas.

CREATE POLICY manager_areas_select ON app.manager_areas
  FOR SELECT TO app_runtime
  USING (app.is_admin() OR manager_id = app.current_user_id());

CREATE POLICY manager_areas_insert ON app.manager_areas
  FOR INSERT TO app_runtime
  WITH CHECK (app.is_admin());

CREATE POLICY manager_areas_delete ON app.manager_areas
  FOR DELETE TO app_runtime
  USING (app.is_admin());

COMMENT ON POLICY manager_areas_select ON app.manager_areas IS
  'Admin: todas. Gerente: las suyas.';
COMMENT ON POLICY manager_areas_insert ON app.manager_areas IS
  'Solo el administrador asigna áreas a un gerente.';
COMMENT ON POLICY manager_areas_delete ON app.manager_areas IS
  'Solo el administrador retira áreas de un gerente.';


-- ---------------------------------------------------------------------------
-- app.files
-- ---------------------------------------------------------------------------
-- El alcance completo vive en las funciones de V2:
--   can_read_file_row  -> propios, estándar del área del jefe, áreas del gerente, o todo si es admin
--   can_delete_file_row-> propios, áreas del gerente, o todo si es admin
--   can_upload_for_area-> valida el área que se asignará al archivo

CREATE POLICY files_select ON app.files
  FOR SELECT TO app_runtime
  USING (app.can_read_file_row(owner_id, area_id, deleted_at));

CREATE POLICY files_insert ON app.files
  FOR INSERT TO app_runtime
  WITH CHECK (
    owner_id = app.current_user_id()
    AND deleted_at IS NULL
    AND app.can_upload_for_area(area_id)
  );

COMMENT ON POLICY files_select ON app.files IS
  'Un archivo eliminado no es visible para nadie. El resto, según can_read_file_row.';
COMMENT ON POLICY files_insert ON app.files IS
  'Solo se puede cargar como uno mismo y en un área permitida.';


-- Eliminación lógica: no puede hacerse con un UPDATE directo.
--
-- PostgreSQL exige que la fila resultante de un UPDATE siga cumpliendo las
-- políticas de SELECT de la tabla. Como files_select oculta los archivos
-- eliminados, cualquier UPDATE que establezca deleted_at falla siempre con
-- "new row violates row-level security policy", por muy permisivas que sean
-- las políticas de UPDATE.
--
-- Comprobado con tres variantes de la política de UPDATE (USING y WITH CHECK
-- en true): el error solo desaparece al retirar la política de SELECT, lo que
-- confirmó el mecanismo.
--
-- Para no renunciar a que un archivo eliminado sea invisible para todos, la
-- eliminación se encapsula en una función SECURITY DEFINER que reutiliza la
-- autorización de V2. V1 ya no concede privilegio de UPDATE sobre app.files,
-- así que no existe ninguna otra vía para modificar la tabla.

CREATE OR REPLACE FUNCTION app.soft_delete_file(
  p_file_id     uuid,
  p_actor_email text
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
DECLARE
  v_actor uuid := app.current_user_id();
BEGIN
  IF v_actor IS NULL THEN
    RAISE EXCEPTION 'No hay usuario en el contexto de la transacción'
      USING ERRCODE = 'insufficient_privilege';
  END IF;

  IF p_file_id IS NULL OR p_actor_email IS NULL OR btrim(p_actor_email) = '' THEN
    RAISE EXCEPTION 'p_file_id y p_actor_email son obligatorios'
      USING ERRCODE = 'null_value_not_allowed';
  END IF;

  -- Devuelve false en lugar de lanzar: la API registra el intento denegado.
  IF NOT app.can_delete_file(p_file_id) THEN
    RETURN false;
  END IF;

  UPDATE app.files
     SET deleted_at = now(),
         deleted_by_id = v_actor,
         deleted_by_email = p_actor_email
   WHERE id = p_file_id
     AND deleted_at IS NULL;

  RETURN FOUND;
END;
$$;

COMMENT ON FUNCTION app.soft_delete_file(uuid, text) IS
  'Única vía de eliminación lógica. Valida el alcance con can_delete_file y deja constancia de quién eliminó. Devuelve false si no está autorizado.';

REVOKE ALL ON FUNCTION app.soft_delete_file(uuid, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app.soft_delete_file(uuid, text) TO app_runtime;


-- ---------------------------------------------------------------------------
-- audit.action_logs
-- ---------------------------------------------------------------------------
-- Solo lectura y solo para gerentes y administradores. La escritura pasa
-- siempre por audit.append_event, que es SECURITY DEFINER; por eso no existen
-- políticas de INSERT, UPDATE ni DELETE. El trigger de V1 impide modificarlo.

CREATE POLICY action_logs_select ON audit.action_logs
  FOR SELECT TO app_runtime
  USING (app.can_view_history(actor_user_id, actor_area_id));

COMMENT ON POLICY action_logs_select ON audit.action_logs IS
  'Admin: todo. Gerente: sus acciones y las ocurridas en sus áreas. Estándar y jefe de área: sin acceso.';


-- ---------------------------------------------------------------------------
-- app.login_rate_limits
-- ---------------------------------------------------------------------------
-- Tabla operativa que no guarda datos personales en claro: la clave es un HMAC
-- de correo e IP. Solo la API la consulta y actualiza, así que se concede
-- acceso completo al rol de ejecución y a ningún otro.

CREATE POLICY login_rate_limits_runtime ON app.login_rate_limits
  FOR ALL TO app_runtime
  USING (true)
  WITH CHECK (true);

COMMENT ON POLICY login_rate_limits_runtime ON app.login_rate_limits IS
  'Tabla operativa de uso exclusivo de la API; no guarda datos personales en claro.';

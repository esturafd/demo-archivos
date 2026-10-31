-- V5: revocación de una sesión por su identificador.
--
-- El cierre de la sesión propia ya existe: app.revoke_session(token_hash) opera
-- sobre el hash del token de la cookie. Pero el listado de dispositivos
-- conectados (GET /api/auth/sessions) devuelve identificadores de sesión, no
-- tokens: el cliente no conoce el token de sus otras sesiones y no debe
-- conocerlo. Para poder cerrar una sesión concreta desde esa lista hace falta
-- una función que actúe por id.
--
-- Autorización: solo el propietario de la sesión o un administrador. La
-- comprobación se hace aquí, con el contexto de la transacción, para que no
-- exista ninguna vía que revoque una sesión ajena.
--
-- Se mantiene el patrón del resto de funciones de sesiones: SECURITY DEFINER
-- (app.sessions no tiene privilegios ni políticas para app_runtime) y
-- search_path fijo.


CREATE OR REPLACE FUNCTION app.revoke_session_by_id(p_session_id uuid)
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
  WITH revoked AS (
    UPDATE app.sessions s
       SET revoked_at = now()
     WHERE s.id = p_session_id
       AND s.revoked_at IS NULL
       -- El propietario de la sesión o un administrador. Sin contexto,
       -- current_user_id() es NULL e is_admin() es false, así que deniega.
       AND (s.user_id = app.current_user_id() OR app.is_admin())
    RETURNING 1
  )
  SELECT EXISTS (SELECT 1 FROM revoked)
$$;

COMMENT ON FUNCTION app.revoke_session_by_id(uuid) IS
  'Revoca una sesión por su identificador. Solo el propietario o un administrador. Devuelve false si no existe, ya estaba revocada o no hay permiso.';

REVOKE ALL ON FUNCTION app.revoke_session_by_id(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app.revoke_session_by_id(uuid) TO app_runtime;


-- ---------------------------------------------------------------------------
-- Identificador de la sesión actual
-- ---------------------------------------------------------------------------
-- Para que la API pueda distinguir la sesión con la que se está navegando
-- dentro del listado de dispositivos conectados. La API no puede consultar
-- app.sessions con SQL directo, así que la resolución pasa por una función.

CREATE OR REPLACE FUNCTION app.session_id_for_token(p_token_hash text)
RETURNS uuid
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
  SELECT s.id
    FROM app.sessions s
   WHERE s.token_hash = p_token_hash
     AND s.revoked_at IS NULL
     AND s.expires_at > now()
$$;

COMMENT ON FUNCTION app.session_id_for_token(text) IS
  'Identificador de la sesión activa correspondiente a un token, o NULL. Para marcar la sesión actual en el listado.';

REVOKE ALL ON FUNCTION app.session_id_for_token(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app.session_id_for_token(text) TO app_runtime;

import type { Credentials } from '@/auth/domain/credentials'
import type { SessionSummary } from '@/auth/domain/session'

/**
 * Puerto del módulo `auth`: lo que la aplicación necesita de la identidad.
 *
 * <p>La implementación (HTTP) vive en `infrastructure`; los casos de uso usan
 * esta interfaz, no axios.
 */
export interface AuthPort {
  /** Inicia sesión. La sesión viaja en una cookie; el token no se expone. */
  login(credentials: Credentials): Promise<void>

  /** Cierra la sesión actual en el servidor. */
  logout(): Promise<void>

  /** Sesiones activas propias. */
  listSessions(): Promise<SessionSummary[]>

  /** Revoca una sesión concreta (propia, o cualquiera si es administrador). */
  revokeSession(sessionId: string): Promise<void>
}

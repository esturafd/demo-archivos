import { ApiError } from '@/shared/error/ApiError'
import { httpClient } from '@/shared/http/httpClient'
import { currentSessionSchema, type SessionUser } from '@/shared/session/model'

/**
 * Usuario de la sesión actual, o `null` si no hay sesión.
 *
 * <p>El 401 aquí es esperado (aún no se ha iniciado sesión), así que se marca la
 * petición para que no dispare el manejador global de «sesión caducada».
 */
export async function fetchCurrentUser(): Promise<SessionUser | null> {
  try {
    const { data } = await httpClient.get('/auth/sessions/current', {
      skipUnauthenticated: true,
    })
    return currentSessionSchema.parse(data).user
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null
    }
    throw error
  }
}

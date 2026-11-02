import axios, { type AxiosInstance } from 'axios'
import { env } from '@/shared/config/env'
import { toApiError } from '@/shared/error/toApiError'
import { CSRF_HEADER, readCsrfToken } from '@/shared/http/csrf'

// Permite marcar peticiones que esperan un 401 (la comprobación de sesión) para
// que no disparen el manejador global de «sesión caducada».
declare module 'axios' {
  export interface AxiosRequestConfig {
    skipUnauthenticated?: boolean
  }
}

const UNSAFE_METHODS = new Set(['post', 'put', 'patch', 'delete'])

let onUnauthenticated: (() => void) | null = null

/** Registra (o limpia) el manejador global de sesión caducada. */
export function setOnUnauthenticated(handler: (() => void) | null): void {
  onUnauthenticated = handler
}

/**
 * Cliente HTTP único de la aplicación.
 *
 * <p>Va con `withCredentials` (cookies), añade el CSRF en métodos que modifican
 * estado y traduce cualquier fallo al error uniforme. Un 401 inesperado avisa al
 * manejador global para que la aplicación vuelva al inicio de sesión.
 */
export const httpClient: AxiosInstance = axios.create({
  baseURL: env.apiBaseUrl,
  withCredentials: true,
  headers: { Accept: 'application/json' },
})

httpClient.interceptors.request.use((config) => {
  const method = (config.method ?? 'get').toLowerCase()
  if (UNSAFE_METHODS.has(method)) {
    const token = readCsrfToken()
    if (token) {
      config.headers.set(CSRF_HEADER, token)
    }
  }
  return config
})

httpClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    const apiError = toApiError(error)
    const skip = axios.isAxiosError(error) && error.config?.skipUnauthenticated === true
    if (apiError.status === 401 && !skip) {
      onUnauthenticated?.()
    }
    return Promise.reject(apiError)
  },
)

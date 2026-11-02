import axios from 'axios'
import { ApiError } from '@/shared/error/ApiError'
import { apiErrorBodySchema } from '@/shared/error/errorSchema'

/**
 * Traduce cualquier fallo (de axios o no) al error uniforme de la aplicación.
 *
 * <p>Si la respuesta trae el cuerpo de error de la API, se conserva tal cual;
 * si no (por ejemplo, un fallo de red), se sintetiza uno legible.
 */
export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error
  }

  if (axios.isAxiosError(error)) {
    const status = error.response?.status ?? 0
    const parsed = apiErrorBodySchema.safeParse(error.response?.data)
    if (parsed.success) {
      return new ApiError({
        status,
        code: parsed.data.code,
        message: parsed.data.message,
        requestId: parsed.data.requestId,
      })
    }
    return new ApiError({
      status,
      code: status === 0 ? 'network_error' : 'unexpected_error',
      message:
        status === 0
          ? 'No se pudo conectar con el servidor.'
          : 'Ocurrió un error inesperado.',
    })
  }

  return new ApiError({
    status: 0,
    code: 'unexpected_error',
    message: 'Ocurrió un error inesperado.',
  })
}

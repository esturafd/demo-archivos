/**
 * Error uniforme de la aplicación.
 *
 * <p>Refleja el cuerpo de error de la API (`{ code, message, requestId }`) más el
 * estado HTTP. Es lo que reciben los componentes tras cualquier fallo, en vez de
 * un error crudo de axios.
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly requestId?: string

  constructor(params: { status: number; code: string; message: string; requestId?: string }) {
    super(params.message)
    this.name = 'ApiError'
    this.status = params.status
    this.code = params.code
    this.requestId = params.requestId
  }
}

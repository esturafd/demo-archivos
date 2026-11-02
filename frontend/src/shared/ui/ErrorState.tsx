import { ApiError } from '@/shared/error/ApiError'

/** Estado de error: mensaje del servidor y su referencia, si la hay. */
export function ErrorState({ error }: { error: unknown }) {
  const apiError = error instanceof ApiError ? error : null
  return (
    <div className="state" role="alert">
      <h2>No se pudo completar la operación</h2>
      <p>{apiError?.message ?? 'Ocurrió un error inesperado.'}</p>
      {apiError?.requestId ? <small>Referencia: {apiError.requestId}</small> : null}
    </div>
  )
}

/**
 * CSRF por el patrón de doble envío.
 *
 * <p>La autenticación es por cookie, así que el cliente debe reenviar el valor de
 * la cookie `csrf-token` en la cabecera `X-CSRF-TOKEN` en todo método que
 * modifica estado.
 */
export const CSRF_COOKIE = 'csrf-token'
export const CSRF_HEADER = 'X-CSRF-TOKEN'

/** Valor de la cookie CSRF, o `null` si no está. */
export function readCsrfToken(): string | null {
  const prefix = `${CSRF_COOKIE}=`
  const row = document.cookie.split('; ').find((item) => item.startsWith(prefix))
  if (!row) {
    return null
  }
  const value = row.slice(prefix.length)
  return value === '' ? null : value
}

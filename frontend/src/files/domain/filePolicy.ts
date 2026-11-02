/**
 * Reglas puras del archivo que se sube. Reflejan las del backend
 * (`FileContentPolicy`): el cliente valida antes de enviar, pero **el servidor
 * vuelve a validar** (tipo real por firma y límite de tamaño).
 */

/** 20 MB, en bytes. Igual que el `CHECK` de `app.files`. */
export const MAX_FILE_BYTES = 20_971_520

/** Tipos MIME permitidos, con su etiqueta legible. */
export const ALLOWED_TYPES = [
  { mime: 'application/pdf', label: 'PDF' },
  { mime: 'image/png', label: 'PNG' },
  { mime: 'image/jpeg', label: 'JPEG' },
  { mime: 'image/gif', label: 'GIF' },
  { mime: 'image/webp', label: 'WebP' },
  { mime: 'text/plain', label: 'Texto' },
  { mime: 'text/csv', label: 'CSV' },
] as const

export type AllowedMime = (typeof ALLOWED_TYPES)[number]['mime']

/** Motivo por el que un archivo no se admite en el cliente. */
export type FileViolation = 'empty' | 'tooLarge' | 'typeNotAllowed'

/** Normaliza un tipo MIME: sin parámetros y en minúsculas. */
export function normalizeType(type: string): string {
  const base = type.split(';')[0] ?? ''
  return base.trim().toLowerCase()
}

/**
 * Comprueba el archivo antes de enviarlo.
 *
 * <p>Si el navegador no declara el tipo (algunos CSV), no se rechaza aquí: el
 * servidor decide por la firma real del contenido.
 *
 * @return el motivo del rechazo, o `null` si el archivo es aceptable
 */
export function validateFile(file: File): FileViolation | null {
  if (file.size <= 0) {
    return 'empty'
  }
  if (file.size > MAX_FILE_BYTES) {
    return 'tooLarge'
  }
  const type = normalizeType(file.type)
  if (type && !ALLOWED_TYPES.some((allowed) => allowed.mime === type)) {
    return 'typeNotAllowed'
  }
  return null
}

/** Etiqueta corta de un tipo MIME (PDF, PNG…). */
export function typeLabel(mime: string): string {
  const normalized = normalizeType(mime)
  const found = ALLOWED_TYPES.find((allowed) => allowed.mime === normalized)
  if (found) {
    return found.label
  }
  const slash = normalized.indexOf('/')
  return (slash >= 0 ? normalized.slice(slash + 1) : normalized).toUpperCase()
}

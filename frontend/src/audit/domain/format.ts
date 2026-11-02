// Formato de presentación del módulo `audit`.

/** Fecha y hora localizadas (31 ene 2026, 10:15). */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return '—'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '—'
  }
  return date.toLocaleString('es', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/** Metadatos como texto legible (JSON con sangría), o `—` si no hay. */
export function formatMetadata(metadata: Record<string, unknown> | null | undefined): string {
  if (!metadata || Object.keys(metadata).length === 0) {
    return '—'
  }
  return JSON.stringify(metadata, null, 2)
}

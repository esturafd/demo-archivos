// Formato de presentación del módulo `files` (tamaño y fecha legibles).

const UNITS = ['B', 'KB', 'MB', 'GB'] as const

/** Tamaño en bytes con unidad y separador decimal en español (1,2 MB). */
export function formatBytes(bytes: number): string {
  let value = bytes
  let unit = 0
  while (value >= 1024 && unit < UNITS.length - 1) {
    value /= 1024
    unit += 1
  }
  const rounded = unit === 0 ? value : Math.round(value * 10) / 10
  return `${rounded.toLocaleString('es', { maximumFractionDigits: 1 })} ${UNITS[unit]}`
}

/** Fecha en formato corto localizado (31 ene 2026). */
export function formatDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '—'
  }
  return date.toLocaleDateString('es', { day: '2-digit', month: 'short', year: 'numeric' })
}

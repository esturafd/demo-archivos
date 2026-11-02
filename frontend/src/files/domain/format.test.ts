import { describe, expect, it } from 'vitest'
import { formatBytes, formatDate } from '@/files/domain/format'

describe('formatBytes', () => {
  it('muestra los bytes sin decimales', () => {
    expect(formatBytes(0)).toBe('0 B')
    expect(formatBytes(512)).toBe('512 B')
  })

  it('sube de unidad y usa la coma decimal', () => {
    expect(formatBytes(1536)).toBe('1,5 KB')
    expect(formatBytes(480 * 1024)).toBe('480 KB')
  })
})

describe('formatDate', () => {
  it('formatea una fecha válida', () => {
    expect(formatDate('2026-01-31T10:15:30Z')).toContain('2026')
  })

  it('devuelve un guion si no es válida', () => {
    expect(formatDate('no-es-fecha')).toBe('—')
  })
})

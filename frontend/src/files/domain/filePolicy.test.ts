import { describe, expect, it } from 'vitest'
import {
  MAX_FILE_BYTES,
  normalizeType,
  typeLabel,
  validateFile,
} from '@/files/domain/filePolicy'

/** Objeto mínimo con lo que usan las reglas (tamaño y tipo declarado). */
function fakeFile(size: number, type: string): File {
  return { size, type } as File
}

describe('validateFile', () => {
  it('rechaza un archivo vacío', () => {
    expect(validateFile(fakeFile(0, 'application/pdf'))).toBe('empty')
  })

  it('rechaza un archivo que supera el límite', () => {
    expect(validateFile(fakeFile(MAX_FILE_BYTES + 1, 'application/pdf'))).toBe('tooLarge')
  })

  it('rechaza un tipo no permitido', () => {
    expect(validateFile(fakeFile(10, 'application/x-msdownload'))).toBe('typeNotAllowed')
  })

  it('acepta un tipo permitido', () => {
    expect(validateFile(fakeFile(10, 'image/png'))).toBeNull()
  })

  it('acepta el límite exacto', () => {
    expect(validateFile(fakeFile(MAX_FILE_BYTES, 'application/pdf'))).toBeNull()
  })

  it('no rechaza si el navegador no declara el tipo', () => {
    expect(validateFile(fakeFile(10, ''))).toBeNull()
  })
})

describe('typeLabel', () => {
  it('traduce los tipos conocidos', () => {
    expect(typeLabel('application/pdf')).toBe('PDF')
    expect(typeLabel('image/jpeg; charset=binary')).toBe('JPEG')
  })

  it('cae al subtipo en mayúsculas para los desconocidos', () => {
    expect(typeLabel('application/zip')).toBe('ZIP')
  })
})

describe('normalizeType', () => {
  it('quita parámetros y normaliza a minúsculas', () => {
    expect(normalizeType('TEXT/CSV; charset=utf-8')).toBe('text/csv')
  })
})

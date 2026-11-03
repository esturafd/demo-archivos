import { beforeEach, describe, expect, it } from 'vitest'
import { readCsrfToken } from '@/shared/http/csrf'

function clearCookies() {
  document.cookie = 'csrf-token=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
}

describe('readCsrfToken', () => {
  beforeEach(clearCookies)

  it('devuelve el valor de la cookie csrf-token', () => {
    document.cookie = 'csrf-token=valor-de-prueba; path=/'
    expect(readCsrfToken()).toBe('valor-de-prueba')
  })

  it('devuelve null si la cookie no está', () => {
    expect(readCsrfToken()).toBeNull()
  })
})

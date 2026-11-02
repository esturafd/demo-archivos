import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/error/ApiError'
import { httpClient, setOnUnauthenticated } from '@/shared/http/httpClient'
import { server } from '@/test/server'

const API = 'http://localhost:3000/api'

function clearCookies() {
  document.cookie = 'csrf-token=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
}

describe('httpClient', () => {
  beforeEach(clearCookies)

  it('añade el CSRF en los métodos que modifican estado', async () => {
    let received: string | null = null
    server.use(
      http.post(`${API}/files`, ({ request }) => {
        received = request.headers.get('x-csrf-token')
        return HttpResponse.json({ ok: true }, { status: 201 })
      }),
    )
    document.cookie = 'csrf-token=token-de-prueba; path=/'

    await httpClient.post('/files', {})

    expect(received).toBe('token-de-prueba')
  })

  it('no añade el CSRF en GET', async () => {
    let received: string | null = null
    server.use(
      http.get(`${API}/files`, ({ request }) => {
        received = request.headers.get('x-csrf-token')
        return HttpResponse.json({ items: [], nextCursor: null })
      }),
    )
    document.cookie = 'csrf-token=token-de-prueba; path=/'

    await httpClient.get('/files')

    expect(received).toBeNull()
  })

  it('traduce el error uniforme de la API', async () => {
    server.use(
      http.get(`${API}/files`, () =>
        HttpResponse.json(
          { code: 'conflict', message: 'La operación entra en conflicto.', requestId: 'req-1' },
          { status: 409 },
        ),
      ),
    )

    await expect(httpClient.get('/files')).rejects.toMatchObject({
      code: 'conflict',
      status: 409,
      requestId: 'req-1',
    })
  })

  it('avisa al manejador global ante un 401 inesperado', async () => {
    const handler = vi.fn()
    setOnUnauthenticated(handler)
    server.use(
      http.get(`${API}/files`, () =>
        HttpResponse.json({ code: 'unauthenticated', message: 'x' }, { status: 401 }),
      ),
    )

    await expect(httpClient.get('/files')).rejects.toBeInstanceOf(ApiError)
    expect(handler).toHaveBeenCalledOnce()

    setOnUnauthenticated(null)
  })

  it('no avisa cuando la petición espera el 401 (comprobación de sesión)', async () => {
    const handler = vi.fn()
    setOnUnauthenticated(handler)
    server.use(
      http.get(`${API}/auth/sessions/current`, () =>
        HttpResponse.json({ code: 'unauthenticated', message: 'x' }, { status: 401 }),
      ),
    )

    await expect(
      httpClient.get('/auth/sessions/current', { skipUnauthenticated: true }),
    ).rejects.toBeInstanceOf(ApiError)
    expect(handler).not.toHaveBeenCalled()

    setOnUnauthenticated(null)
  })
})

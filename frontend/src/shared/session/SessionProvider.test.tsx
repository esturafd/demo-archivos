import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { SessionProvider } from '@/shared/session/SessionProvider'
import { useSession } from '@/shared/session/sessionContext'
import { server } from '@/test/server'

const API = 'http://localhost:3000/api'

function Probe() {
  const { status, user } = useSession()
  return (
    <span data-testid="probe">
      {status}:{user?.email ?? '-'}
    </span>
  )
}

function renderProbe() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <SessionProvider>
        <Probe />
      </SessionProvider>
    </QueryClientProvider>,
  )
}

describe('sesión', () => {
  it('con sesión, queda autenticada', async () => {
    server.use(
      http.get(`${API}/auth/sessions/current`, () =>
        HttpResponse.json({
          user: { id: 'u1', email: 'ana@demo.local', name: 'Ana', role: 'admin', active: true },
          session: null,
        }),
      ),
    )

    renderProbe()

    await waitFor(() =>
      expect(screen.getByTestId('probe')).toHaveTextContent('authenticated:ana@demo.local'),
    )
  })

  it('sin sesión (401), queda anónima', async () => {
    server.use(
      http.get(`${API}/auth/sessions/current`, () =>
        HttpResponse.json({ code: 'unauthenticated', message: 'Debes iniciar sesión.' }, { status: 401 }),
      ),
    )

    renderProbe()

    await waitFor(() => expect(screen.getByTestId('probe')).toHaveTextContent('anonymous:-'))
  })
})

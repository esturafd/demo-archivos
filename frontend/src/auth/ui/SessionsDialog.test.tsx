import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { SessionsDialog } from '@/auth/ui/SessionsDialog'
import { server } from '@/test/server'

const API = 'http://localhost:3000/api'

const SESSIONS = {
  sessions: [
    {
      id: 's1',
      createdAt: '2026-01-31T10:00:00Z',
      expiresAt: '2026-01-31T22:00:00Z',
      lastSeenAt: '2026-01-31T12:00:00Z',
      ipAddress: '203.0.113.10',
      userAgent: 'Mozilla/5.0',
      current: true,
    },
    {
      id: 's2',
      createdAt: '2026-01-30T09:00:00Z',
      expiresAt: '2026-01-30T21:00:00Z',
      lastSeenAt: '2026-01-30T09:30:00Z',
      ipAddress: '203.0.113.11',
      userAgent: 'Mozilla/5.0',
      current: false,
    },
  ],
}

function renderDialog() {
  server.use(http.get(`${API}/auth/sessions`, () => HttpResponse.json(SESSIONS)))
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <SessionsDialog open onClose={vi.fn()} />
    </QueryClientProvider>,
  )
}

describe('sesiones activas', () => {
  it('lista las sesiones y marca la actual', async () => {
    renderDialog()

    expect(await screen.findByText('203.0.113.10')).toBeInTheDocument()
    expect(screen.getByText('esta sesión')).toBeInTheDocument()
    // La sesión actual no se puede revocar; la otra sí.
    expect(screen.getAllByRole('button', { name: 'Revocar' })).toHaveLength(1)
  })

  it('revoca la sesión elegida', async () => {
    renderDialog()
    const revoked = vi.fn()
    server.use(
      http.delete(`${API}/auth/sessions/s2`, () => {
        revoked()
        return new HttpResponse(null, { status: 204 })
      }),
    )

    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Revocar' }))

    expect(revoked).toHaveBeenCalledOnce()
  })
})

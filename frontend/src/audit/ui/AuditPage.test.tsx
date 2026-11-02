import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it } from 'vitest'
import { AuditPage } from '@/audit/ui/AuditPage'
import { SessionProvider } from '@/shared/session/SessionProvider'
import { server } from '@/test/server'

const API = 'http://localhost:3000/api'

const MANAGER = {
  id: 'u2',
  email: 'gerente@demo.local',
  name: 'Gerente',
  role: 'manager',
  areaId: 'a1',
  areaName: 'Área A',
  active: true,
  createdAt: 'x',
}
const ADMIN = {
  id: 'ad1',
  email: 'ana@demo.local',
  name: 'Ana Admin',
  role: 'admin',
  areaId: null,
  areaName: null,
  active: true,
  createdAt: 'x',
}

const E1 = {
  id: 1,
  occurredAt: '2026-01-31T10:15:30Z',
  action: 'file_uploaded',
  actorUserId: 'm1',
  actorEmail: 'maria@demo.local',
  actorRole: 'standard',
  actorAreaName: 'Área A',
  fileId: 'f1',
  fileName: 'informe.pdf',
  ipAddress: '203.0.113.10',
  requestId: 'req-1',
  metadata: { size: 20480 },
}
const E2 = {
  id: 2,
  occurredAt: '2026-01-30T09:00:00Z',
  action: 'login_succeeded',
  actorUserId: 'm1',
  actorEmail: 'maria@demo.local',
  actorRole: 'standard',
  actorAreaName: 'Área A',
  fileId: null,
  fileName: null,
  ipAddress: '203.0.113.11',
  requestId: 'req-2',
  metadata: null,
}

let ownersCalled = false
let usersCalled = false

beforeEach(() => {
  ownersCalled = false
  usersCalled = false
  server.use(
    http.get(`${API}/audit/events`, () => HttpResponse.json({ items: [E1, E2], nextCursor: null })),
    http.get(`${API}/files/owners`, () => {
      ownersCalled = true
      return HttpResponse.json([{ id: 'm1', email: 'maria@demo.local', name: 'María Pérez' }])
    }),
    http.get(`${API}/users`, () => {
      usersCalled = true
      return HttpResponse.json([ADMIN, MANAGER])
    }),
  )
})

type SessionFixture = {
  id: string
  email: string
  name: string
  role: string
  areaId: string | null
  areaName: string | null
  active: boolean
  createdAt: string
}

function renderAudit(user: SessionFixture) {
  server.use(
    http.get(`${API}/auth/sessions/current`, () =>
      HttpResponse.json({ user, session: null }),
    ),
  )
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <SessionProvider>
        <MemoryRouter initialEntries={['/historial']}>
          <AuditPage />
        </MemoryRouter>
      </SessionProvider>
    </QueryClientProvider>,
  )
}

describe('página de historial', () => {
  it('lista los eventos y despliega el detalle de la fila', async () => {
    renderAudit(MANAGER)

    expect(await screen.findByText('informe.pdf')).toBeInTheDocument()
    expect(
      within(screen.getByRole('table')).getByText('Archivo cargado'),
    ).toBeInTheDocument()

    const user = userEvent.setup()
    await user.click(screen.getAllByRole('button', { name: 'Ver detalle' })[0])

    expect(screen.getByText('req-1')).toBeInTheDocument()
    expect(screen.getByText(/20480/)).toBeInTheDocument()
  })

  it('el filtro de acción viaja al servidor', async () => {
    const actions: (string | null)[] = []
    server.use(
      http.get(`${API}/audit/events`, ({ request }) => {
        actions.push(new URL(request.url).searchParams.get('action'))
        return HttpResponse.json({ items: [], nextCursor: null })
      }),
    )
    renderAudit(MANAGER)

    const user = userEvent.setup()
    await user.selectOptions(await screen.findByLabelText('Filtrar por acción'), 'file_uploaded')

    await waitFor(() => expect(actions).toContain('file_uploaded'))
  })

  it('el gerente filtra por los actores de su alcance', async () => {
    const actors: (string | null)[] = []
    server.use(
      http.get(`${API}/audit/events`, ({ request }) => {
        actors.push(new URL(request.url).searchParams.get('actorId'))
        return HttpResponse.json({ items: [], nextCursor: null })
      }),
    )
    renderAudit(MANAGER)

    const user = userEvent.setup()
    await screen.findByRole('option', { name: 'María Pérez' })
    await user.selectOptions(screen.getByLabelText('Filtrar por actor'), 'm1')

    await waitFor(() => expect(actors).toContain('m1'))
    expect(ownersCalled).toBe(true)
    expect(usersCalled).toBe(false)
  })

  it('el administrador toma los actores de los usuarios', async () => {
    renderAudit(ADMIN)

    expect(await screen.findByRole('option', { name: 'Ana Admin' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Gerente' })).toBeInTheDocument()
    expect(usersCalled).toBe(true)
    expect(ownersCalled).toBe(false)
  })

  it('la paginación avanza con el cursor', async () => {
    const cursors: (string | null)[] = []
    server.use(
      http.get(`${API}/audit/events`, ({ request }) => {
        const cursor = new URL(request.url).searchParams.get('cursor')
        cursors.push(cursor)
        return cursor
          ? HttpResponse.json({ items: [E2], nextCursor: null })
          : HttpResponse.json({ items: [E1], nextCursor: 'CURSOR_1' })
      }),
    )
    renderAudit(MANAGER)

    const user = userEvent.setup()
    await screen.findByText('informe.pdf')
    await user.click(screen.getByRole('button', { name: 'Siguiente' }))

    await waitFor(() => expect(cursors).toContain('CURSOR_1'))
  })
})

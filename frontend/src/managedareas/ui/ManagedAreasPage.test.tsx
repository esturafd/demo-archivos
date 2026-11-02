import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it } from 'vitest'
import { ManagedAreasPage } from '@/managedareas/ui/ManagedAreasPage'
import { SessionProvider } from '@/shared/session/SessionProvider'
import { server } from '@/test/server'

const API = 'http://localhost:3000/api'

const ANA = {
  id: 'ad1',
  email: 'ana@demo.local',
  name: 'Ana Admin',
  role: 'admin',
  areaId: null,
  areaName: null,
  active: true,
  createdAt: 'x',
}
const MARIA = {
  id: 'u2',
  email: 'maria@demo.local',
  name: 'María Pérez',
  role: 'manager',
  areaId: null,
  areaName: null,
  active: true,
  createdAt: 'x',
}
const JUAN = {
  id: 'u3',
  email: 'juan@demo.local',
  name: 'Juan Gómez',
  role: 'manager',
  areaId: null,
  areaName: null,
  active: true,
  createdAt: 'x',
}

const AREAS = [
  { id: 'a1', name: 'Área A', description: null, active: true, createdAt: 'x', updatedAt: 'x' },
  { id: 'a2', name: 'Área B', description: null, active: true, createdAt: 'x', updatedAt: 'x' },
  { id: 'a3', name: 'Área C', description: null, active: false, createdAt: 'x', updatedAt: 'x' },
]

let usersCalled = false

beforeEach(() => {
  usersCalled = false
  server.use(
    http.get(`${API}/areas`, () => HttpResponse.json(AREAS)),
    http.get(`${API}/users`, () => {
      usersCalled = true
      return HttpResponse.json([ANA, MARIA, JUAN])
    }),
    http.get(`${API}/users/:id/managed-areas`, () =>
      HttpResponse.json({ areas: [{ id: 'a1', name: 'Área A', active: true }] }),
    ),
  )
})

function renderPage(user: typeof ANA) {
  server.use(
    http.get(`${API}/auth/sessions/current`, () =>
      HttpResponse.json({ user, session: null }),
    ),
  )
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <SessionProvider>
        <MemoryRouter initialEntries={['/areas-gestionadas']}>
          <ManagedAreasPage />
        </MemoryRouter>
      </SessionProvider>
    </QueryClientProvider>,
  )
}

describe('áreas gestionadas', () => {
  it('un gerente ve las suyas, sin selector ni edición', async () => {
    renderPage(MARIA)

    expect(await screen.findByText('Área A')).toBeInTheDocument()
    expect(screen.queryByLabelText('Elegir el gerente')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Editar áreas' })).not.toBeInTheDocument()
    // La lista de usuarios es solo del administrador.
    expect(usersCalled).toBe(false)
  })

  it('el administrador elige gerente (por defecto, el primero)', async () => {
    renderPage(ANA)

    const select = await screen.findByLabelText('Elegir el gerente')
    expect(select).toHaveValue('u2')
    expect(screen.getByRole('option', { name: 'María Pérez' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Juan Gómez' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Editar áreas' })).toBeInTheDocument()
  })

  it('reemplazar el conjunto envía las áreas elegidas', async () => {
    const bodies: unknown[] = []
    server.use(
      http.put(`${API}/users/u2/managed-areas`, async ({ request }) => {
        bodies.push(await request.json())
        return HttpResponse.json({ areas: [] })
      }),
    )
    renderPage(ANA)

    const user = userEvent.setup()
    await screen.findByRole('button', { name: 'Editar áreas' })
    await user.click(screen.getByRole('button', { name: 'Editar áreas' }))

    // El conjunto inicial es {a1}; se añade a2.
    await user.click(screen.getByRole('checkbox', { name: 'Área B' }))
    await user.click(screen.getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0]).toEqual({ areaIds: ['a1', 'a2'] })
  })

  it('rechaza el conjunto vacío sin llamar al servidor', async () => {
    let puts = 0
    server.use(
      http.put(`${API}/users/u2/managed-areas`, () => {
        puts += 1
        return HttpResponse.json({ areas: [] })
      }),
    )
    renderPage(ANA)

    const user = userEvent.setup()
    await screen.findByRole('button', { name: 'Editar áreas' })
    await user.click(screen.getByRole('button', { name: 'Editar áreas' }))

    await user.click(screen.getByRole('checkbox', { name: 'Área A' }))
    await user.click(screen.getByRole('button', { name: 'Guardar' }))

    expect(await screen.findByText('Elige al menos un área.')).toBeInTheDocument()
    expect(puts).toBe(0)
  })
})

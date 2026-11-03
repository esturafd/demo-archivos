import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it } from 'vitest'
import { SessionProvider } from '@/shared/session/SessionProvider'
import { server } from '@/test/server'
import { UsersPage } from '@/users/ui/UsersPage'

const API = 'http://localhost:3000/api'

const ANA = {
  id: 'u1',
  email: 'ana@demo.local',
  name: 'Ana Admin',
  role: 'admin',
  areaId: null,
  areaName: null,
  active: true,
  createdAt: '2026-01-01T08:00:00Z',
}
const MARIA = {
  id: 'u2',
  email: 'maria@demo.local',
  name: 'María Pérez',
  role: 'standard',
  areaId: 'a1',
  areaName: 'Área A',
  active: true,
  createdAt: '2026-01-02T08:00:00Z',
}
const JUAN = {
  id: 'u3',
  email: 'juan@demo.local',
  name: 'Juan Gómez',
  role: 'manager',
  areaId: null,
  areaName: null,
  active: false,
  createdAt: '2026-01-03T08:00:00Z',
}

const AREAS = [
  { id: 'a1', name: 'Área A', description: null, active: true, createdAt: 'x', updatedAt: 'x' },
  { id: 'a2', name: 'Área B', description: null, active: false, createdAt: 'x', updatedAt: 'x' },
]

beforeEach(() => {
  server.use(
    http.get(`${API}/users`, () => HttpResponse.json([ANA, MARIA, JUAN])),
    http.get(`${API}/areas`, () => HttpResponse.json(AREAS)),
  )
})

function renderUsers() {
  server.use(
    http.get(`${API}/auth/sessions/current`, () =>
      HttpResponse.json({ user: ANA, session: null }),
    ),
  )
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <SessionProvider>
        <MemoryRouter initialEntries={['/usuarios']}>
          <UsersPage />
        </MemoryRouter>
      </SessionProvider>
    </QueryClientProvider>,
  )
}

describe('página de usuarios', () => {
  it('lista los usuarios, con el selector de área y el enlace a sus archivos', async () => {
    renderUsers()

    expect(await screen.findByText('María Pérez')).toBeInTheDocument()
    expect(screen.getByText('juan@demo.local')).toBeInTheDocument()
    expect(screen.getByLabelText('Filtrar por área')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ver archivos de María Pérez' })).toHaveAttribute(
      'href',
      '/archivos?owner=u2',
    )
    // El administrador no puede desactivarse a sí mismo.
    expect(screen.queryByRole('button', { name: 'Desactivar Ana Admin' })).not.toBeInTheDocument()
  })

  it('el filtro de área viaja al servidor', async () => {
    const requested: (string | null)[] = []
    server.use(
      http.get(`${API}/users`, ({ request }) => {
        requested.push(new URL(request.url).searchParams.get('areaId'))
        return HttpResponse.json([MARIA])
      }),
    )
    renderUsers()

    const user = userEvent.setup()
    await user.selectOptions(await screen.findByLabelText('Filtrar por área'), 'a1')

    await waitFor(() => expect(requested).toContain('a1'))
  })

  it('crea un usuario con el cuerpo esperado', async () => {
    const bodies: unknown[] = []
    server.use(
      http.post(`${API}/users`, async ({ request }) => {
        bodies.push(await request.json())
        return HttpResponse.json(MARIA, { status: 201 })
      }),
    )
    renderUsers()

    const user = userEvent.setup()
    await screen.findByText('María Pérez')
    await user.click(screen.getByRole('button', { name: 'Nuevo usuario' }))
    const dialog = await screen.findByRole('dialog')

    await user.type(within(dialog).getByLabelText('Correo'), 'nueva@demo.local')
    await user.type(within(dialog).getByLabelText('Contraseña'), 'Contraseña123')
    await user.type(within(dialog).getByLabelText('Nombre'), 'Nueva Persona')
    await user.selectOptions(within(dialog).getByLabelText('Área'), 'a1')
    await user.click(within(dialog).getByRole('button', { name: 'Crear' }))

    await waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0]).toEqual({
      email: 'nueva@demo.local',
      password: 'Contraseña123',
      name: 'Nueva Persona',
      role: 'standard',
      areaId: 'a1',
    })
  })

  it('avisa cuando el correo ya existe', async () => {
    server.use(
      http.post(`${API}/users`, () =>
        HttpResponse.json({ code: 'conflict', message: 'x' }, { status: 409 }),
      ),
    )
    renderUsers()

    const user = userEvent.setup()
    await screen.findByText('María Pérez')
    await user.click(screen.getByRole('button', { name: 'Nuevo usuario' }))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText('Correo'), 'maria@demo.local')
    await user.type(within(dialog).getByLabelText('Contraseña'), 'Contraseña123')
    await user.type(within(dialog).getByLabelText('Nombre'), 'María Pérez')
    await user.selectOptions(within(dialog).getByLabelText('Área'), 'a1')
    await user.click(within(dialog).getByRole('button', { name: 'Crear' }))

    expect(await screen.findByText('Ya existe un usuario con ese correo.')).toBeInTheDocument()
  })

  it('editar envía el cambio', async () => {
    const bodies: unknown[] = []
    server.use(
      http.patch(`${API}/users/u2`, async ({ request }) => {
        bodies.push(await request.json())
        return HttpResponse.json(MARIA)
      }),
    )
    renderUsers()

    const user = userEvent.setup()
    await screen.findByText('María Pérez')
    await user.click(screen.getByRole('button', { name: 'Editar María Pérez' }))

    const name = screen.getByLabelText('Nombre')
    await user.clear(name)
    await user.type(name, 'María P.')
    await user.click(screen.getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0]).toMatchObject({ name: 'María P.', role: 'standard', areaId: 'a1' })
  })

  it('desactivar pide confirmación y envía el estado', async () => {
    const bodies: unknown[] = []
    server.use(
      http.patch(`${API}/users/u2`, async ({ request }) => {
        bodies.push(await request.json())
        return HttpResponse.json({ ...MARIA, active: false })
      }),
    )
    renderUsers()

    const user = userEvent.setup()
    await screen.findByText('María Pérez')
    await user.click(screen.getByRole('button', { name: 'Desactivar María Pérez' }))

    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Desactivar' }))

    await waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0]).toMatchObject({ active: false })
  })
})

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it } from 'vitest'
import { AreasPage } from '@/areas/ui/AreasPage'
import { SessionProvider } from '@/shared/session/SessionProvider'
import { server } from '@/test/server'

const API = 'http://localhost:3000/api'

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

const AREA_A = {
  id: 'a1',
  name: 'Área A',
  description: 'Atención al cliente',
  active: true,
  createdAt: 'x',
  updatedAt: 'x',
}
const AREA_B = {
  id: 'a2',
  name: 'Área B',
  description: null,
  active: false,
  createdAt: 'x',
  updatedAt: 'x',
}

beforeEach(() => {
  server.use(http.get(`${API}/areas`, () => HttpResponse.json([AREA_A, AREA_B])))
})

function renderAreas(user: typeof ADMIN) {
  server.use(
    http.get(`${API}/auth/sessions/current`, () =>
      HttpResponse.json({ user, session: null }),
    ),
  )
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <SessionProvider>
        <MemoryRouter initialEntries={['/areas']}>
          <AreasPage />
        </MemoryRouter>
      </SessionProvider>
    </QueryClientProvider>,
  )
}

describe('página de áreas', () => {
  it('un rol sin permisos solo consulta', async () => {
    renderAreas({ ...ADMIN, role: 'manager' })

    expect(await screen.findByText('Área A')).toBeInTheDocument()
    expect(screen.getByText('Atención al cliente')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Nueva área' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Editar Área A' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Ver usuarios de Área A' })).not.toBeInTheDocument()
  })

  it('el administrador crea un área con el cuerpo esperado', async () => {
    const bodies: unknown[] = []
    server.use(
      http.post(`${API}/areas`, async ({ request }) => {
        bodies.push(await request.json())
        return HttpResponse.json(AREA_A, { status: 201 })
      }),
    )
    renderAreas(ADMIN)

    const user = userEvent.setup()
    await screen.findByText('Área A')
    await user.click(screen.getByRole('button', { name: 'Nueva área' }))

    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText('Nombre'), 'Área Nueva')
    await user.type(within(dialog).getByLabelText('Descripción'), '  Descripción  ')
    await user.click(within(dialog).getByRole('button', { name: 'Crear' }))

    await waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0]).toEqual({ name: 'Área Nueva', description: 'Descripción' })
  })

  it('avisa cuando el nombre ya existe', async () => {
    server.use(
      http.post(`${API}/areas`, () =>
        HttpResponse.json({ code: 'conflict', message: 'x' }, { status: 409 }),
      ),
    )
    renderAreas(ADMIN)

    const user = userEvent.setup()
    await screen.findByText('Área A')
    await user.click(screen.getByRole('button', { name: 'Nueva área' }))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText('Nombre'), 'Área A')
    await user.click(within(dialog).getByRole('button', { name: 'Crear' }))

    expect(await screen.findByText('Ya existe un área con ese nombre.')).toBeInTheDocument()
  })

  it('editar envía el cambio', async () => {
    const bodies: unknown[] = []
    server.use(
      http.patch(`${API}/areas/a1`, async ({ request }) => {
        bodies.push(await request.json())
        return HttpResponse.json(AREA_A)
      }),
    )
    renderAreas(ADMIN)

    const user = userEvent.setup()
    await screen.findByText('Área A')
    await user.click(screen.getByRole('button', { name: 'Editar Área A' }))

    const name = screen.getByLabelText('Nombre')
    await user.clear(name)
    await user.type(name, 'Área A+')
    await user.click(screen.getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0]).toMatchObject({
      name: 'Área A+',
      description: 'Atención al cliente',
      active: true,
    })
  })

  it('desactivar pide confirmación y envía el estado', async () => {
    const bodies: unknown[] = []
    server.use(
      http.patch(`${API}/areas/a1`, async ({ request }) => {
        bodies.push(await request.json())
        return HttpResponse.json({ ...AREA_A, active: false })
      }),
    )
    renderAreas(ADMIN)

    const user = userEvent.setup()
    await screen.findByText('Área A')
    await user.click(screen.getByRole('button', { name: 'Desactivar Área A' }))

    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Desactivar' }))

    await waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0]).toMatchObject({ active: false })
  })
})

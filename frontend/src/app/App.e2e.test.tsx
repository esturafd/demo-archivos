import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it } from 'vitest'
import { AppProviders } from '@/app/providers'
import { routes } from '@/app/routes'
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
const MARIA = {
  id: 'u2',
  email: 'maria@demo.local',
  name: 'María Pérez',
  role: 'standard',
  areaId: 'a1',
  areaName: 'Área A',
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

beforeEach(() => {
  server.use(
    http.get(`${API}/files/owners`, () => HttpResponse.json([MARIA])),
    http.get(`${API}/files`, () => HttpResponse.json({ items: [], nextCursor: null })),
    http.get(`${API}/areas`, () => HttpResponse.json([AREA_A])),
    http.get(`${API}/users`, () => HttpResponse.json([ADMIN, MARIA])),
    http.get(`${API}/audit/events`, () => HttpResponse.json({ items: [], nextCursor: null })),
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

function renderApp(user: SessionFixture | null, initialEntry: string) {
  // Sin `user` no se registra la sesión: la prueba define su propio manejador
  // (por ejemplo, para el inicio de sesión).
  if (user) {
    server.use(
      http.get(`${API}/auth/sessions/current`, () =>
        HttpResponse.json({ user, session: null }),
      ),
    )
  }
  const router = createMemoryRouter(routes, { initialEntries: [initialEntry] })
  return render(
    <AppProviders>
      <RouterProvider router={router} />
    </AppProviders>,
  )
}

describe('recorrido de la aplicación', () => {
  it('el estándar solo ve sus secciones', async () => {
    renderApp({ ...MARIA, role: 'standard' }, '/archivos')

    expect(await screen.findByTestId('brand-name')).toHaveTextContent('Archivos')
    expect(screen.getByRole('link', { name: 'Archivos' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Áreas' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Usuarios' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Historial' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Áreas gestionadas' })).not.toBeInTheDocument()
  })

  it('el administrador va del área a sus usuarios y a los archivos de uno', async () => {
    const requestedArea: (string | null)[] = []
    server.use(
      http.get(`${API}/users`, ({ request }) => {
        requestedArea.push(new URL(request.url).searchParams.get('areaId'))
        return HttpResponse.json([MARIA])
      }),
    )
    renderApp(ADMIN, '/areas')

    const user = userEvent.setup()
    expect(await screen.findByRole('heading', { name: 'Áreas' })).toBeInTheDocument()

    // Áreas → Usuarios filtrados por el área.
    await user.click(await screen.findByRole('link', { name: 'Ver usuarios de Área A' }))
    expect(await screen.findByRole('heading', { name: 'Usuarios' })).toBeInTheDocument()
    await waitFor(() => expect(requestedArea).toContain('a1'))
    expect(screen.getByLabelText('Filtrar por área')).toHaveValue('a1')

    // Usuarios → Archivos de ese usuario.
    await user.click(await screen.findByRole('link', { name: 'Ver archivos de María Pérez' }))
    expect(await screen.findByRole('heading', { name: 'Archivos' })).toBeInTheDocument()
  })

  it('el gerente no accede a usuarios: sin permisos', async () => {
    renderApp({ ...MARIA, role: 'manager' }, '/usuarios')

    expect(await screen.findByRole('heading', { name: 'Sin permisos' })).toBeInTheDocument()
  })

  it('tras iniciar sesión se entra al área de trabajo', async () => {
    let authenticated = false
    server.use(
      http.get(`${API}/auth/sessions/current`, () =>
        authenticated
          ? HttpResponse.json({ user: ADMIN, session: null })
          : HttpResponse.json({ code: 'unauthenticated', message: 'x' }, { status: 401 }),
      ),
      http.post(`${API}/auth/sessions`, () => {
        authenticated = true
        return HttpResponse.json({ user: ADMIN, session: null }, { status: 201 })
      }),
    )
    renderApp(null, '/archivos')

    const user = userEvent.setup()
    await user.type(await screen.findByLabelText('Correo'), 'ana@demo.local')
    await user.type(screen.getByLabelText('Contraseña'), 'Contraseña123')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByTestId('brand-name')).toHaveTextContent('Archivos')
    expect(screen.getByRole('link', { name: 'Usuarios' })).toBeInTheDocument()
  })
})

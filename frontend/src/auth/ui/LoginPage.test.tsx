import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { LoginPage } from '@/auth/ui/LoginPage'
import { SessionProvider } from '@/shared/session/SessionProvider'
import { server } from '@/test/server'

const API = 'http://localhost:3000/api'

function sessionAnonymous() {
  server.use(
    http.get(`${API}/auth/sessions/current`, () =>
      HttpResponse.json({ code: 'unauthenticated', message: 'x' }, { status: 401 }),
    ),
  )
}

function renderLogin() {
  sessionAnonymous()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter(
    [
      { path: '/login', element: <LoginPage /> },
      { path: '/archivos', element: <div>Pantalla de archivos</div> },
    ],
    { initialEntries: ['/login'] },
  )
  return render(
    <QueryClientProvider client={queryClient}>
      <SessionProvider>
        <RouterProvider router={router} />
      </SessionProvider>
    </QueryClientProvider>,
  )
}

async function fillCredentials(email: string, password: string) {
  const user = userEvent.setup()
  await user.type(await screen.findByLabelText('Correo'), email)
  await user.type(screen.getByLabelText('Contraseña'), password)
  await user.click(screen.getByRole('button', { name: 'Entrar' }))
}

describe('inicio de sesión', () => {
  it('con credenciales válidas, entra y navega', async () => {
    renderLogin()
    server.use(
      http.post(`${API}/auth/sessions`, () =>
        HttpResponse.json(
          { user: { id: 'u1', email: 'ana@demo.local', name: 'Ana', role: 'admin', active: true }, session: null },
          { status: 201 },
        ),
      ),
    )

    await fillCredentials('ana@demo.local', 'Correcta123')

    expect(await screen.findByText('Pantalla de archivos')).toBeInTheDocument()
  })

  it('con credenciales inválidas, muestra un mensaje claro', async () => {
    renderLogin()
    server.use(
      http.post(`${API}/auth/sessions`, () =>
        HttpResponse.json({ code: 'unauthenticated', message: 'x' }, { status: 401 }),
      ),
    )

    await fillCredentials('ana@demo.local', 'incorrecta')

    expect(await screen.findByText('Correo o contraseña incorrectos.')).toBeInTheDocument()
  })

  it('con demasiados intentos, lo indica', async () => {
    renderLogin()
    server.use(
      http.post(`${API}/auth/sessions`, () =>
        HttpResponse.json({ code: 'too_many_requests', message: 'x' }, { status: 429 }),
      ),
    )

    await fillCredentials('ana@demo.local', 'incorrecta')

    expect(
      await screen.findByText('Demasiados intentos. Vuelve a intentarlo más tarde.'),
    ).toBeInTheDocument()
  })

  it('valida en el cliente antes de enviar', async () => {
    renderLogin()
    const post = vi.fn()
    server.use(
      http.post(`${API}/auth/sessions`, () => {
        post()
        return HttpResponse.json({}, { status: 201 })
      }),
    )

    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Entrar' }))

    expect(await screen.findByText('Escribe tu correo.')).toBeInTheDocument()
    expect(post).not.toHaveBeenCalled()
  })
})

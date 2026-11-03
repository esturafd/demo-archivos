import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { Sidebar } from '@/app/layout/Sidebar'
import type { AppRole } from '@/shared/session/model'
import { SessionProvider } from '@/shared/session/SessionProvider'
import { server } from '@/test/server'

const API = 'http://localhost:3000/api'

function renderSidebarAs(role: AppRole) {
  server.use(
    http.get(`${API}/auth/sessions/current`, () =>
      HttpResponse.json({
        user: { id: 'u1', email: 'ana@demo.local', name: 'Ana Admin', role, active: true },
        session: null,
      }),
    ),
  )
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <SessionProvider>
        <MemoryRouter>
          <Sidebar />
        </MemoryRouter>
      </SessionProvider>
    </QueryClientProvider>,
  )
}

describe('barra lateral', () => {
  it('muestra la marca y todos los enlaces para el administrador', async () => {
    renderSidebarAs('admin')

    expect(await screen.findByTestId('brand-name')).toHaveTextContent('Archivos')
    for (const label of ['Archivos', 'Áreas', 'Áreas gestionadas', 'Usuarios', 'Historial']) {
      expect(screen.getByRole('link', { name: label })).toBeInTheDocument()
    }
    expect(screen.getByRole('button', { name: 'Cerrar sesión' })).toBeInTheDocument()
  })

  it('oculta las secciones reservadas para el estándar', async () => {
    renderSidebarAs('standard')

    expect(await screen.findByTestId('brand-name')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Archivos' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Áreas' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Áreas gestionadas' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Usuarios' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Historial' })).not.toBeInTheDocument()
  })
})

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it } from 'vitest'
import { FilesPage } from '@/files/ui/FilesPage'
import type { AppRole } from '@/shared/session/model'
import { SessionProvider } from '@/shared/session/SessionProvider'
import { server } from '@/test/server'

const API = 'http://localhost:3000/api'

const PDF = {
  id: 'f1',
  originalName: 'informe.pdf',
  mimeType: 'application/pdf',
  sizeBytes: 20480,
  ownerEmail: 'ana@demo.local',
  areaId: null,
  uploadedAt: '2026-01-31T10:15:30Z',
}
const PNG = {
  id: 'f2',
  originalName: 'fachada.png',
  mimeType: 'image/png',
  sizeBytes: 491520,
  ownerEmail: 'ana@demo.local',
  areaId: null,
  uploadedAt: '2026-01-30T09:00:00Z',
}

const OWNERS = [
  { id: 'u1', email: 'ana@demo.local', name: 'Ana' },
  { id: 'u2', email: 'maria@demo.local', name: 'María' },
]

beforeEach(() => {
  server.use(
    http.get(`${API}/files/owners`, () => HttpResponse.json(OWNERS)),
    http.get(`${API}/files`, () => HttpResponse.json({ items: [PDF, PNG], nextCursor: null })),
  )
})

function renderFiles(role: AppRole = 'standard') {
  server.use(
    http.get(`${API}/auth/sessions/current`, () =>
      HttpResponse.json({
        user: { id: 'u1', email: 'ana@demo.local', name: 'Ana', role, active: true },
        session: null,
      }),
    ),
  )
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <SessionProvider>
        <MemoryRouter initialEntries={['/archivos']}>
          <FilesPage />
        </MemoryRouter>
      </SessionProvider>
    </QueryClientProvider>,
  )
}

function fileInput(): HTMLInputElement {
  const input = document.querySelector('input[type="file"]')
  if (!input) {
    throw new Error('No se encontró el campo de archivo')
  }
  return input as HTMLInputElement
}

describe('página de archivos', () => {
  it('el estándar no ve el selector y lista sus archivos', async () => {
    renderFiles('standard')

    expect(await screen.findByText('informe.pdf')).toBeInTheDocument()
    expect(screen.getByText('fachada.png')).toBeInTheDocument()
    expect(
      screen.queryByLabelText('Elegir el usuario cuyos archivos se muestran'),
    ).not.toBeInTheDocument()
  })

  it('quien ve archivos de otros tiene el selector, con uno mismo por defecto', async () => {
    renderFiles('admin')

    const select = await screen.findByLabelText('Elegir el usuario cuyos archivos se muestran')
    expect(screen.getByRole('option', { name: 'Ana (yo)' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'María' })).toBeInTheDocument()
    expect(select).toHaveValue('u1')
  })

  it('al elegir otro propietario, pide sus archivos', async () => {
    const requested: (string | null)[] = []
    server.use(
      http.get(`${API}/files`, ({ request }) => {
        requested.push(new URL(request.url).searchParams.get('ownerId'))
        return HttpResponse.json({ items: [], nextCursor: null })
      }),
    )
    renderFiles('manager')

    const user = userEvent.setup()
    const select = await screen.findByLabelText('Elegir el usuario cuyos archivos se muestran')
    await user.selectOptions(select, 'u2')

    await waitFor(() => expect(requested).toContain('u2'))
  })

  it('la búsqueda viaja al servidor', async () => {
    const searched: (string | null)[] = []
    server.use(
      http.get(`${API}/files`, ({ request }) => {
        searched.push(new URL(request.url).searchParams.get('search'))
        return HttpResponse.json({ items: [], nextCursor: null })
      }),
    )
    renderFiles('standard')

    const user = userEvent.setup()
    await user.type(await screen.findByLabelText('Buscar archivos por nombre'), 'informe')

    await waitFor(() => expect(searched).toContain('informe'))
  })

  it('la paginación avanza y retrocede con el cursor', async () => {
    server.use(
      http.get(`${API}/files`, ({ request }) => {
        const cursor = new URL(request.url).searchParams.get('cursor')
        return cursor
          ? HttpResponse.json({ items: [PNG], nextCursor: null })
          : HttpResponse.json({ items: [PDF], nextCursor: 'CURSOR_1' })
      }),
    )
    renderFiles('standard')

    const user = userEvent.setup()
    await screen.findByText('informe.pdf')
    await user.click(screen.getByRole('button', { name: 'Siguiente' }))
    expect(await screen.findByText('fachada.png')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Anterior' }))
    expect(await screen.findByText('informe.pdf')).toBeInTheDocument()
  })

  it('eliminar pide confirmación y borra el archivo', async () => {
    const removed: string[] = []
    server.use(
      http.delete(`${API}/files/f1`, () => {
        removed.push('f1')
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderFiles('standard')

    const user = userEvent.setup()
    await screen.findByText('informe.pdf')
    await user.click(screen.getByRole('button', { name: 'Eliminar informe.pdf' }))

    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText('informe.pdf')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Eliminar' }))

    await waitFor(() => expect(removed).toContain('f1'))
  })

  it('valida el tipo antes de enviar', async () => {
    const uploaded = { count: 0 }
    server.use(
      http.post(`${API}/files`, () => {
        uploaded.count += 1
        return HttpResponse.json(PDF, { status: 201 })
      }),
    )
    renderFiles('standard')

    const user = userEvent.setup()
    await screen.findByText('informe.pdf')
    await user.click(screen.getByRole('button', { name: 'Cargar archivo' }))

    const bad = new File(['x'], 'virus.exe', { type: 'application/x-msdownload' })
    fireEvent.change(fileInput(), { target: { files: [bad] } })
    await user.click(screen.getByRole('button', { name: 'Cargar' }))

    expect(await screen.findByText('Ese tipo de archivo no está permitido.')).toBeInTheDocument()
    expect(uploaded.count).toBe(0)
  })

  it('carga un archivo válido y cierra el modal', async () => {
    const uploaded = { count: 0 }
    server.use(
      http.post(`${API}/files`, () => {
        uploaded.count += 1
        return HttpResponse.json(PDF, { status: 201 })
      }),
    )
    renderFiles('standard')

    const user = userEvent.setup()
    await screen.findByText('informe.pdf')
    await user.click(screen.getByRole('button', { name: 'Cargar archivo' }))

    const file = new File(['%PDF-1.7 contenido'], 'nuevo.pdf', { type: 'application/pdf' })
    fireEvent.change(fileInput(), { target: { files: [file] } })
    expect(await screen.findByText('nuevo.pdf')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Cargar' }))

    await waitFor(() => expect(uploaded.count).toBe(1))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })
})

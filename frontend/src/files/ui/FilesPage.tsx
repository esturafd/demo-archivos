import '@/files/ui/files.css'
import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { useFileOwners } from '@/files/application/useFileOwners'
import { useFiles } from '@/files/application/useFiles'
import type { FileItem } from '@/files/domain/file'
import { DeleteFileDialog } from '@/files/ui/DeleteFileDialog'
import { FileTable } from '@/files/ui/FileTable'
import { FilesToolbar } from '@/files/ui/FilesToolbar'
import { UploadFileDialog } from '@/files/ui/UploadFileDialog'
import { filesAdapter } from '@/files/infrastructure/FilesHttpAdapter'
import { useUpdateSearchParams } from '@/shared/routing/useUpdateSearchParams'
import { hasAtLeastRole } from '@/shared/session/roles'
import { useSession } from '@/shared/session/sessionContext'
import { EmptyState } from '@/shared/ui/EmptyState'
import { ErrorState } from '@/shared/ui/ErrorState'
import { PageLoading } from '@/shared/ui/PageLoading'

/** Página de archivos: listado, búsqueda, carga y eliminación. */
export function FilesPage() {
  const { user } = useSession()
  const [searchParams] = useSearchParams()

  // El cursor no es reutilizable hacia atrás, así que se guarda la pila de la
  // sesión de navegación; la URL conserva la página actual, que es lo que se comparte.
  const [cursorStack, setCursorStack] = useState<(string | undefined)[]>([])
  const [uploadOpen, setUploadOpen] = useState(false)
  const [toDelete, setToDelete] = useState<FileItem | null>(null)

  const canSeeOthers = user ? hasAtLeastRole(user.role, 'area_head') : false

  const search = searchParams.get('q') ?? ''
  const ownerParam = searchParams.get('owner') ?? undefined
  const cursor = searchParams.get('cursor') ?? undefined
  const ownerId = ownerParam ?? user?.id

  const files = useFiles({
    search: search || undefined,
    ownerId,
    cursor,
  })
  const owners = useFileOwners(canSeeOthers)

  const updateParams = useUpdateSearchParams()

  if (!user) {
    return null
  }

  function changeSearch(value: string) {
    setCursorStack([])
    updateParams({ q: value || null, cursor: null })
  }

  function changeOwner(id: string) {
    setCursorStack([])
    updateParams({ owner: id, cursor: null })
  }

  function goNext() {
    const next = files.data?.nextCursor
    if (!next) {
      return
    }
    setCursorStack((stack) => [...stack, cursor])
    updateParams({ cursor: next })
  }

  function goPrev() {
    if (cursorStack.length === 0) {
      return
    }
    const stack = [...cursorStack]
    const previous = stack.pop()
    setCursorStack(stack)
    updateParams({ cursor: previous ?? null })
  }

  const items = files.data?.items ?? []
  const hasFilters = search !== ''

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1>Archivos</h1>
          <p>Los archivos que puedes ver según tu rol y tu área.</p>
        </div>
        <button type="button" className="btn btn--primary" onClick={() => setUploadOpen(true)}>
          Cargar archivo
        </button>
      </header>

      <FilesToolbar
        canSeeOthers={canSeeOthers}
        owners={owners.data ?? []}
        ownerId={ownerId ?? ''}
        selfId={user.id}
        selfName={user.name}
        onOwnerChange={changeOwner}
        search={search}
        onSearchChange={changeSearch}
      />

      {files.isPending ? (
        <PageLoading />
      ) : files.isError ? (
        <ErrorState error={files.error} />
      ) : items.length === 0 ? (
        <EmptyState title={hasFilters ? 'Sin resultados' : 'No hay archivos'}>
          {hasFilters
            ? 'No se encontraron archivos con esos criterios.'
            : 'Carga un archivo para empezar.'}
        </EmptyState>
      ) : (
        <div className="card">
          <FileTable items={items} contentUrl={filesAdapter.contentUrl} onDelete={setToDelete} />
          <div className="pagination">
            <span>
              {items.length} {items.length === 1 ? 'archivo' : 'archivos'}
            </span>
            <span className="pagination__actions">
              <button
                type="button"
                className="btn"
                onClick={goPrev}
                disabled={cursorStack.length === 0}
              >
                Anterior
              </button>
              <button
                type="button"
                className="btn"
                onClick={goNext}
                disabled={!files.data?.nextCursor}
              >
                Siguiente
              </button>
            </span>
          </div>
        </div>
      )}

      <UploadFileDialog open={uploadOpen} onClose={() => setUploadOpen(false)} />
      <DeleteFileDialog file={toDelete} onClose={() => setToDelete(null)} />
    </div>
  )
}

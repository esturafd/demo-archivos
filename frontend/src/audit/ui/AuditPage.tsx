import '@/audit/ui/audit.css'
import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router'
import { useAuditEvents } from '@/audit/application/useAuditEvents'
import { AuditTable } from '@/audit/ui/AuditTable'
import { AuditToolbar, type ActorOption } from '@/audit/ui/AuditToolbar'
import { useFileOwners } from '@/files/application/useFileOwners'
import { useUpdateSearchParams } from '@/shared/routing/useUpdateSearchParams'
import { useSession } from '@/shared/session/sessionContext'
import { EmptyState } from '@/shared/ui/EmptyState'
import { ErrorState } from '@/shared/ui/ErrorState'
import { PageLoading } from '@/shared/ui/PageLoading'
import { useUsers } from '@/users/application/useUsers'

/** Página de historial (gerente o superior): eventos con filtros y cursor. */
export function AuditPage() {
  const { user } = useSession()
  const [searchParams] = useSearchParams()
  const updateParams = useUpdateSearchParams()
  const [cursorStack, setCursorStack] = useState<(string | undefined)[]>([])

  const isAdmin = user?.role === 'admin'
  // El administrador filtra por cualquier usuario; el gerente, por los de sus áreas.
  // Hasta que no se resuelve la sesión no se pide nada (evita una petición de propietarios
  // antes de saber el rol).
  const ownersEnabled = user !== null && !isAdmin
  const users = useUsers({}, isAdmin)
  const owners = useFileOwners(ownersEnabled)

  const actors: ActorOption[] = useMemo(() => {
    if (isAdmin) {
      return (users.data ?? []).map((item) => ({ id: item.id, label: item.name }))
    }
    return (owners.data ?? []).map((item) => ({ id: item.id, label: item.name ?? item.email }))
  }, [isAdmin, users.data, owners.data])

  const action = searchParams.get('action') ?? ''
  const actorId = searchParams.get('actor') ?? ''
  const cursor = searchParams.get('cursor') ?? undefined

  const events = useAuditEvents({
    action: action || undefined,
    actorId: actorId || undefined,
    cursor,
  })

  if (!user) {
    return null
  }

  function changeFilter(next: Record<string, string | null>) {
    setCursorStack([])
    updateParams({ ...next, cursor: null })
  }

  function goNext() {
    const next = events.data?.nextCursor
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

  const items = events.data?.items ?? []
  const hasFilters = action !== '' || actorId !== ''

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1>Historial</h1>
          <p>Las acciones registradas según tu alcance.</p>
        </div>
      </header>

      <AuditToolbar
        action={action}
        onActionChange={(value) => changeFilter({ action: value || null })}
        actors={actors}
        actorId={actorId}
        onActorChange={(value) => changeFilter({ actor: value || null })}
      />

      {events.isPending ? (
        <PageLoading />
      ) : events.isError ? (
        <ErrorState error={events.error} />
      ) : items.length === 0 ? (
        <EmptyState title={hasFilters ? 'Sin resultados' : 'No hay eventos'}>
          {hasFilters
            ? 'No se encontraron eventos con esos criterios.'
            : 'Todavía no hay actividad registrada.'}
        </EmptyState>
      ) : (
        <div className="card">
          <AuditTable events={items} />
          <div className="pagination">
            <span>
              {items.length} {items.length === 1 ? 'evento' : 'eventos'}
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
                disabled={!events.data?.nextCursor}
              >
                Siguiente
              </button>
            </span>
          </div>
        </div>
      )}
    </div>
  )
}

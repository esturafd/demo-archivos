import '@/users/ui/users.css'
import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { useAreas } from '@/areas/application/useAreas'
import { useUsers } from '@/users/application/useUsers'
import type { UserDetail } from '@/users/domain/user'
import { CreateUserDialog } from '@/users/ui/CreateUserDialog'
import { EditUserDialog } from '@/users/ui/EditUserDialog'
import { ToggleUserDialog } from '@/users/ui/ToggleUserDialog'
import { UsersTable } from '@/users/ui/UsersTable'
import { UsersToolbar } from '@/users/ui/UsersToolbar'
import { useUpdateSearchParams } from '@/shared/routing/useUpdateSearchParams'
import { useSession } from '@/shared/session/sessionContext'
import { EmptyState } from '@/shared/ui/EmptyState'
import { ErrorState } from '@/shared/ui/ErrorState'
import { PageLoading } from '@/shared/ui/PageLoading'

/** Página de usuarios (administrador): listado, alta, edición y estado. */
export function UsersPage() {
  const { user: current } = useSession()
  const [searchParams] = useSearchParams()
  const updateParams = useUpdateSearchParams()

  const [createOpen, setCreateOpen] = useState(false)
  const [editing, setEditing] = useState<UserDetail | null>(null)
  const [toggling, setToggling] = useState<UserDetail | null>(null)

  const search = searchParams.get('q') ?? ''
  const areaId = searchParams.get('area') ?? ''

  const users = useUsers({ search: search || undefined, areaId: areaId || undefined })
  const areas = useAreas()

  if (!current) {
    return null
  }

  const items = users.data ?? []
  const hasFilters = search !== '' || areaId !== ''

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1>Usuarios</h1>
          <p>Administra las cuentas, sus roles y sus áreas.</p>
        </div>
        <button type="button" className="btn btn--primary" onClick={() => setCreateOpen(true)}>
          Nuevo usuario
        </button>
      </header>

      <UsersToolbar
        areas={areas.data ?? []}
        areaId={areaId}
        onAreaChange={(id) => updateParams({ area: id || null })}
        search={search}
        onSearchChange={(value) => updateParams({ q: value || null })}
      />

      {users.isPending ? (
        <PageLoading />
      ) : users.isError ? (
        <ErrorState error={users.error} />
      ) : items.length === 0 ? (
        <EmptyState title={hasFilters ? 'Sin resultados' : 'No hay usuarios'}>
          {hasFilters ? 'No se encontraron usuarios con esos criterios.' : undefined}
        </EmptyState>
      ) : (
        <div className="card">
          <UsersTable
            users={items}
            currentUserId={current.id}
            onEdit={setEditing}
            onToggleActive={setToggling}
          />
        </div>
      )}

      <CreateUserDialog open={createOpen} areas={areas.data ?? []} onClose={() => setCreateOpen(false)} />
      <EditUserDialog
        user={editing}
        areas={areas.data ?? []}
        isSelf={editing?.id === current.id}
        onClose={() => setEditing(null)}
      />
      <ToggleUserDialog user={toggling} onClose={() => setToggling(null)} />
    </div>
  )
}

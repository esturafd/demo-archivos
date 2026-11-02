import '@/areas/ui/areas.css'
import { useState } from 'react'
import { useAreas } from '@/areas/application/useAreas'
import type { Area } from '@/areas/domain/area'
import { AreaFormDialog } from '@/areas/ui/AreaFormDialog'
import { AreasTable } from '@/areas/ui/AreasTable'
import { ToggleAreaDialog } from '@/areas/ui/ToggleAreaDialog'
import { useSession } from '@/shared/session/sessionContext'
import { EmptyState } from '@/shared/ui/EmptyState'
import { ErrorState } from '@/shared/ui/ErrorState'
import { PageLoading } from '@/shared/ui/PageLoading'

/** Página de áreas: consulta para cualquiera y administración para el administrador. */
export function AreasPage() {
  const { user } = useSession()
  const areas = useAreas()
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<Area | null>(null)
  const [toggling, setToggling] = useState<Area | null>(null)

  const canManage = user?.role === 'admin'
  const items = areas.data ?? []

  function openCreate() {
    setEditing(null)
    setFormOpen(true)
  }

  function openEdit(area: Area) {
    setEditing(area)
    setFormOpen(true)
  }

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1>Áreas</h1>
          <p>
            {canManage
              ? 'Gestiona las áreas y consulta sus usuarios.'
              : 'Las áreas de la organización.'}
          </p>
        </div>
        {canManage ? (
          <button type="button" className="btn btn--primary" onClick={openCreate}>
            Nueva área
          </button>
        ) : null}
      </header>

      {areas.isPending ? (
        <PageLoading />
      ) : areas.isError ? (
        <ErrorState error={areas.error} />
      ) : items.length === 0 ? (
        <EmptyState title="No hay áreas">
          {canManage ? 'Crea la primera área.' : 'Todavía no hay áreas disponibles.'}
        </EmptyState>
      ) : (
        <div className="card">
          <AreasTable
            areas={items}
            canManage={canManage}
            onEdit={openEdit}
            onToggleActive={setToggling}
          />
        </div>
      )}

      {canManage ? (
        <>
          <AreaFormDialog open={formOpen} area={editing} onClose={() => setFormOpen(false)} />
          <ToggleAreaDialog area={toggling} onClose={() => setToggling(null)} />
        </>
      ) : null}
    </div>
  )
}

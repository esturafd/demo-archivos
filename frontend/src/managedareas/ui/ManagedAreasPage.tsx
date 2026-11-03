import '@/managedareas/ui/managedareas.css'
import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { useManagedAreas } from '@/managedareas/application/useManagedAreas'
import { ManagedAreasDialog } from '@/managedareas/ui/ManagedAreasDialog'
import { useUpdateSearchParams } from '@/shared/routing/useUpdateSearchParams'
import { useSession } from '@/shared/session/sessionContext'
import { EmptyState } from '@/shared/ui/EmptyState'
import { ErrorState } from '@/shared/ui/ErrorState'
import { PageLoading } from '@/shared/ui/PageLoading'
import { useUsers } from '@/users/application/useUsers'

/** Página de áreas gestionadas: consulta (gerente o superior) y reemplazo (administrador). */
export function ManagedAreasPage() {
  const { user } = useSession()
  const [searchParams] = useSearchParams()
  const updateParams = useUpdateSearchParams()
  const [dialogOpen, setDialogOpen] = useState(false)

  const isAdmin = user?.role === 'admin'
  // La lista de usuarios es solo para el administrador (es quien elige el gerente).
  const managers = useUsers({}, isAdmin)
  const managerList = (managers.data ?? []).filter((item) => item.role === 'manager')

  const managerParam = searchParams.get('manager') ?? ''
  const managerId = isAdmin ? managerParam || managerList[0]?.id : user?.id
  const managerName = isAdmin
    ? (managerList.find((item) => item.id === managerId)?.name ?? 'el gerente')
    : (user?.name ?? '')

  const managed = useManagedAreas(managerId)

  if (!user) {
    return null
  }

  const areas = managed.data ?? []

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1>Áreas gestionadas</h1>
          <p>{isAdmin ? 'Áreas que gestiona cada gerente.' : 'Las áreas que gestionas.'}</p>
        </div>
        {isAdmin && managerId ? (
          <button type="button" className="btn btn--primary" onClick={() => setDialogOpen(true)}>
            Editar áreas
          </button>
        ) : null}
      </header>

      {isAdmin && managerList.length > 0 ? (
        <div className="toolbar">
          <label className="select">
            <span className="lbl">Gerente</span>
            <select
              aria-label="Elegir el gerente"
              value={managerId ?? ''}
              onChange={(event) => updateParams({ manager: event.target.value })}
            >
              {managerList.map((manager) => (
                <option key={manager.id} value={manager.id}>
                  {manager.name}
                </option>
              ))}
            </select>
          </label>
        </div>
      ) : null}

      {isAdmin && managers.isPending ? (
        <PageLoading />
      ) : isAdmin && managerList.length === 0 ? (
        <EmptyState title="No hay gerentes">
          Crea un usuario con rol gerente para asignarle áreas.
        </EmptyState>
      ) : managed.isPending ? (
        <PageLoading />
      ) : managed.isError ? (
        <ErrorState error={managed.error} />
      ) : areas.length === 0 ? (
        <EmptyState title="Sin áreas gestionadas">
          {isAdmin ? 'Asigna al menos un área a este gerente.' : 'Aún no tienes áreas asignadas.'}
        </EmptyState>
      ) : (
        <div className="card">
          <div className="chips">
            {areas.map((area) => (
              <span key={area.id} className={area.active ? 'chip' : 'chip chip--off'}>
                {area.name}
                {area.active ? '' : ' (inactiva)'}
              </span>
            ))}
          </div>
        </div>
      )}

      {isAdmin && managerId ? (
        <ManagedAreasDialog
          open={dialogOpen}
          managerId={managerId}
          managerName={managerName}
          currentIds={areas.map((area) => area.id)}
          onClose={() => setDialogOpen(false)}
        />
      ) : null}
    </div>
  )
}

import { useEffect, useState } from 'react'
import { useAreas } from '@/areas/application/useAreas'
import { useReplaceManagedAreas } from '@/managedareas/application/useReplaceManagedAreas'
import { ApiError } from '@/shared/error/ApiError'
import { Modal } from '@/shared/ui/Modal'

function replaceErrorMessage(error: unknown): string | null {
  if (!(error instanceof ApiError)) {
    return null
  }
  switch (error.status) {
    case 400:
      return 'Elige al menos un área.'
    case 403:
      return 'Solo el administrador puede cambiar las áreas gestionadas.'
    case 404:
      return 'El gerente ya no existe.'
    default:
      return error.message
  }
}

interface ManagedAreasDialogProps {
  open: boolean
  managerId: string
  managerName: string
  /** Áreas que gestiona ahora mismo. */
  currentIds: string[]
  onClose: () => void
}

/** Modal para reemplazar el conjunto de áreas gestionadas de un gerente. */
export function ManagedAreasDialog({
  open,
  managerId,
  managerName,
  currentIds,
  onClose,
}: ManagedAreasDialogProps) {
  const areas = useAreas()
  const replace = useReplaceManagedAreas()
  const [selected, setSelected] = useState<string[]>(currentIds)
  const [errorText, setErrorText] = useState<string | null>(null)

  useEffect(() => {
    if (open) {
      setSelected(currentIds)
      setErrorText(null)
      replace.reset()
    }
    // Se reinicia al abrir (o al cambiar de gerente).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, managerId])

  function toggle(areaId: string) {
    setSelected((current) =>
      current.includes(areaId) ? current.filter((id) => id !== areaId) : [...current, areaId],
    )
  }

  function submit(event: React.FormEvent) {
    event.preventDefault()
    if (selected.length === 0) {
      setErrorText('Elige al menos un área.')
      return
    }
    setErrorText(null)
    replace.mutate({ managerId, areaIds: selected }, { onSuccess: () => onClose() })
  }

  const message = errorText ?? replaceErrorMessage(replace.error)

  return (
    <Modal
      open={open}
      title="Áreas gestionadas"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose} disabled={replace.isPending}>
            Cancelar
          </button>
          <button
            type="submit"
            form="managed-areas-form"
            className="btn btn--primary"
            disabled={replace.isPending}
          >
            {replace.isPending ? 'Guardando…' : 'Guardar'}
          </button>
        </>
      }
    >
      <form id="managed-areas-form" className="stack" onSubmit={submit} noValidate>
        <p className="muted">
          Elige las áreas que gestiona <strong>{managerName}</strong>. Se reemplaza el conjunto
          completo y no puede quedar vacío.
        </p>

        {areas.isPending ? <p>Cargando…</p> : null}
        {areas.data ? (
          <div className="checklist">
            {areas.data.map((area) => (
              <label key={area.id} className="check">
                <input
                  type="checkbox"
                  checked={selected.includes(area.id)}
                  onChange={() => toggle(area.id)}
                />
                {area.name}
                {area.active ? '' : ' (inactiva)'}
              </label>
            ))}
          </div>
        ) : null}

        {message ? (
          <p className="field-error" role="alert">
            {message}
          </p>
        ) : null}
      </form>
    </Modal>
  )
}

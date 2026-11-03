import { useEffect } from 'react'
import { useUpdateArea } from '@/areas/application/useUpdateArea'
import type { Area } from '@/areas/domain/area'
import { ApiError } from '@/shared/error/ApiError'
import { Modal } from '@/shared/ui/Modal'

function toggleErrorMessage(error: unknown): string | null {
  if (!(error instanceof ApiError)) {
    return null
  }
  switch (error.status) {
    case 400:
      return 'No se pudo cambiar el estado del área.'
    case 403:
      return 'Solo el administrador puede gestionar las áreas.'
    case 404:
      return 'El área ya no existe.'
    default:
      return error.message
  }
}

/** Confirmación para activar o desactivar un área. */
export function ToggleAreaDialog({ area, onClose }: { area: Area | null; onClose: () => void }) {
  const update = useUpdateArea()
  const activating = area ? !area.active : false

  useEffect(() => {
    if (area) {
      update.reset()
    }
    // Se reinicia al cambiar de área.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [area])

  const message = toggleErrorMessage(update.error)

  return (
    <Modal
      open={area !== null}
      title={activating ? 'Activar área' : 'Desactivar área'}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose} disabled={update.isPending}>
            Cancelar
          </button>
          <button
            type="button"
            className={activating ? 'btn btn--primary' : 'btn btn--danger'}
            onClick={() => {
              if (!area) {
                return
              }
              update.mutate(
                {
                  id: area.id,
                  input: {
                    name: area.name,
                    description: area.description ?? null,
                    active: !area.active,
                  },
                },
                { onSuccess: () => onClose() },
              )
            }}
            disabled={update.isPending}
          >
            {activating ? 'Activar' : 'Desactivar'}
          </button>
        </>
      }
    >
      {activating ? (
        <p>¿Seguro que quieres activar {area?.name}? Volverá a estar disponible.</p>
      ) : (
        <p>
          ¿Seguro que quieres desactivar <span className="danger-text">{area?.name}</span>? El área
          se conserva (con sus archivos e historial) pero deja de estar disponible.
        </p>
      )}
      {message ? (
        <p className="field-error" role="alert">
          {message}
        </p>
      ) : null}
    </Modal>
  )
}

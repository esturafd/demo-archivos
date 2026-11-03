import { useEffect } from 'react'
import { useUpdateUser } from '@/users/application/useUpdateUser'
import { requiresArea, type UserDetail } from '@/users/domain/user'
import { ApiError } from '@/shared/error/ApiError'
import { Modal } from '@/shared/ui/Modal'

function toggleErrorMessage(error: unknown): string | null {
  if (!(error instanceof ApiError)) {
    return null
  }
  switch (error.status) {
    case 400:
      return 'No se pudo cambiar el estado del usuario.'
    case 404:
      return 'El usuario ya no existe.'
    default:
      return error.message
  }
}

/** Confirmación para activar o desactivar un usuario. */
export function ToggleUserDialog({
  user,
  onClose,
}: {
  user: UserDetail | null
  onClose: () => void
}) {
  const update = useUpdateUser()
  const activating = user ? !user.active : false

  useEffect(() => {
    if (user) {
      update.reset()
    }
    // Se reinicia al cambiar de usuario.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user])

  const message = toggleErrorMessage(update.error)

  return (
    <Modal
      open={user !== null}
      title={activating ? 'Activar usuario' : 'Desactivar usuario'}
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
              if (!user) {
                return
              }
              update.mutate(
                {
                  id: user.id,
                  input: {
                    name: user.name,
                    role: user.role,
                    areaId: requiresArea(user.role) ? (user.areaId ?? null) : null,
                    active: !user.active,
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
        <p>¿Seguro que quieres activar a {user?.name}? Podrá volver a iniciar sesión.</p>
      ) : (
        <p>
          ¿Seguro que quieres desactivar a <span className="danger-text">{user?.name}</span>? Se
          revocarán sus sesiones abiertas.
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

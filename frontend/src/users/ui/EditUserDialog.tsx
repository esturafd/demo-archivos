import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import type { Area } from '@/areas/domain/area'
import { useUpdateUser } from '@/users/application/useUpdateUser'
import { requiresArea, type UserDetail } from '@/users/domain/user'
import { updateUserSchema, type UpdateUserValues } from '@/users/domain/userForm'
import { ApiError } from '@/shared/error/ApiError'
import { Modal } from '@/shared/ui/Modal'
import { RoleAreaFields } from '@/users/ui/RoleAreaFields'

function updateErrorMessage(error: unknown): string | null {
  if (!(error instanceof ApiError)) {
    return null
  }
  switch (error.status) {
    case 400:
      return 'No se pudo guardar el cambio. Revisa los datos.'
    case 404:
      return 'El usuario ya no existe.'
    default:
      return error.message
  }
}

/** Modal de edición de un usuario (nombre, rol, área y estado). */
export function EditUserDialog({
  user,
  areas,
  isSelf,
  onClose,
}: {
  user: UserDetail | null
  areas: Area[]
  isSelf: boolean
  onClose: () => void
}) {
  const update = useUpdateUser()
  const {
    register,
    handleSubmit,
    watch,
    reset,
    formState: { errors },
  } = useForm<UpdateUserValues>({
    resolver: zodResolver(updateUserSchema),
    defaultValues: { name: '', role: 'standard', areaId: '', active: true },
  })

  useEffect(() => {
    if (user) {
      update.reset()
      reset({
        name: user.name,
        role: user.role,
        areaId: user.areaId ?? '',
        active: user.active,
      })
    }
    // Se reinicia al cambiar de usuario.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user])

  const role = watch('role')
  const message = updateErrorMessage(update.error)

  const onSubmit = handleSubmit((values) => {
    if (!user) {
      return
    }
    update.mutate(
      {
        id: user.id,
        input: {
          name: values.name,
          role: values.role,
          areaId: requiresArea(values.role) ? (values.areaId ?? null) : null,
          active: values.active,
        },
      },
      { onSuccess: () => onClose() },
    )
  })

  return (
    <Modal
      open={user !== null}
      title="Editar usuario"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose} disabled={update.isPending}>
            Cancelar
          </button>
          <button
            type="submit"
            form="edit-user-form"
            className="btn btn--primary"
            disabled={update.isPending}
          >
            {update.isPending ? 'Guardando…' : 'Guardar'}
          </button>
        </>
      }
    >
      <form id="edit-user-form" className="stack" onSubmit={onSubmit} noValidate>
        <label>
          Nombre
          <input type="text" {...register('name')} />
          {errors.name ? <small className="field-error">{errors.name.message}</small> : null}
        </label>

        <RoleAreaFields
          role={role}
          areas={areas}
          roleField={register('role')}
          areaField={register('areaId')}
          roleError={errors.role?.message}
          areaError={errors.areaId?.message}
          roleDisabled={isSelf}
        />

        <label className="check">
          <input type="checkbox" {...register('active')} disabled={isSelf} />
          Cuenta activa
        </label>

        {isSelf ? (
          <p className="muted">No puedes cambiar tu propio rol ni desactivar tu cuenta.</p>
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

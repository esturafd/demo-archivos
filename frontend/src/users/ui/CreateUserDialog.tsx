import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import type { Area } from '@/areas/domain/area'
import { useCreateUser } from '@/users/application/useCreateUser'
import { requiresArea } from '@/users/domain/user'
import { createUserSchema, type CreateUserValues } from '@/users/domain/userForm'
import { ApiError } from '@/shared/error/ApiError'
import { Modal } from '@/shared/ui/Modal'
import { RoleAreaFields } from '@/users/ui/RoleAreaFields'

function createErrorMessage(error: unknown): string | null {
  if (!(error instanceof ApiError)) {
    return null
  }
  switch (error.status) {
    case 400:
      return 'Revisa los datos e inténtalo de nuevo.'
    case 409:
      return 'Ya existe un usuario con ese correo.'
    default:
      return error.message
  }
}

const EMPTY: CreateUserValues = {
  email: '',
  password: '',
  name: '',
  role: 'standard',
  areaId: '',
}

/** Modal de alta de un usuario. */
export function CreateUserDialog({
  open,
  areas,
  onClose,
}: {
  open: boolean
  areas: Area[]
  onClose: () => void
}) {
  const create = useCreateUser()
  const {
    register,
    handleSubmit,
    watch,
    reset,
    formState: { errors },
  } = useForm<CreateUserValues>({
    resolver: zodResolver(createUserSchema),
    defaultValues: EMPTY,
  })

  useEffect(() => {
    if (open) {
      reset(EMPTY)
      create.reset()
    }
    // Se reinicia solo al abrir.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  const role = watch('role')
  const message = createErrorMessage(create.error)

  const onSubmit = handleSubmit((values) => {
    create.mutate(
      {
        email: values.email,
        password: values.password,
        name: values.name,
        role: values.role,
        areaId: requiresArea(values.role) ? (values.areaId ?? null) : null,
      },
      { onSuccess: () => onClose() },
    )
  })

  return (
    <Modal
      open={open}
      title="Nuevo usuario"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose} disabled={create.isPending}>
            Cancelar
          </button>
          <button
            type="submit"
            form="create-user-form"
            className="btn btn--primary"
            disabled={create.isPending}
          >
            {create.isPending ? 'Creando…' : 'Crear'}
          </button>
        </>
      }
    >
      <form id="create-user-form" className="stack" onSubmit={onSubmit} noValidate>
        <label>
          Correo
          <input type="email" autoComplete="off" {...register('email')} />
          {errors.email ? <small className="field-error">{errors.email.message}</small> : null}
        </label>

        <label>
          Contraseña
          <input type="password" autoComplete="new-password" {...register('password')} />
          {errors.password ? (
            <small className="field-error">{errors.password.message}</small>
          ) : null}
        </label>

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
        />

        {message ? (
          <p className="field-error" role="alert">
            {message}
          </p>
        ) : null}
      </form>
    </Modal>
  )
}

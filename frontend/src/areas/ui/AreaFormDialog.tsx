import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { useCreateArea } from '@/areas/application/useCreateArea'
import { useUpdateArea } from '@/areas/application/useUpdateArea'
import type { Area } from '@/areas/domain/area'
import { areaFormSchema, type AreaFormValues } from '@/areas/domain/areaForm'
import { ApiError } from '@/shared/error/ApiError'
import { Modal } from '@/shared/ui/Modal'

function areaErrorMessage(error: unknown): string | null {
  if (!(error instanceof ApiError)) {
    return null
  }
  switch (error.status) {
    case 400:
      return 'Revisa los datos e inténtalo de nuevo.'
    case 403:
      return 'Solo el administrador puede gestionar las áreas.'
    case 404:
      return 'El área ya no existe.'
    case 409:
      return 'Ya existe un área con ese nombre.'
    default:
      return error.message
  }
}

/** Modal de alta y edición de un área. */
export function AreaFormDialog({
  open,
  area,
  onClose,
}: {
  open: boolean
  area: Area | null
  onClose: () => void
}) {
  const create = useCreateArea()
  const update = useUpdateArea()
  const isEdit = area !== null

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<AreaFormValues>({
    resolver: zodResolver(areaFormSchema),
    defaultValues: { name: '', description: '' },
  })

  useEffect(() => {
    if (open) {
      reset({ name: area?.name ?? '', description: area?.description ?? '' })
      create.reset()
      update.reset()
    }
    // Se reinicia al abrir o al cambiar de área.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, area])

  const pending = create.isPending || update.isPending
  const message = areaErrorMessage(create.error ?? update.error)

  const onSubmit = handleSubmit((values) => {
    const description = values.description?.trim() ? values.description.trim() : null
    if (area) {
      update.mutate(
        { id: area.id, input: { name: values.name, description, active: area.active } },
        { onSuccess: () => onClose() },
      )
    } else {
      create.mutate({ name: values.name, description }, { onSuccess: () => onClose() })
    }
  })

  return (
    <Modal
      open={open}
      title={isEdit ? 'Editar área' : 'Nueva área'}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose} disabled={pending}>
            Cancelar
          </button>
          <button
            type="submit"
            form="area-form"
            className="btn btn--primary"
            disabled={pending}
          >
            {pending ? 'Guardando…' : isEdit ? 'Guardar' : 'Crear'}
          </button>
        </>
      }
    >
      <form id="area-form" className="stack" onSubmit={onSubmit} noValidate>
        <label>
          Nombre
          <input type="text" {...register('name')} />
          {errors.name ? <small className="field-error">{errors.name.message}</small> : null}
        </label>

        <label>
          Descripción
          <textarea rows={3} {...register('description')} />
          {errors.description ? (
            <small className="field-error">{errors.description.message}</small>
          ) : null}
        </label>

        {message ? (
          <p className="field-error" role="alert">
            {message}
          </p>
        ) : null}
      </form>
    </Modal>
  )
}

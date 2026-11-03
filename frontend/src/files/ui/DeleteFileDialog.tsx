import { useEffect } from 'react'
import { useDeleteFile } from '@/files/application/useDeleteFile'
import type { FileItem } from '@/files/domain/file'
import { ApiError } from '@/shared/error/ApiError'
import { Modal } from '@/shared/ui/Modal'

function deleteErrorMessage(error: unknown): string | null {
  if (!(error instanceof ApiError)) {
    return null
  }
  switch (error.status) {
    case 403:
      return 'No tienes permiso para eliminar este archivo.'
    case 404:
      return 'El archivo ya no está disponible.'
    default:
      return error.message
  }
}

/** Confirmación de eliminación lógica de un archivo. */
export function DeleteFileDialog({ file, onClose }: { file: FileItem | null; onClose: () => void }) {
  const remove = useDeleteFile()

  useEffect(() => {
    if (file) {
      remove.reset()
    }
    // Se reinicia al cambiar de archivo.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [file])

  const message = deleteErrorMessage(remove.error)

  return (
    <Modal
      open={file !== null}
      title="Eliminar archivo"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose} disabled={remove.isPending}>
            Cancelar
          </button>
          <button
            type="button"
            className="btn btn--danger"
            onClick={() => {
              if (file) {
                remove.mutate(file.id, { onSuccess: () => onClose() })
              }
            }}
            disabled={remove.isPending}
          >
            {remove.isPending ? 'Eliminando…' : 'Eliminar'}
          </button>
        </>
      }
    >
      <p>
        ¿Seguro que quieres eliminar <span className="danger-text">{file?.originalName}</span>? Es
        una eliminación lógica: dejará de estar disponible.
      </p>
      {message ? (
        <p className="field-error" role="alert">
          {message}
        </p>
      ) : null}
    </Modal>
  )
}

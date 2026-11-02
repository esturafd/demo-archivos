import { useEffect, useRef, useState } from 'react'
import { useUploadFile } from '@/files/application/useUploadFile'
import { formatBytes } from '@/files/domain/format'
import { validateFile, type FileViolation } from '@/files/domain/filePolicy'
import { ApiError } from '@/shared/error/ApiError'
import { Modal } from '@/shared/ui/Modal'

const VIOLATION_MESSAGE: Record<FileViolation, string> = {
  empty: 'El archivo está vacío.',
  tooLarge: 'El archivo supera el límite de 20 MB.',
  typeNotAllowed: 'Ese tipo de archivo no está permitido.',
}

function serverErrorMessage(error: unknown): string | null {
  if (!(error instanceof ApiError)) {
    return null
  }
  switch (error.status) {
    case 400:
      return 'No se pudo leer el archivo. Inténtalo de nuevo.'
    case 413:
      return 'El archivo supera el límite de 20 MB.'
    case 415:
      return 'Ese tipo de archivo no está permitido.'
    case 422:
      return 'El contenido no coincide con el tipo declarado.'
    default:
      return error.message
  }
}

/**
 * Modal de carga.
 *
 * <p>Valida tipo y tamaño **antes** de enviar (el servidor vuelve a validar por
 * la firma real), muestra el progreso y traduce los errores `413/415/422`.
 */
export function UploadFileDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const upload = useUploadFile()
  const [file, setFile] = useState<File | null>(null)
  const [errorText, setErrorText] = useState<string | null>(null)
  const [progress, setProgress] = useState(0)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (open) {
      setFile(null)
      setErrorText(null)
      setProgress(0)
      upload.reset()
    }
    // `upload` es estable; se reinicia solo al abrir.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  function choose(selected: File | null) {
    setFile(selected)
    const violation = selected ? validateFile(selected) : null
    setErrorText(violation ? VIOLATION_MESSAGE[violation] : null)
  }

  function submit(event: React.FormEvent) {
    event.preventDefault()
    if (!file) {
      setErrorText('Elige un archivo.')
      return
    }
    const violation = validateFile(file)
    if (violation) {
      setErrorText(VIOLATION_MESSAGE[violation])
      return
    }
    setErrorText(null)
    upload.mutate(
      { file, onProgress: setProgress },
      { onSuccess: () => onClose() },
    )
  }

  const message = errorText ?? serverErrorMessage(upload.error)

  return (
    <Modal
      open={open}
      title="Cargar archivo"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose} disabled={upload.isPending}>
            Cancelar
          </button>
          <button
            type="submit"
            form="upload-file-form"
            className="btn btn--primary"
            disabled={upload.isPending}
          >
            {upload.isPending ? 'Cargando…' : 'Cargar'}
          </button>
        </>
      }
    >
      <form id="upload-file-form" onSubmit={submit} noValidate>
        <label
          className="drop"
          onDragOver={(event) => event.preventDefault()}
          onDrop={(event) => {
            event.preventDefault()
            choose(event.dataTransfer.files[0] ?? null)
          }}
        >
          {file ? (
            <span className="drop__file">
              <strong>{file.name}</strong>
              <span className="muted">{formatBytes(file.size)}</span>
            </span>
          ) : (
            <span>
              <strong>Arrastra un archivo aquí</strong> o haz clic para elegirlo.
            </span>
          )}
          <input
            ref={inputRef}
            type="file"
            hidden
            onChange={(event) => choose(event.target.files?.[0] ?? null)}
          />
        </label>

        <p className="hint">
          Máximo 20 MB. Tipos permitidos: PDF, PNG, JPEG, GIF, WebP y texto. El tipo real se
          comprueba por su contenido.
        </p>

        {upload.isPending ? <progress value={progress} max={100} /> : null}

        {message ? (
          <p className="field-error" role="alert">
            {message}
          </p>
        ) : null}
      </form>
    </Modal>
  )
}

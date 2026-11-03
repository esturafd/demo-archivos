import { formatBytes, formatDate } from '@/files/domain/format'
import type { FileItem } from '@/files/domain/file'
import { typeLabel } from '@/files/domain/filePolicy'
import { FileTypeIcon } from '@/files/ui/FileTypeIcon'

interface FileTableProps {
  items: FileItem[]
  /** URL del contenido de un archivo (descarga del mismo origen). */
  contentUrl: (id: string) => string
  onDelete: (file: FileItem) => void
}

/** Tabla de archivos. Cada fila ofrece descargar y eliminar. */
export function FileTable({ items, contentUrl, onDelete }: FileTableProps) {
  return (
    <table className="data-table">
      <thead>
        <tr>
          <th>Nombre</th>
          <th>Tipo</th>
          <th className="num">Tamaño</th>
          <th>Propietario</th>
          <th>Subido</th>
          <th>
            <span className="sr-only">Acciones</span>
          </th>
        </tr>
      </thead>
      <tbody>
        {items.map((file) => (
          <tr key={file.id}>
            <td>
              <span className="fname">
                <span className="fname__ico">
                  <FileTypeIcon mimeType={file.mimeType} />
                </span>
                {file.originalName}
              </span>
            </td>
            <td>
              <span className="badge">{typeLabel(file.mimeType)}</span>
            </td>
            <td className="num">{formatBytes(file.sizeBytes)}</td>
            <td className="muted">{file.ownerEmail}</td>
            <td className="muted">{formatDate(file.uploadedAt)}</td>
            <td>
              <span className="actions">
                <a
                  className="btn-icon"
                  href={contentUrl(file.id)}
                  download
                  title="Descargar"
                  aria-label={`Descargar ${file.originalName}`}
                >
                  <svg
                    width="17"
                    height="17"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    aria-hidden="true"
                  >
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                    <path d="M7 10l5 5 5-5" />
                    <path d="M12 15V3" />
                  </svg>
                </a>
                <button
                  type="button"
                  className="btn-icon btn-icon--danger"
                  title="Eliminar"
                  aria-label={`Eliminar ${file.originalName}`}
                  onClick={() => onDelete(file)}
                >
                  <svg
                    width="17"
                    height="17"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    aria-hidden="true"
                  >
                    <path d="M3 6h18" />
                    <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                    <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6" />
                  </svg>
                </button>
              </span>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

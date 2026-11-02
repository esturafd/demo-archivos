import type { FileItem, FileOwner, FilePage } from '@/files/domain/file'

/** Parámetros del listado de archivos. */
export interface ListFilesParams {
  /** Texto a buscar en el nombre. */
  search?: string
  /** Propietario por el que filtrar; el alcance lo acota el servidor. */
  ownerId?: string
  /** Cursor opaco de la página anterior. */
  cursor?: string
  /** Tamaño de página (por defecto 20, máximo 100). */
  limit?: number
}

/** Datos de una carga, con el progreso opcional. */
export interface UploadFileParams {
  file: File
  onProgress?: (percent: number) => void
}

/**
 * Puerto del módulo `files`: lo que la aplicación necesita de los archivos.
 *
 * <p>La implementación (HTTP) vive en `infrastructure`; los casos de uso usan
 * esta interfaz, no axios.
 */
export interface FilesPort {
  /** Lista y busca archivos visibles, con paginación por cursor. */
  list(params: ListFilesParams): Promise<FilePage>

  /** Propietarios cuyos archivos puede ver el actor (incluido él mismo). */
  owners(): Promise<FileOwner[]>

  /** Carga un archivo (multipart). */
  upload(params: UploadFileParams): Promise<FileItem>

  /** Elimina lógicamente un archivo. */
  remove(id: string): Promise<void>

  /** URL del contenido, para descargarlo como enlace del mismo origen. */
  contentUrl(id: string): string
}

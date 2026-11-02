import type { ListFilesParams } from '@/files/application/FilesPort'

/** Claves de caché del módulo `files`. */
export const filesKeys = {
  /** Raíz: invalida listados y propietarios de golpe. */
  all: ['files'] as const,
  list: (params: ListFilesParams) => ['files', 'list', params] as const,
  owners: () => ['files', 'owners'] as const,
}

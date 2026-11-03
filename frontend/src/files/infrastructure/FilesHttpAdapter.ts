import { z } from 'zod'
import type {
  FilesPort,
  ListFilesParams,
  UploadFileParams,
} from '@/files/application/FilesPort'
import {
  fileItemSchema,
  fileOwnerSchema,
  filePageSchema,
  type FileItem,
  type FileOwner,
  type FilePage,
} from '@/files/domain/file'
import { env } from '@/shared/config/env'
import { httpClient } from '@/shared/http/httpClient'

/** Omite los parámetros vacíos para no ensuciar la query. */
function queryParams(params: ListFilesParams): Record<string, string | number> {
  const result: Record<string, string | number> = {}
  if (params.search) {
    result.search = params.search
  }
  if (params.ownerId) {
    result.ownerId = params.ownerId
  }
  if (params.cursor) {
    result.cursor = params.cursor
  }
  if (params.limit) {
    result.limit = params.limit
  }
  return result
}

/** Adaptador de `files` sobre la API HTTP. */
export class FilesHttpAdapter implements FilesPort {
  async list(params: ListFilesParams): Promise<FilePage> {
    const { data } = await httpClient.get('/files', { params: queryParams(params) })
    return filePageSchema.parse(data)
  }

  async owners(): Promise<FileOwner[]> {
    const { data } = await httpClient.get('/files/owners')
    return z.array(fileOwnerSchema).parse(data)
  }

  async upload({ file, onProgress }: UploadFileParams): Promise<FileItem> {
    const body = new FormData()
    body.append('file', file)
    // No se fija `Content-Type`: axios añade el `boundary` del multipart.
    const { data } = await httpClient.post('/files', body, {
      onUploadProgress: (event) => {
        if (!onProgress || !event.total) {
          return
        }
        onProgress(Math.round((event.loaded / event.total) * 100))
      },
    })
    return fileItemSchema.parse(data)
  }

  async remove(id: string): Promise<void> {
    await httpClient.delete(`/files/${id}`)
  }

  contentUrl(id: string): string {
    return `${env.apiBaseUrl}/files/${id}/content`
  }
}

/** Instancia única del adaptador. */
export const filesAdapter: FilesPort = new FilesHttpAdapter()

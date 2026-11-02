import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { UploadFileParams } from '@/files/application/FilesPort'
import { filesKeys } from '@/files/application/filesKeys'
import { filesAdapter } from '@/files/infrastructure/FilesHttpAdapter'

/** Caso de uso: cargar un archivo y refrescar el listado. */
export function useUploadFile() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (params: UploadFileParams) => filesAdapter.upload(params),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: filesKeys.all }),
  })
}

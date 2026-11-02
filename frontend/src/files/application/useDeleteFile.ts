import { useMutation, useQueryClient } from '@tanstack/react-query'
import { filesKeys } from '@/files/application/filesKeys'
import { filesAdapter } from '@/files/infrastructure/FilesHttpAdapter'

/** Caso de uso: eliminar lógicamente un archivo y refrescar el listado. */
export function useDeleteFile() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => filesAdapter.remove(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: filesKeys.all }),
  })
}

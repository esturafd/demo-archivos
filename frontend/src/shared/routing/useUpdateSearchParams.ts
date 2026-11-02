import { useCallback } from 'react'
import { useSearchParams } from 'react-router'

/**
 * Actualiza los *search params* de la URL.
 *
 * <p>Las claves con valor `null` o cadena vacía se eliminan; el resto se fija.
 * Es el mecanismo con el que los listados guardan sus filtros en la URL.
 */
export function useUpdateSearchParams() {
  const [, setSearchParams] = useSearchParams()

  return useCallback(
    (next: Record<string, string | null>) => {
      setSearchParams(
        (previous) => {
          const params = new URLSearchParams(previous)
          for (const [key, value] of Object.entries(next)) {
            if (value === null || value === '') {
              params.delete(key)
            } else {
              params.set(key, value)
            }
          }
          return params
        },
        { replace: false },
      )
    },
    [setSearchParams],
  )
}

import { z } from 'zod'

/** Vista pública de un área gestionada por un gerente. */
export const managedAreaSchema = z.object({
  id: z.string(),
  name: z.string(),
  active: z.boolean(),
})
export type ManagedArea = z.infer<typeof managedAreaSchema>

/** Respuesta de `GET/PUT /api/users/{id}/managed-areas`. */
export const managedAreasResponseSchema = z.object({
  areas: z.array(managedAreaSchema),
})

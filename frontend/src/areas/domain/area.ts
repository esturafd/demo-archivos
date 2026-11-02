import { z } from 'zod'

/** Vista pública de un área (la que devuelve la API). */
export const areaSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string().nullable().optional(),
  active: z.boolean(),
  createdAt: z.string(),
  updatedAt: z.string(),
})
export type Area = z.infer<typeof areaSchema>

import { z } from 'zod'

/** Cuerpo de error uniforme de la API. */
export const apiErrorBodySchema = z.object({
  code: z.string(),
  message: z.string(),
  requestId: z.string().optional(),
})

export type ApiErrorBody = z.infer<typeof apiErrorBodySchema>

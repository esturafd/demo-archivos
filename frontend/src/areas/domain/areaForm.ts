import { z } from 'zod'

/** Campos de un área (alta y edición): nombre obligatorio y descripción opcional. */
export const areaFormSchema = z.object({
  name: z.string().min(1, 'Escribe el nombre.').max(120, 'Máximo 120 caracteres.'),
  description: z.string().max(500, 'Máximo 500 caracteres.').optional(),
})
export type AreaFormValues = z.infer<typeof areaFormSchema>

import { z } from 'zod'
import { requiresArea } from '@/users/domain/user'
import { APP_ROLES } from '@/shared/session/model'

const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/

/** Campos comunes al alta y a la edición. */
const baseFields = {
  name: z.string().min(1, 'Escribe el nombre.').max(120, 'Máximo 120 caracteres.'),
  role: z.enum(APP_ROLES),
}

/** Alta de un usuario. */
export const createUserSchema = z
  .object({
    ...baseFields,
    email: z.string().min(1, 'Escribe el correo.').regex(EMAIL, 'Correo no válido.'),
    password: z
      .string()
      .min(8, 'Al menos 8 caracteres.')
      .max(200, 'Máximo 200 caracteres.'),
    areaId: z.string().optional(),
  })
  .superRefine((values, ctx) => {
    if (requiresArea(values.role) && !values.areaId) {
      ctx.addIssue({ code: 'custom', path: ['areaId'], message: 'Elige un área para este rol.' })
    }
  })
export type CreateUserValues = z.infer<typeof createUserSchema>

/** Edición de un usuario. */
export const updateUserSchema = z
  .object({
    ...baseFields,
    areaId: z.string().optional(),
    active: z.boolean(),
  })
  .superRefine((values, ctx) => {
    if (requiresArea(values.role) && !values.areaId) {
      ctx.addIssue({ code: 'custom', path: ['areaId'], message: 'Elige un área para este rol.' })
    }
  })
export type UpdateUserValues = z.infer<typeof updateUserSchema>

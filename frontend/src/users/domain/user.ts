import { z } from 'zod'
import { APP_ROLES, type AppRole } from '@/shared/session/model'

/** Vista pública de un usuario (la que devuelve la API). */
export const userDetailSchema = z.object({
  id: z.string(),
  email: z.string(),
  name: z.string(),
  role: z.enum(APP_ROLES),
  areaId: z.string().nullable().optional(),
  areaName: z.string().nullable().optional(),
  active: z.boolean(),
  createdAt: z.string(),
})
export type UserDetail = z.infer<typeof userDetailSchema>

/** Los roles `standard` y `area_head` exigen un área propia. */
export function requiresArea(role: AppRole): boolean {
  return role === 'standard' || role === 'area_head'
}

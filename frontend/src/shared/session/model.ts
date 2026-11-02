import { z } from 'zod'

/** Roles de negocio, los mismos que en el backend. */
export const APP_ROLES = ['standard', 'area_head', 'manager', 'admin'] as const
export type AppRole = (typeof APP_ROLES)[number]

/** Vista pública de un usuario (la que devuelve la API). */
export const sessionUserSchema = z.object({
  id: z.string(),
  email: z.string(),
  name: z.string(),
  role: z.enum(APP_ROLES),
  areaId: z.string().nullable().optional(),
  areaName: z.string().nullable().optional(),
  areaActive: z.boolean().nullable().optional(),
  active: z.boolean(),
})
export type SessionUser = z.infer<typeof sessionUserSchema>

/** Respuesta de `GET /api/auth/sessions/current` (solo se usa el usuario). */
export const currentSessionSchema = z.object({
  user: sessionUserSchema,
})
export type CurrentSession = z.infer<typeof currentSessionSchema>

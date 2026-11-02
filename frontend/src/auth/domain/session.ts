import { z } from 'zod'

/** Vista pública de una sesión (la que devuelve la API). */
export const sessionViewSchema = z.object({
  id: z.string(),
  createdAt: z.string(),
  expiresAt: z.string(),
  lastSeenAt: z.string().nullable().optional(),
  ipAddress: z.string().nullable().optional(),
  userAgent: z.string().nullable().optional(),
  current: z.boolean(),
})

export type SessionSummary = z.infer<typeof sessionViewSchema>

/** Respuesta de `GET /api/auth/sessions`. */
export const sessionsResponseSchema = z.object({
  sessions: z.array(sessionViewSchema),
})

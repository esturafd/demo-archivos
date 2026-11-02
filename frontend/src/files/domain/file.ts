import { z } from 'zod'

/** Vista pública de un archivo (la que devuelve la API). Nunca incluye la clave interna. */
export const fileItemSchema = z.object({
  id: z.string(),
  originalName: z.string(),
  mimeType: z.string(),
  sizeBytes: z.number(),
  ownerEmail: z.string(),
  areaId: z.string().nullable().optional(),
  uploadedAt: z.string(),
})
export type FileItem = z.infer<typeof fileItemSchema>

/** Página del listado, paginada por cursor. */
export const filePageSchema = z.object({
  items: z.array(fileItemSchema),
  nextCursor: z.string().nullable(),
})
export type FilePage = z.infer<typeof filePageSchema>

/** Propietario visible, para el selector de usuario. */
export const fileOwnerSchema = z.object({
  id: z.string(),
  email: z.string(),
  name: z.string().nullable().optional(),
})
export type FileOwner = z.infer<typeof fileOwnerSchema>

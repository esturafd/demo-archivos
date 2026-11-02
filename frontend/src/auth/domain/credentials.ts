import { z } from 'zod'

const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/

/** Credenciales de inicio de sesión. */
export const credentialsSchema = z.object({
  email: z.string().min(1, 'Escribe tu correo.').regex(EMAIL, 'Correo no válido.'),
  password: z.string().min(1, 'Escribe tu contraseña.'),
})

export type Credentials = z.infer<typeof credentialsSchema>

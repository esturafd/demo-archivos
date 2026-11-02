import type { AppRole } from '@/shared/session/model'

const ROLE_LEVEL: Record<AppRole, number> = {
  standard: 0,
  area_head: 1,
  manager: 2,
  admin: 3,
}

/** Nivel del rol (mayor = más permisos). */
export function roleLevel(role: AppRole): number {
  return ROLE_LEVEL[role]
}

/** True si `role` alcanza al menos el nivel de `required`. */
export function hasAtLeastRole(role: AppRole, required: AppRole): boolean {
  return ROLE_LEVEL[role] >= ROLE_LEVEL[required]
}

/** Etiqueta legible de cada rol. */
export const ROLE_LABEL: Record<AppRole, string> = {
  standard: 'Estándar',
  area_head: 'Jefe de área',
  manager: 'Gerente',
  admin: 'Administrador',
}

/** Etiqueta de un rol por su valor crudo, con el valor como respaldo. */
export function roleLabel(role: string | null | undefined): string {
  if (!role) {
    return '—'
  }
  return ROLE_LABEL[role as AppRole] ?? role
}

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

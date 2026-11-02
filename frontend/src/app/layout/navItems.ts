import type { AppRole } from '@/shared/session/model'

export interface NavItem {
  to: string
  label: string
  /** Nivel de rol mínimo para ver el enlace; sin él, lo ve cualquiera. */
  minRole?: AppRole
}

/**
 * Enlaces de la barra lateral: un módulo por enlace (salvo `shared` y `auth`).
 */
export const NAV_ITEMS: NavItem[] = [
  { to: '/archivos', label: 'Archivos' },
  { to: '/areas', label: 'Áreas' },
  { to: '/areas-gestionadas', label: 'Áreas gestionadas', minRole: 'manager' },
  { to: '/usuarios', label: 'Usuarios', minRole: 'admin' },
  { to: '/historial', label: 'Historial', minRole: 'manager' },
]

import type { AppRole } from '@/shared/session/model'

/** Iconos disponibles para los enlaces de la barra lateral. */
export type NavIconName = 'files' | 'areas' | 'managed' | 'users' | 'history'

export interface NavItem {
  to: string
  label: string
  icon: NavIconName
  /** Nivel de rol mínimo para ver el enlace; sin él, lo ve cualquiera. */
  minRole?: AppRole
}

/**
 * Enlaces de la barra lateral: un módulo por enlace (salvo `shared` y `auth`).
 */
export const NAV_ITEMS: NavItem[] = [
  { to: '/archivos', label: 'Archivos', icon: 'files' },
  { to: '/areas', label: 'Áreas', icon: 'areas' },
  { to: '/areas-gestionadas', label: 'Áreas gestionadas', icon: 'managed', minRole: 'manager' },
  { to: '/usuarios', label: 'Usuarios', icon: 'users', minRole: 'admin' },
  { to: '/historial', label: 'Historial', icon: 'history', minRole: 'manager' },
]

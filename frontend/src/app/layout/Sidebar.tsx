import { useState } from 'react'
import { NavLink } from 'react-router'
import { NAV_ITEMS } from '@/app/layout/navItems'
import { NavIcon } from '@/app/layout/NavIcon'
import { useLogout } from '@/auth/application/useLogout'
import { SessionsDialog } from '@/auth/ui/SessionsDialog'
import { useSession } from '@/shared/session/sessionContext'
import { hasAtLeastRole, ROLE_LABEL } from '@/shared/session/roles'

function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('')
}

/** Barra lateral: marca, usuario arriba y navegación por módulo. */
export function Sidebar() {
  const { user } = useSession()
  const logout = useLogout()
  const [sessionsOpen, setSessionsOpen] = useState(false)

  if (!user) {
    return null
  }

  return (
    <aside className="sidebar">
      <div className="brand">
        <span className="brand__mark" aria-hidden="true">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
            <path d="M14 2v6h6" />
          </svg>
        </span>
        <span data-testid="brand-name">Archivos</span>
      </div>

      <button
        type="button"
        className="user-card"
        onClick={() => setSessionsOpen(true)}
        aria-haspopup="dialog"
        title="Ver sesiones activas"
      >
        <span className="user-card__avatar" aria-hidden="true">{initials(user.name)}</span>
        <span className="user-card__who">
          <span className="user-card__name">{user.name}</span>
          <span className="user-card__role">{ROLE_LABEL[user.role]}</span>
        </span>
      </button>

      <nav className="nav" aria-label="Principal">
        <span className="nav__label">General</span>
        {NAV_ITEMS.filter(
          (item) => !item.minRole || hasAtLeastRole(user.role, item.minRole),
        ).map((item) => (
          <NavLink key={item.to} to={item.to} className="nav__link">
            <NavIcon name={item.icon} />
            {item.label}
          </NavLink>
        ))}
      </nav>

      <div className="sidebar__footer">
        <button
          type="button"
          className="logout"
          onClick={() => logout.mutate()}
          disabled={logout.isPending}
        >
          <svg
            width="18"
            height="18"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
            <path d="M16 17l5-5-5-5" />
            <path d="M21 12H9" />
          </svg>
          Cerrar sesión
        </button>
      </div>

      <SessionsDialog open={sessionsOpen} onClose={() => setSessionsOpen(false)} />
    </aside>
  )
}

import { useMutation, useQueryClient } from '@tanstack/react-query'
import { NavLink, useNavigate } from 'react-router'
import { NAV_ITEMS } from '@/app/layout/navItems'
import type { AppRole } from '@/shared/session/model'
import { logout } from '@/shared/session/sessionApi'
import { SESSION_QUERY_KEY } from '@/shared/session/SessionProvider'
import { useSession } from '@/shared/session/sessionContext'
import { hasAtLeastRole } from '@/shared/session/roles'

const ROLE_LABEL: Record<AppRole, string> = {
  standard: 'Estándar',
  area_head: 'Jefe de área',
  manager: 'Gerente',
  admin: 'Administrador',
}

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
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const logoutMutation = useMutation({
    mutationFn: logout,
    onSettled: () => {
      queryClient.setQueryData(SESSION_QUERY_KEY, null)
      navigate('/login', { replace: true })
    },
  })

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

      <div className="user-card">
        <span className="user-card__avatar" aria-hidden="true">{initials(user.name)}</span>
        <span className="user-card__who">
          <span className="user-card__name">{user.name}</span>
          <span className="user-card__role">{ROLE_LABEL[user.role]}</span>
        </span>
      </div>

      <nav className="nav" aria-label="Principal">
        <span className="nav__label">General</span>
        {NAV_ITEMS.filter(
          (item) => !item.minRole || hasAtLeastRole(user.role, item.minRole),
        ).map((item) => (
          <NavLink key={item.to} to={item.to} className="nav__link">
            {item.label}
          </NavLink>
        ))}
      </nav>

      <div className="sidebar__footer">
        <button
          type="button"
          className="logout"
          onClick={() => logoutMutation.mutate()}
          disabled={logoutMutation.isPending}
        >
          Cerrar sesión
        </button>
      </div>
    </aside>
  )
}

import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import type { AppRole } from '@/shared/session/model'
import { useSession } from '@/shared/session/sessionContext'
import { hasAtLeastRole } from '@/shared/session/roles'
import { ForbiddenPage } from '@/shared/ui/ForbiddenPage'
import { PageLoading } from '@/shared/ui/PageLoading'

/** Exige sesión; si no la hay, lleva al inicio de sesión. */
export function RequireSession({ children }: { children: ReactNode }) {
  const { status } = useSession()
  const location = useLocation()

  if (status === 'loading') {
    return <PageLoading />
  }
  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  return <>{children}</>
}

/** Exige un nivel de rol mínimo; si no se alcanza, muestra «sin permisos». */
export function RequireRole({ role, children }: { role: AppRole; children: ReactNode }) {
  const { user } = useSession()

  if (!user || !hasAtLeastRole(user.role, role)) {
    return <ForbiddenPage />
  }
  return <>{children}</>
}

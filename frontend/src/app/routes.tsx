import { lazy, Suspense, type ReactNode } from 'react'
import { createBrowserRouter, Navigate, type RouteObject } from 'react-router'
import { AppLayout } from '@/app/layout/AppLayout'
import { NotFoundPage } from '@/app/pages/NotFoundPage'
import { RequireRole, RequireSession } from '@/shared/session/guards'
import { PageLoading } from '@/shared/ui/PageLoading'

// Cada página se carga por partes: así el paquete inicial no incluye todos los
// módulos. El `Suspense` (aquí y en el layout) muestra el estado de carga.
const LoginPage = lazy(() => import('@/auth/ui/LoginPage').then((m) => ({ default: m.LoginPage })))
const FilesPage = lazy(() => import('@/files/ui/FilesPage').then((m) => ({ default: m.FilesPage })))
const AreasPage = lazy(() => import('@/areas/ui/AreasPage').then((m) => ({ default: m.AreasPage })))
const ManagedAreasPage = lazy(() =>
  import('@/managedareas/ui/ManagedAreasPage').then((m) => ({ default: m.ManagedAreasPage })),
)
const UsersPage = lazy(() => import('@/users/ui/UsersPage').then((m) => ({ default: m.UsersPage })))
const AuditPage = lazy(() => import('@/audit/ui/AuditPage').then((m) => ({ default: m.AuditPage })))

function withSuspense(node: ReactNode) {
  return <Suspense fallback={<PageLoading />}>{node}</Suspense>
}

/** Rutas de la aplicación. Se exportan para poder montarlas en las pruebas. */
export const routes: RouteObject[] = [
  { path: '/login', element: withSuspense(<LoginPage />) },
  {
    path: '/',
    element: (
      <RequireSession>
        <AppLayout />
      </RequireSession>
    ),
    children: [
      { index: true, element: <Navigate to="/archivos" replace /> },
      { path: 'archivos', element: <FilesPage /> },
      { path: 'areas', element: <AreasPage /> },
      {
        path: 'areas-gestionadas',
        element: (
          <RequireRole role="manager">
            <ManagedAreasPage />
          </RequireRole>
        ),
      },
      {
        path: 'usuarios',
        element: (
          <RequireRole role="admin">
            <UsersPage />
          </RequireRole>
        ),
      },
      {
        path: 'historial',
        element: (
          <RequireRole role="manager">
            <AuditPage />
          </RequireRole>
        ),
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]

export const router = createBrowserRouter(routes)

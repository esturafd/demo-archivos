import { createBrowserRouter, Navigate } from 'react-router'
import { AppLayout } from '@/app/layout/AppLayout'
import { NotFoundPage } from '@/app/pages/NotFoundPage'
import { AreasPage } from '@/areas/ui/AreasPage'
import { AuditPage } from '@/audit/ui/AuditPage'
import { LoginPage } from '@/auth/ui/LoginPage'
import { FilesPage } from '@/files/ui/FilesPage'
import { ManagedAreasPage } from '@/managedareas/ui/ManagedAreasPage'
import { UsersPage } from '@/users/ui/UsersPage'
import { RequireRole, RequireSession } from '@/shared/session/guards'

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
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
])

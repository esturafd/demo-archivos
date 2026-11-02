import { createBrowserRouter, Navigate } from 'react-router'
import { AppLayout } from '@/app/layout/AppLayout'
import { ComingSoonPage } from '@/app/pages/ComingSoonPage'
import { NotFoundPage } from '@/app/pages/NotFoundPage'
import { LoginPage } from '@/auth/ui/LoginPage'
import { FilesPage } from '@/files/ui/FilesPage'
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
      { path: 'areas', element: <ComingSoonPage title="Áreas" /> },
      {
        path: 'areas-gestionadas',
        element: (
          <RequireRole role="manager">
            <ComingSoonPage title="Áreas gestionadas" />
          </RequireRole>
        ),
      },
      {
        path: 'usuarios',
        element: (
          <RequireRole role="admin">
            <ComingSoonPage title="Usuarios" />
          </RequireRole>
        ),
      },
      {
        path: 'historial',
        element: (
          <RequireRole role="manager">
            <ComingSoonPage title="Historial" />
          </RequireRole>
        ),
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])

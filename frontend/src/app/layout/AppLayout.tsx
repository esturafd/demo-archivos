import { Outlet } from 'react-router'
import { Sidebar } from '@/app/layout/Sidebar'

/** Estructura de la aplicación: barra lateral + contenido. */
export function AppLayout() {
  return (
    <div className="app-shell">
      <Sidebar />
      <main className="content">
        <Outlet />
      </main>
    </div>
  )
}

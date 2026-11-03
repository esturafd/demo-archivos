import { Suspense } from 'react'
import { Outlet } from 'react-router'
import { Sidebar } from '@/app/layout/Sidebar'
import { PageLoading } from '@/shared/ui/PageLoading'

/** Estructura de la aplicación: barra lateral + contenido. */
export function AppLayout() {
  return (
    <div className="app-shell">
      <Sidebar />
      <main className="content">
        {/* Las páginas se cargan por partes (code splitting): el área de contenido
            muestra el estado de carga mientras llega el trozo. */}
        <Suspense fallback={<PageLoading />}>
          <Outlet />
        </Suspense>
      </main>
    </div>
  )
}

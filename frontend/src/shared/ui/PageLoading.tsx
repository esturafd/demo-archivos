/** Pantalla de carga a página completa (mientras se resuelve la sesión). */
export function PageLoading() {
  return (
    <div className="page-loading" role="status" aria-live="polite">
      <span>Cargando…</span>
    </div>
  )
}

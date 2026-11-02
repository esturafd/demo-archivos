/** Pantalla para cuando la sesión no alcanza el nivel necesario. */
export function ForbiddenPage() {
  return (
    <div className="state" role="alert">
      <h2>Sin permisos</h2>
      <p>No tienes acceso a esta sección.</p>
    </div>
  )
}

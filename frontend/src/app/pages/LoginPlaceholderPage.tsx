/**
 * Marcador del inicio de sesión.
 *
 * <p>La pantalla real llega con el módulo `auth`; aquí solo se ocupa la ruta para
 * que las guardas puedan redirigir.
 */
export function LoginPlaceholderPage() {
  return (
    <main className="content">
      <div className="state">
        <h2>Archivos</h2>
        <p>El inicio de sesión se construye en la fase de autenticación.</p>
      </div>
    </main>
  )
}

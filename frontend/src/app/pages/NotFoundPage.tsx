import { Link } from 'react-router'

/** Página no encontrada. */
export function NotFoundPage() {
  return (
    <div className="state">
      <h2>Página no encontrada</h2>
      <p>
        <Link to="/">Volver al inicio</Link>
      </p>
    </div>
  )
}

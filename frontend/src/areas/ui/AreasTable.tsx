import { Link } from 'react-router'
import type { Area } from '@/areas/domain/area'

interface AreasTableProps {
  areas: Area[]
  /** El administrador ve además «Ver usuarios», editar y activar/desactivar. */
  canManage: boolean
  onEdit: (area: Area) => void
  onToggleActive: (area: Area) => void
}

/** Tabla de áreas. Cada fila enlaza a los usuarios del área. */
export function AreasTable({ areas, canManage, onEdit, onToggleActive }: AreasTableProps) {
  return (
    <table className="data-table">
      <thead>
        <tr>
          <th>Nombre</th>
          <th>Descripción</th>
          <th>Estado</th>
          {canManage ? (
            <th>
              <span className="sr-only">Acciones</span>
            </th>
          ) : null}
        </tr>
      </thead>
      <tbody>
        {areas.map((area) => (
          <tr key={area.id}>
            <td className="area-name">{area.name}</td>
            <td className="muted area-desc">{area.description || '—'}</td>
            <td>
              <span className={area.active ? 'status' : 'status status--off'}>
                {area.active ? 'Activa' : 'Inactiva'}
              </span>
            </td>
            {canManage ? (
              <td>
                <span className="actions">
                  <Link
                    className="btn-icon"
                    to={`/usuarios?area=${area.id}`}
                    title="Ver usuarios"
                    aria-label={`Ver usuarios de ${area.name}`}
                  >
                    <svg
                      width="17"
                      height="17"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      aria-hidden="true"
                    >
                      <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
                      <circle cx="9" cy="7" r="4" />
                      <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
                      <path d="M16 3.13a4 4 0 0 1 0 7.75" />
                    </svg>
                  </Link>
                  <button
                    type="button"
                    className="btn-icon"
                    title="Editar"
                    aria-label={`Editar ${area.name}`}
                    onClick={() => onEdit(area)}
                  >
                    <svg
                      width="17"
                      height="17"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      aria-hidden="true"
                    >
                      <path d="M12 20h9" />
                      <path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4z" />
                    </svg>
                  </button>
                  <button
                    type="button"
                    className="btn-icon btn-icon--danger"
                    title={area.active ? 'Desactivar' : 'Activar'}
                    aria-label={`${area.active ? 'Desactivar' : 'Activar'} ${area.name}`}
                    onClick={() => onToggleActive(area)}
                  >
                    <svg
                      width="17"
                      height="17"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      aria-hidden="true"
                    >
                      <path d="M18.36 6.64a9 9 0 1 1-12.73 0" />
                      <path d="M12 2v10" />
                    </svg>
                  </button>
                </span>
              </td>
            ) : null}
          </tr>
        ))}
      </tbody>
    </table>
  )
}

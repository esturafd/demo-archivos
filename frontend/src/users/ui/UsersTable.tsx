import { Link } from 'react-router'
import { ROLE_LABEL } from '@/shared/session/roles'
import type { UserDetail } from '@/users/domain/user'

function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('')
}

interface UsersTableProps {
  users: UserDetail[]
  currentUserId: string
  onEdit: (user: UserDetail) => void
  onToggleActive: (user: UserDetail) => void
}

/** Tabla de usuarios. Cada fila enlaza a sus archivos y permite editar. */
export function UsersTable({ users, currentUserId, onEdit, onToggleActive }: UsersTableProps) {
  return (
    <table className="data-table">
      <thead>
        <tr>
          <th>Nombre</th>
          <th>Correo</th>
          <th>Rol</th>
          <th>Área</th>
          <th>Estado</th>
          <th>
            <span className="sr-only">Acciones</span>
          </th>
        </tr>
      </thead>
      <tbody>
        {users.map((user) => {
          const isSelf = user.id === currentUserId
          return (
            <tr key={user.id}>
              <td>
                <span className="user-name">
                  <span className="avatar" aria-hidden="true">
                    {initials(user.name)}
                  </span>
                  {user.name}
                </span>
              </td>
              <td className="muted">{user.email}</td>
              <td>
                <span className="badge">{ROLE_LABEL[user.role]}</span>
              </td>
              <td className="muted">{user.areaName ?? '—'}</td>
              <td>
                <span className={user.active ? 'status' : 'status status--off'}>
                  {user.active ? 'Activo' : 'Inactivo'}
                </span>
              </td>
              <td>
                <span className="actions">
                  <Link
                    className="btn-icon"
                    to={`/archivos?owner=${user.id}`}
                    title="Ver archivos"
                    aria-label={`Ver archivos de ${user.name}`}
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
                      <path d="M4 20h16a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7l-2-2H4a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2z" />
                    </svg>
                  </Link>
                  <button
                    type="button"
                    className="btn-icon"
                    title="Editar"
                    aria-label={`Editar ${user.name}`}
                    onClick={() => onEdit(user)}
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
                  {isSelf ? null : (
                    <button
                      type="button"
                      className="btn-icon btn-icon--danger"
                      title={user.active ? 'Desactivar' : 'Activar'}
                      aria-label={`${user.active ? 'Desactivar' : 'Activar'} ${user.name}`}
                      onClick={() => onToggleActive(user)}
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
                  )}
                </span>
              </td>
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}

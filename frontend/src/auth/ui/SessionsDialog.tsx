import { useRevokeSession } from '@/auth/application/useRevokeSession'
import { useSessions } from '@/auth/application/useSessions'
import { ErrorState } from '@/shared/ui/ErrorState'
import { Modal } from '@/shared/ui/Modal'

function formatDate(value: string | null | undefined): string {
  if (!value) {
    return '—'
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString('es')
}

/** Sesiones activas propias, con la opción de revocar cada una. */
export function SessionsDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const sessions = useSessions(open)
  const revoke = useRevokeSession()

  return (
    <Modal
      open={open}
      title="Sesiones activas"
      onClose={onClose}
      footer={
        <button type="button" className="btn" onClick={onClose}>
          Cerrar
        </button>
      }
    >
      {sessions.isPending ? <p>Cargando…</p> : null}
      {sessions.isError ? <ErrorState error={sessions.error} /> : null}

      {sessions.data ? (
        <ul className="sessions">
          {sessions.data.map((session) => (
            <li key={session.id} className="sessions__item">
              <div className="sessions__info">
                <span className="sessions__ip">{session.ipAddress ?? 'IP desconocida'}</span>
                {session.current ? <span className="sessions__badge">esta sesión</span> : null}
                <span className="sessions__meta">
                  {session.userAgent ?? '—'} · {formatDate(session.createdAt)}
                </span>
              </div>
              {session.current ? null : (
                <button
                  type="button"
                  className="btn"
                  onClick={() => revoke.mutate(session.id)}
                  disabled={revoke.isPending}
                >
                  Revocar
                </button>
              )}
            </li>
          ))}
        </ul>
      ) : null}
    </Modal>
  )
}

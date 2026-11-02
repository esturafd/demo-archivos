import { Fragment, useState } from 'react'
import { actionLabel, type AuditEvent } from '@/audit/domain/auditEvent'
import { formatDateTime, formatMetadata } from '@/audit/domain/format'
import { roleLabel } from '@/shared/session/roles'

function actorText(event: AuditEvent): string {
  const email = event.actorEmail ?? '—'
  return event.actorRole ? `${email} · ${roleLabel(event.actorRole)}` : email
}

/** Tabla del historial. Cada fila se expande con el detalle del evento. */
export function AuditTable({ events }: { events: AuditEvent[] }) {
  const [expanded, setExpanded] = useState<number | null>(null)

  return (
    <table className="data-table audit-table">
      <thead>
        <tr>
          <th>Fecha</th>
          <th>Acción</th>
          <th>Actor</th>
          <th>Archivo</th>
          <th>IP</th>
          <th>
            <span className="sr-only">Detalle</span>
          </th>
        </tr>
      </thead>
      <tbody>
        {events.map((event) => {
          const open = expanded === event.id
          return (
            <Fragment key={event.id}>
              <tr className="audit-row">
                <td className="muted">{formatDateTime(event.occurredAt)}</td>
                <td>
                  <span className="badge">{actionLabel(event.action)}</span>
                </td>
                <td className="muted">{event.actorEmail ?? '—'}</td>
                <td className="muted">{event.fileName ?? '—'}</td>
                <td className="muted">{event.ipAddress ?? '—'}</td>
                <td>
                  <button
                    type="button"
                    className="btn-icon"
                    aria-expanded={open}
                    aria-label={open ? 'Ocultar detalle' : 'Ver detalle'}
                    onClick={() => setExpanded(open ? null : event.id)}
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
                      style={{ transform: open ? 'rotate(180deg)' : undefined }}
                    >
                      <path d="M6 9l6 6 6-6" />
                    </svg>
                  </button>
                </td>
              </tr>
              {open ? (
                <tr className="audit-detail">
                  <td colSpan={6}>
                    <dl className="audit-detail__grid">
                      <div>
                        <dt>Ocurrido</dt>
                        <dd>{formatDateTime(event.occurredAt)}</dd>
                      </div>
                      <div>
                        <dt>Actor</dt>
                        <dd>{actorText(event)}</dd>
                      </div>
                      <div>
                        <dt>Área</dt>
                        <dd>{event.actorAreaName ?? '—'}</dd>
                      </div>
                      <div>
                        <dt>Archivo</dt>
                        <dd>
                          {event.fileName ?? '—'}
                          {event.fileId ? <span className="muted"> ({event.fileId})</span> : null}
                        </dd>
                      </div>
                      <div>
                        <dt>IP</dt>
                        <dd>{event.ipAddress ?? '—'}</dd>
                      </div>
                      <div>
                        <dt>Petición</dt>
                        <dd>{event.requestId ?? '—'}</dd>
                      </div>
                      <div className="audit-detail__wide">
                        <dt>Metadatos</dt>
                        <dd>
                          <pre>{formatMetadata(event.metadata)}</pre>
                        </dd>
                      </div>
                    </dl>
                  </td>
                </tr>
              ) : null}
            </Fragment>
          )
        })}
      </tbody>
    </table>
  )
}

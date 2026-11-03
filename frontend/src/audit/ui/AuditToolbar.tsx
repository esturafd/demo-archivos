import { AUDIT_ACTIONS, ACTION_LABEL } from '@/audit/domain/auditEvent'

/** Opción del selector de actor. */
export interface ActorOption {
  id: string
  label: string
}

interface AuditToolbarProps {
  action: string
  onActionChange: (value: string) => void
  actors: ActorOption[]
  actorId: string
  onActorChange: (value: string) => void
}

/** Filtros del historial: acción y actor. */
export function AuditToolbar({
  action,
  onActionChange,
  actors,
  actorId,
  onActorChange,
}: AuditToolbarProps) {
  return (
    <div className="toolbar">
      <label className="select">
        <span className="lbl">Acción</span>
        <select
          aria-label="Filtrar por acción"
          value={action}
          onChange={(event) => onActionChange(event.target.value)}
        >
          <option value="">Todas las acciones</option>
          {AUDIT_ACTIONS.map((value) => (
            <option key={value} value={value}>
              {ACTION_LABEL[value]}
            </option>
          ))}
        </select>
      </label>

      <label className="select">
        <span className="lbl">Actor</span>
        <select
          aria-label="Filtrar por actor"
          value={actorId}
          onChange={(event) => onActorChange(event.target.value)}
        >
          <option value="">Todos los actores</option>
          {actors.map((actor) => (
            <option key={actor.id} value={actor.id}>
              {actor.label}
            </option>
          ))}
        </select>
      </label>
    </div>
  )
}

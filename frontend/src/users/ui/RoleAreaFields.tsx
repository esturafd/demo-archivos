import type { UseFormRegisterReturn } from 'react-hook-form'
import type { Area } from '@/areas/domain/area'
import { APP_ROLES, type AppRole } from '@/shared/session/model'
import { ROLE_LABEL, requiresArea } from '@/users/domain/user'

interface RoleAreaFieldsProps {
  role: AppRole
  areas: Area[]
  roleField: UseFormRegisterReturn
  areaField: UseFormRegisterReturn
  roleError?: string
  areaError?: string
  /** Se desactiva al editar el propio administrador (no puede cambiarse el rol). */
  roleDisabled?: boolean
}

/**
 * Campos de rol y área, comunes al alta y a la edición.
 *
 * <p>El área solo se pide para estándar y jefe de área; para gerente y
 * administrador el área propia no aplica.
 */
export function RoleAreaFields({
  role,
  areas,
  roleField,
  areaField,
  roleError,
  areaError,
  roleDisabled = false,
}: RoleAreaFieldsProps) {
  return (
    <>
      <label>
        Rol
        <select {...roleField} disabled={roleDisabled}>
          {APP_ROLES.map((value) => (
            <option key={value} value={value}>
              {ROLE_LABEL[value]}
            </option>
          ))}
        </select>
        {roleError ? <small className="field-error">{roleError}</small> : null}
      </label>

      {requiresArea(role) ? (
        <label>
          Área
          <select {...areaField}>
            <option value="">Elige un área…</option>
            {areas.map((area) => (
              <option key={area.id} value={area.id}>
                {area.name}
                {area.active ? '' : ' (inactiva)'}
              </option>
            ))}
          </select>
          {areaError ? <small className="field-error">{areaError}</small> : null}
        </label>
      ) : null}
    </>
  )
}

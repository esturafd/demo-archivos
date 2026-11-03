import type { Area } from '@/areas/domain/area'
import { SearchField } from '@/shared/ui/SearchField'

interface UsersToolbarProps {
  areas: Area[]
  areaId: string
  onAreaChange: (id: string) => void
  search: string
  onSearchChange: (value: string) => void
}

/** Filtros del listado de usuarios: área (todas por defecto) y búsqueda. */
export function UsersToolbar({
  areas,
  areaId,
  onAreaChange,
  search,
  onSearchChange,
}: UsersToolbarProps) {
  return (
    <div className="toolbar">
      <label className="select">
        <span className="lbl">Área</span>
        <select
          aria-label="Filtrar por área"
          value={areaId}
          onChange={(event) => onAreaChange(event.target.value)}
        >
          <option value="">Todas las áreas</option>
          {areas.map((area) => (
            <option key={area.id} value={area.id}>
              {area.name}
            </option>
          ))}
        </select>
      </label>

      <SearchField
        value={search}
        onChange={onSearchChange}
        label="Buscar usuarios"
        placeholder="Buscar por nombre o correo…"
      />
    </div>
  )
}

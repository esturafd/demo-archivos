import type { FileOwner } from '@/files/domain/file'
import { SearchField } from '@/shared/ui/SearchField'

interface FilesToolbarProps {
  /** Si el rol puede ver archivos de otros; si no, el selector no aparece. */
  canSeeOthers: boolean
  owners: FileOwner[]
  ownerId: string
  selfId: string
  selfName: string
  onOwnerChange: (id: string) => void
  search: string
  onSearchChange: (value: string) => void
}

/**
 * Filtros del listado: selector de usuario (solo para quien ve archivos de
 * otros) y búsqueda por nombre. El estado vive en la URL, en la página.
 */
export function FilesToolbar({
  canSeeOthers,
  owners,
  ownerId,
  selfId,
  selfName,
  onOwnerChange,
  search,
  onSearchChange,
}: FilesToolbarProps) {
  // El propio usuario siempre aparece primero, aunque la lista aún no haya llegado.
  const options = [
    { id: selfId, label: `${selfName} (yo)` },
    ...owners
      .filter((owner) => owner.id !== selfId)
      .map((owner) => ({ id: owner.id, label: owner.name ?? owner.email })),
  ]

  return (
    <div className="toolbar">
      {canSeeOthers ? (
        <label className="select">
          <span className="lbl">Ver archivos de</span>
          <select
            aria-label="Elegir el usuario cuyos archivos se muestran"
            value={ownerId}
            onChange={(event) => onOwnerChange(event.target.value)}
          >
            {options.map((option) => (
              <option key={option.id} value={option.id}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
      ) : null}

      <SearchField
        value={search}
        onChange={onSearchChange}
        label="Buscar archivos por nombre"
        placeholder="Buscar por nombre…"
      />
    </div>
  )
}

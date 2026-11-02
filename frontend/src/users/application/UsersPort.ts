import type { AppRole } from '@/shared/session/model'
import type { UserDetail } from '@/users/domain/user'

/** Parámetros del listado de usuarios. */
export interface ListUsersParams {
  /** Texto a buscar en nombre o correo. */
  search?: string
  /** Área por la que acotar; por defecto, todas. */
  areaId?: string
}

/** Datos del alta de un usuario. */
export interface CreateUserInput {
  email: string
  password: string
  name: string
  role: AppRole
  /** Área propia: obligatoria para estándar y jefe de área; `null` en el resto. */
  areaId: string | null
}

/** Datos de la edición de un usuario. */
export interface UpdateUserInput {
  name: string
  role: AppRole
  /** Área propia: obligatoria para estándar y jefe de área; `null` en el resto. */
  areaId: string | null
  active: boolean
}

/**
 * Puerto del módulo `users`: administración de usuarios.
 *
 * <p>La implementación (HTTP) vive en `infrastructure`.
 */
export interface UsersPort {
  /** Lista y busca usuarios visibles para el administrador. */
  list(params: ListUsersParams): Promise<UserDetail[]>

  /** Crea un usuario. */
  create(input: CreateUserInput): Promise<UserDetail>

  /** Edita nombre, rol, área o estado. */
  update(id: string, input: UpdateUserInput): Promise<UserDetail>
}

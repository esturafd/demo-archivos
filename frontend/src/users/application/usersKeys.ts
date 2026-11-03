import type { ListUsersParams } from '@/users/application/UsersPort'

/** Claves de caché del módulo `users`. */
export const usersKeys = {
  all: ['users'] as const,
  list: (params: ListUsersParams) => ['users', 'list', params] as const,
}

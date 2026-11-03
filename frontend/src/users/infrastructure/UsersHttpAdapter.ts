import { z } from 'zod'
import type {
  CreateUserInput,
  ListUsersParams,
  UpdateUserInput,
  UsersPort,
} from '@/users/application/UsersPort'
import { userDetailSchema, type UserDetail } from '@/users/domain/user'
import { httpClient } from '@/shared/http/httpClient'

/** Omite los parámetros vacíos para no ensuciar la query. */
function queryParams(params: ListUsersParams): Record<string, string> {
  const result: Record<string, string> = {}
  if (params.search) {
    result.search = params.search
  }
  if (params.areaId) {
    result.areaId = params.areaId
  }
  return result
}

/** Adaptador de `users` sobre la API HTTP. */
export class UsersHttpAdapter implements UsersPort {
  async list(params: ListUsersParams): Promise<UserDetail[]> {
    const { data } = await httpClient.get('/users', { params: queryParams(params) })
    return z.array(userDetailSchema).parse(data)
  }

  async create(input: CreateUserInput): Promise<UserDetail> {
    const { data } = await httpClient.post('/users', input)
    return userDetailSchema.parse(data)
  }

  async update(id: string, input: UpdateUserInput): Promise<UserDetail> {
    const { data } = await httpClient.patch(`/users/${id}`, input)
    return userDetailSchema.parse(data)
  }
}

/** Instancia única del adaptador. */
export const usersAdapter: UsersPort = new UsersHttpAdapter()

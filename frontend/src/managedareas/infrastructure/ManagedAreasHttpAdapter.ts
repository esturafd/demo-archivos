import type { ManagedAreasPort } from '@/managedareas/application/ManagedAreasPort'
import {
  managedAreasResponseSchema,
  type ManagedArea,
} from '@/managedareas/domain/managedArea'
import { httpClient } from '@/shared/http/httpClient'

/** Adaptador de `managedareas` sobre la API HTTP. */
export class ManagedAreasHttpAdapter implements ManagedAreasPort {
  async list(managerId: string): Promise<ManagedArea[]> {
    const { data } = await httpClient.get(`/users/${managerId}/managed-areas`)
    return managedAreasResponseSchema.parse(data).areas
  }

  async replace(managerId: string, areaIds: string[]): Promise<ManagedArea[]> {
    const { data } = await httpClient.put(`/users/${managerId}/managed-areas`, { areaIds })
    return managedAreasResponseSchema.parse(data).areas
  }
}

/** Instancia única del adaptador. */
export const managedAreasAdapter: ManagedAreasPort = new ManagedAreasHttpAdapter()

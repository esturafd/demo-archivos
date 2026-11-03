import { z } from 'zod'
import type {
  AreasPort,
  CreateAreaInput,
  UpdateAreaInput,
} from '@/areas/application/AreasPort'
import { areaSchema, type Area } from '@/areas/domain/area'
import { httpClient } from '@/shared/http/httpClient'

/** Adaptador de `areas` sobre la API HTTP. */
export class AreasHttpAdapter implements AreasPort {
  async list(): Promise<Area[]> {
    const { data } = await httpClient.get('/areas')
    return z.array(areaSchema).parse(data)
  }

  async create(input: CreateAreaInput): Promise<Area> {
    const { data } = await httpClient.post('/areas', input)
    return areaSchema.parse(data)
  }

  async update(id: string, input: UpdateAreaInput): Promise<Area> {
    const { data } = await httpClient.patch(`/areas/${id}`, input)
    return areaSchema.parse(data)
  }
}

/** Instancia única del adaptador. */
export const areasAdapter: AreasPort = new AreasHttpAdapter()

import { describe, expect, it } from 'vitest'
import { createUserSchema, updateUserSchema } from '@/users/domain/userForm'

const VALID = {
  email: 'maria@demo.local',
  password: 'Contraseña123',
  name: 'María Pérez',
  role: 'standard' as const,
  areaId: 'a1',
}

describe('createUserSchema', () => {
  it('acepta un alta válida', () => {
    expect(createUserSchema.safeParse(VALID).success).toBe(true)
  })

  it('rechaza un correo no válido', () => {
    const result = createUserSchema.safeParse({ ...VALID, email: 'no-es-correo' })
    expect(result.success).toBe(false)
  })

  it('rechaza una contraseña corta', () => {
    const result = createUserSchema.safeParse({ ...VALID, password: 'corta' })
    expect(result.success).toBe(false)
  })

  it('exige área para estándar y jefe de área', () => {
    expect(createUserSchema.safeParse({ ...VALID, areaId: '' }).success).toBe(false)
    expect(
      createUserSchema.safeParse({ ...VALID, role: 'area_head', areaId: '' }).success,
    ).toBe(false)
  })

  it('no exige área para gerente ni administrador', () => {
    expect(createUserSchema.safeParse({ ...VALID, role: 'manager', areaId: '' }).success).toBe(true)
    expect(createUserSchema.safeParse({ ...VALID, role: 'admin', areaId: '' }).success).toBe(true)
  })
})

describe('updateUserSchema', () => {
  it('exige área para estándar', () => {
    const result = updateUserSchema.safeParse({
      name: 'X',
      role: 'standard',
      areaId: '',
      active: true,
    })
    expect(result.success).toBe(false)
  })

  it('acepta un gerente sin área', () => {
    const result = updateUserSchema.safeParse({
      name: 'X',
      role: 'manager',
      areaId: '',
      active: false,
    })
    expect(result.success).toBe(true)
  })
})

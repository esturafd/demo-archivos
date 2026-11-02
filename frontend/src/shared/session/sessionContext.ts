import { createContext, useContext } from 'react'
import type { SessionUser } from '@/shared/session/model'

export type SessionStatus = 'loading' | 'authenticated' | 'anonymous'

export interface SessionValue {
  status: SessionStatus
  user: SessionUser | null
}

export const SessionContext = createContext<SessionValue | null>(null)

/** Sesión actual. Debe usarse dentro de `SessionProvider`. */
export function useSession(): SessionValue {
  const value = useContext(SessionContext)
  if (!value) {
    throw new Error('useSession debe usarse dentro de <SessionProvider>')
  }
  return value
}

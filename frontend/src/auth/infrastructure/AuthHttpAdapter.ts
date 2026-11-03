import type { AuthPort } from '@/auth/application/AuthPort'
import type { Credentials } from '@/auth/domain/credentials'
import { sessionsResponseSchema, type SessionSummary } from '@/auth/domain/session'
import { httpClient } from '@/shared/http/httpClient'

/** Adaptador de `auth` sobre la API HTTP. */
export class AuthHttpAdapter implements AuthPort {
  async login(credentials: Credentials): Promise<void> {
    await httpClient.post('/auth/sessions', credentials)
  }

  async logout(): Promise<void> {
    await httpClient.delete('/auth/sessions/current')
  }

  async listSessions(): Promise<SessionSummary[]> {
    const { data } = await httpClient.get('/auth/sessions')
    return sessionsResponseSchema.parse(data).sessions
  }

  async revokeSession(sessionId: string): Promise<void> {
    await httpClient.delete(`/auth/sessions/${sessionId}`)
  }
}

/** Instancia única del adaptador. */
export const authAdapter: AuthPort = new AuthHttpAdapter()

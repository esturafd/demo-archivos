import { setupServer } from 'msw/node'

/** Servidor de MSW compartido por todas las pruebas. */
export const server = setupServer()

/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Base de la API. Por defecto `/api` (mismo origen). */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

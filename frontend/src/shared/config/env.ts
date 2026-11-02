/**
 * Configuración de entorno del frontend.
 *
 * Sólo se expone lo que empieza por `VITE_`. Por defecto la API vive en `/api`
 * (mismo origen, gracias al *rewrite* de producción o al proxy de desarrollo).
 */
export const env = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? '/api',
} as const

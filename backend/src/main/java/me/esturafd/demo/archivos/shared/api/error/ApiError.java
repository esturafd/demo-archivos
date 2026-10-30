package me.esturafd.demo.archivos.shared.api.error;

/**
 * Cuerpo uniforme de error.
 *
 * <p>Es el contrato público de la API: todas las respuestas de error tienen exactamente estos
 * tres campos.
 *
 * @param code      código estable de error, pensado para que el cliente decida
 * @param message   mensaje para el usuario final, sin detalles internos
 * @param requestId identificador de la petición, para correlacionar con los registros
 */
public record ApiError(String code, String message, String requestId) {
}

package me.esturafd.demo.archivos.shared.error.api;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

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
@Schema(
    name = "ApiError",
    description = "Cuerpo uniforme de error. Todas las respuestas de error tienen esta forma."
)
public record ApiError(
    @Schema(
        description = "Código estable de error, pensado para que el cliente decida.",
        example = "not_found")
    String code,
    @Schema(
        description = "Mensaje para el usuario final, sin detalles internos.",
        example = "El recurso solicitado no está disponible.")
    String message,
    @Schema(
        description = "Identificador de la petición, para correlacionar con los registros.",
        example = "a1b2c3d4")
    String requestId
) {
}

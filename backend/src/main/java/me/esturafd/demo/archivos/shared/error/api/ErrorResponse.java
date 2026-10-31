package me.esturafd.demo.archivos.shared.error.api;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import me.esturafd.demo.archivos.shared.error.application.ErrorCode;

/**
 * Construye la respuesta HTTP de error con el cuerpo uniforme.
 *
 * <p>Centraliza el formato para que ningún punto de la API pueda devolver un error con otra
 * forma.
 */
public final class ErrorResponse {

    private ErrorResponse() {
    }

    /** Usa el estado que corresponde al código. */
    public static Response of(ErrorCode errorCode, String requestId) {
        return of(ErrorStatus.of(errorCode), errorCode.code(), errorCode.defaultMessage(), requestId);
    }

    /** Conserva un estado y un mensaje concretos. */
    public static Response of(int status, String code, String message, String requestId) {
        return Response.status(status)
            .entity(new ApiError(code, message, requestId))
            .type(MediaType.APPLICATION_JSON)
            .build();
    }
}

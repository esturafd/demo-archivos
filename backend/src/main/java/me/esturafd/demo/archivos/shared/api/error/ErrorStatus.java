package me.esturafd.demo.archivos.shared.api.error;

import jakarta.ws.rs.core.Response;
import me.esturafd.demo.archivos.shared.application.error.ErrorCode;

import java.util.Map;

/**
 * Traduce códigos de error a estados HTTP.
 *
 * <p>Es el único punto que conoce los estados: el resto de la aplicación no depende de HTTP.
 *
 * <p>Se trabaja con el número de estado y no con {@link Response.Status} porque la enumeración
 * de Jakarta REST no cubre todos los estados que usamos: no existe constante para 422, y
 * depender de ella impediría conservar estados que fije el propio servidor.
 */
public final class ErrorStatus {

    /** No hay constante en {@link Response.Status} para este estado. */
    private static final int UNPROCESSABLE_CONTENT = 422;

    private static final Map<ErrorCode, Integer> STATUSES = Map.ofEntries(
        Map.entry(ErrorCode.BAD_REQUEST, Response.Status.BAD_REQUEST.getStatusCode()),
        Map.entry(ErrorCode.VALIDATION_ERROR, Response.Status.BAD_REQUEST.getStatusCode()),
        Map.entry(ErrorCode.UNAUTHENTICATED, Response.Status.UNAUTHORIZED.getStatusCode()),
        Map.entry(ErrorCode.FORBIDDEN, Response.Status.FORBIDDEN.getStatusCode()),
        Map.entry(ErrorCode.NOT_FOUND, Response.Status.NOT_FOUND.getStatusCode()),
        Map.entry(ErrorCode.CONFLICT, Response.Status.CONFLICT.getStatusCode()),
        Map.entry(ErrorCode.PAYLOAD_TOO_LARGE, Response.Status.REQUEST_ENTITY_TOO_LARGE.getStatusCode()),
        Map.entry(ErrorCode.UNSUPPORTED_MEDIA_TYPE, Response.Status.UNSUPPORTED_MEDIA_TYPE.getStatusCode()),
        Map.entry(ErrorCode.UNPROCESSABLE_CONTENT, UNPROCESSABLE_CONTENT),
        Map.entry(ErrorCode.TOO_MANY_REQUESTS, Response.Status.TOO_MANY_REQUESTS.getStatusCode()),
        Map.entry(ErrorCode.INTERNAL_ERROR, Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()),
        Map.entry(ErrorCode.SERVICE_UNAVAILABLE, Response.Status.SERVICE_UNAVAILABLE.getStatusCode())
    );

    private ErrorStatus() {
    }

    public static int of(ErrorCode errorCode) {
        return STATUSES.getOrDefault(errorCode, Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    }

    /**
     * Deduce el código a partir de un estado ya existente.
     *
     * <p>Se usa cuando el estado lo puso el propio servidor, por ejemplo al rechazar un método
     * HTTP no permitido. Si no corresponde a ningún código conocido, se usa el de su familia
     * (4xx o 5xx) para no inventar códigos nuevos.
     */
    public static ErrorCode fromStatus(int status) {
        for (Map.Entry<ErrorCode, Integer> entry : STATUSES.entrySet()) {
            if (entry.getValue() == status) {
                return entry.getKey();
            }
        }
        return status >= 500 ? ErrorCode.INTERNAL_ERROR : ErrorCode.BAD_REQUEST;
    }
}

package me.esturafd.demo.archivos.shared.application.error;

/**
 * Excepción controlada de la aplicación.
 *
 * <p>Lleva un {@link ErrorCode} que determina el estado HTTP y el cuerpo de la respuesta. Los
 * casos de uso lanzan esta excepción cuando el fallo forma parte del comportamiento esperado:
 * un recurso fuera de alcance, un conflicto de unicidad, un intento de acceso indebido.
 *
 * <p>No depende de HTTP: la traducción a un estado concreto ocurre en la capa de API.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, null, null);
    }

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public ApiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message == null || message.isBlank() ? errorCode.defaultMessage() : message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    /**
     * Marca esta excepción como no registrable con traza completa.
     *
     * <p>Los fallos esperados no necesitan ensuciar los registros; los inesperados, tampoco
     * deberían silenciarse. Se deja a la capa de API decidir cómo registrarlos.
     */
    public boolean isExpected() {
        return errorCode != ErrorCode.INTERNAL_ERROR;
    }

    // -- Constructores abreviados ---------------------------------------------

    public static ApiException badRequest(String message) {
        return new ApiException(ErrorCode.BAD_REQUEST, message);
    }

    public static ApiException validation(String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, message);
    }

    public static ApiException unauthenticated() {
        return new ApiException(ErrorCode.UNAUTHENTICATED);
    }

    public static ApiException forbidden() {
        return new ApiException(ErrorCode.FORBIDDEN);
    }

    /**
     * El recurso no existe o está fuera del alcance del usuario.
     *
     * <p>Ambos casos comparten respuesta a propósito: no se debe revelar la existencia de
     * recursos que el usuario no puede ver.
     */
    public static ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND);
    }

    public static ApiException conflict(String message) {
        return new ApiException(ErrorCode.CONFLICT, message);
    }

    public static ApiException payloadTooLarge() {
        return new ApiException(ErrorCode.PAYLOAD_TOO_LARGE);
    }

    public static ApiException unsupportedMediaType() {
        return new ApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    public static ApiException unprocessableContent(String message) {
        return new ApiException(ErrorCode.UNPROCESSABLE_CONTENT, message);
    }

    public static ApiException tooManyRequests() {
        return new ApiException(ErrorCode.TOO_MANY_REQUESTS);
    }

    public static ApiException internalError(Throwable cause) {
        return new ApiException(ErrorCode.INTERNAL_ERROR, null, cause);
    }

    public static ApiException serviceUnavailable(String message) {
        return new ApiException(ErrorCode.SERVICE_UNAVAILABLE, message);
    }
}

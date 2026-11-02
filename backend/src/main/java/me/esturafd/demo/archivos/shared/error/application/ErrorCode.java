package me.esturafd.demo.archivos.shared.error.application;

/**
 * Códigos de error estables de la API.
 *
 * <p>El valor de {@link #code()} es el contrato con los clientes: no cambia aunque cambie el
 * mensaje. El mensaje por defecto se usa cuando la operación no aporta uno más concreto y está
 * redactado para el usuario final, sin detalles internos.
 *
 * <p>Cada código corresponde a uno de los estados documentados en la API.
 */
public enum ErrorCode {

    /** La petición está mal formada. */
    BAD_REQUEST("bad_request", "La petición no se pudo procesar."),

    /** Los datos enviados no superan la validación. */
    VALIDATION_ERROR("validation_error", "La petición contiene datos inválidos."),

    /** No hay una sesión válida. */
    UNAUTHENTICATED("unauthenticated", "Debes iniciar sesión para continuar."),

    /** La sesión es válida, pero el rol no alcanza para esta operación. */
    FORBIDDEN("forbidden", "No tienes permisos para realizar esta acción."),

    /** El recurso no existe o queda fuera del alcance del usuario. */
    NOT_FOUND("not_found", "El recurso solicitado no está disponible."),

    /** La operación choca con el estado actual, por ejemplo un correo duplicado. */
    CONFLICT("conflict", "La operación entra en conflicto con el estado actual."),

    /** El archivo supera el tamaño permitido. */
    PAYLOAD_TOO_LARGE("payload_too_large", "El archivo supera el tamaño permitido."),

    /** El tipo de archivo no está permitido. */
    UNSUPPORTED_MEDIA_TYPE("unsupported_media_type", "El tipo de archivo no está permitido."),

    /** El contenido no coincide con el tipo declarado. */
    UNPROCESSABLE_CONTENT("unprocessable_content", "El contenido no coincide con el tipo declarado."),

    /** Demasiados intentos, por ejemplo de inicio de sesión. */
    TOO_MANY_REQUESTS("too_many_requests", "Demasiados intentos. Vuelve a intentarlo más tarde."),

    /** Error inesperado. Nunca se expone el detalle. */
    INTERNAL_ERROR("internal_error", "Ocurrió un error inesperado."),

    /** Alguna dependencia no está disponible. */
    SERVICE_UNAVAILABLE("service_unavailable", "El servicio no está disponible en este momento.");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}

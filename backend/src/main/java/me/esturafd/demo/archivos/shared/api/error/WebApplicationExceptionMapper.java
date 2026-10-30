package me.esturafd.demo.archivos.shared.api.error;

import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import me.esturafd.demo.archivos.shared.application.error.ErrorCode;
import me.esturafd.demo.archivos.shared.application.request.RequestContext;

/**
 * Normaliza las respuestas de error que genera el propio servidor.
 *
 * <p>Sin este mapper, excepciones como «método no permitido» o «ruta no encontrada» devolverían
 * un cuerpo distinto al resto de la API.
 *
 * <p>Se conservan todas las cabeceras de la respuesta original —por ejemplo {@code Allow} o
 * {@code WWW-Authenticate}— y solo se sustituye el cuerpo.
 */
@Provider
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {

    @Inject
    RequestContext context;

    @Override
    public Response toResponse(WebApplicationException exception) {
        Response original = exception.getResponse();
        int statusCode = original.getStatus();
        ErrorCode errorCode = ErrorStatus.fromStatus(statusCode);

        Response.ResponseBuilder builder = Response.status(statusCode)
            .entity(new ApiError(errorCode.code(), errorCode.defaultMessage(), context.requestId()))
            .type(MediaType.APPLICATION_JSON);

        original.getHeaders().forEach((name, values) -> {
            if (!HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
                values.forEach(value -> builder.header(name, value));
            }
        });

        return builder.build();
    }
}

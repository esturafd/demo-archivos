package me.esturafd.demo.archivos.shared.error.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import me.esturafd.demo.archivos.shared.error.application.ApiException;
import me.esturafd.demo.archivos.shared.request.application.RequestContext;

/**
 * Traduce las excepciones controladas a respuestas HTTP.
 *
 * <p>Es el caso esperado: la operación detectó el fallo y decidió qué código corresponde.
 */
@Provider
public class ApiExceptionMapper implements ExceptionMapper<ApiException> {

    @Inject
    RequestContext context;

    @Override
    public Response toResponse(ApiException exception) {
        return ErrorResponse.of(
            ErrorStatus.of(exception.errorCode()),
            exception.errorCode().code(),
            exception.getMessage(),
            context.requestId()
        );
    }
}

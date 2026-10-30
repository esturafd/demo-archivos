package me.esturafd.demo.archivos.shared.api.error;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import me.esturafd.demo.archivos.shared.application.error.ErrorCode;
import me.esturafd.demo.archivos.shared.application.request.RequestContext;
import org.jboss.logging.Logger;

/**
 * Traduce los fallos de validación de la entrada.
 *
 * <p>La respuesta es deliberadamente genérica: no se devuelven los nombres de los campos ni los
 * mensajes del validador. El detalle queda en los registros, porque revela la forma interna de
 * los datos.
 */
@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    private static final Logger LOG = Logger.getLogger(ValidationExceptionMapper.class);

    @Inject
    RequestContext context;

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        if (LOG.isDebugEnabled()) {
            exception.getConstraintViolations().forEach(violation ->
                LOG.debugf(
                    "Validación fallida en %s [requestId=%s]",
                    violation.getPropertyPath(),
                    context.requestId()
                )
            );
        }

        return ErrorResponse.of(ErrorCode.VALIDATION_ERROR, context.requestId());
    }
}

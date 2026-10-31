package me.esturafd.demo.archivos.shared.error.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import me.esturafd.demo.archivos.shared.error.application.ErrorCode;
import me.esturafd.demo.archivos.shared.request.application.RequestContext;
import org.jboss.logging.Logger;

/**
 * Última barrera ante un fallo no previsto.
 *
 * <p>El cliente recibe siempre el mismo mensaje genérico: el mensaje de la excepción podría
 * revelar detalles internos, rutas o consultas. El detalle completo queda en los registros,
 * junto al identificador de petición, para poder investigarlo.
 */
@Provider
public class UnexpectedExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(UnexpectedExceptionMapper.class);

    @Inject
    RequestContext context;

    @Override
    public Response toResponse(Throwable exception) {
        LOG.errorf(exception, "Error no controlado [requestId=%s]", context.requestId());
        return ErrorResponse.of(ErrorCode.INTERNAL_ERROR, context.requestId());
    }
}

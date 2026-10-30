package me.esturafd.demo.archivos.shared.api.request;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;
import me.esturafd.demo.archivos.shared.application.request.RequestContext;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Asigna un identificador a cada petición, lo publica y lo limpia al terminar.
 *
 * <p>Va marcado con {@code @PreMatching} para que se ejecute incluso cuando la ruta no existe:
 * así toda petición queda correlacionada en los registros.
 *
 * <p>Si el cliente envía un identificador se reutiliza, siempre que tenga un formato aceptable.
 * Un valor arbitrario se descarta para no inyectar contenido en los registros.
 */
@PreMatching
@Provider
public class RequestIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String HEADER = "X-Request-Id";

    /** Clave del contexto de diagnóstico incluido en cada línea de registro. */
    public static final String MDC_KEY = "requestId";

    private static final Pattern ACCEPTED = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private static final Logger LOG = Logger.getLogger(RequestIdFilter.class);

    @Inject
    RequestContext context;

    @Override
    public void filter(ContainerRequestContext request) {
        String provided = request.getHeaderString(HEADER);
        String requestId = provided != null && ACCEPTED.matcher(provided).matches()
            ? provided
            : UUID.randomUUID().toString();

        context.requestId(requestId);
        MDC.put(MDC_KEY, requestId);

        LOG.debugf("%s %s [requestId=%s]", request.getMethod(), request.getUriInfo().getPath(), requestId);
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        String requestId = context.requestId();
        if (requestId != null) {
            response.getHeaders().putSingle(HEADER, requestId);
        }
        // No dejar el valor en el hilo: el pool reutiliza hilos entre peticiones.
        MDC.remove(MDC_KEY);
    }
}

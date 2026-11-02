package me.esturafd.demo.archivos.shared.request.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;
import me.esturafd.demo.archivos.shared.request.application.RequestContext;
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

    /**
     * Solo se aceptan literales de IP: cuatro octetos o una cadena hexadecimal
     * con dos puntos. Así un valor arbitrario de la cabecera no provoca una
     * resolución de nombres ni un error al fijar el contexto.
     */
    private static final Pattern IP_LITERAL =
        Pattern.compile("^([0-9]{1,3}(\\.[0-9]{1,3}){3}|[0-9A-Fa-f:]{2,45})$");

    private static final String DEFAULT_IP = "127.0.0.1";

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
        context.clientIp(resolveClientIp(request));
        MDC.put(MDC_KEY, requestId);

        LOG.debugf("%s %s [requestId=%s]", request.getMethod(), request.getUriInfo().getPath(), requestId);
    }

    /**
     * IP de origen. En Cloud Run la real es el primer valor de
     * {@code X-Forwarded-For}; si no hay cabecera utilizable se usa un valor
     * local, para que el contexto nunca quede sin IP.
     */
    private static String resolveClientIp(ContainerRequestContext request) {
        String forwarded = request.getHeaderString("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (IP_LITERAL.matcher(first).matches()) {
                return first;
            }
        }
        String realIp = request.getHeaderString("X-Real-IP");
        if (realIp != null && IP_LITERAL.matcher(realIp.trim()).matches()) {
            return realIp.trim();
        }
        return DEFAULT_IP;
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

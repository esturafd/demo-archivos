package me.esturafd.demo.archivos.shared.security.api;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.util.Locale;

/**
 * Añade las cabeceras de endurecimiento a todas las respuestas.
 *
 * <p>Se hace en un filtro de respuesta y no con propiedades de configuración
 * porque algunas cabeceras son **condicionales**: {@code Cache-Control} solo en
 * las respuestas de la API, y {@code Strict-Transport-Security} solo cuando la
 * petición llegó por HTTPS. Vive en {@code shared} porque vale para todos los
 * recursos sin excepción.
 *
 * <p>{@code Strict-Transport-Security} detrás de un proxy (Cloud Run) se detecta
 * por {@code X-Forwarded-Proto}, que es lo que el balanceador pone al terminar el
 * TLS; el servidor de la aplicación ve HTTP.
 */
@Provider
public class SecurityHeadersFilter implements ContainerResponseFilter {

    static final String CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    static final String FRAME_OPTIONS = "X-Frame-Options";
    static final String REFERRER_POLICY = "Referrer-Policy";
    static final String CONTENT_SECURITY_POLICY = "Content-Security-Policy";
    static final String CACHE_CONTROL = "Cache-Control";
    static final String STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security";

    /** Prefijo de las rutas de negocio: sus respuestas nunca se deben cachear. */
    private static final String API_PREFIX = "api/";

    private static final String NO_STORE = "no-store";
    private static final String HSTS_VALUE = "max-age=31536000; includeSubDomains";

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        // La API solo devuelve datos; no sirve HTML. Se niega todo origen.
        response.getHeaders().putSingle(CONTENT_TYPE_OPTIONS, "nosniff");
        response.getHeaders().putSingle(FRAME_OPTIONS, "DENY");
        response.getHeaders().putSingle(REFERRER_POLICY, "no-referrer");
        response.getHeaders().putSingle(CONTENT_SECURITY_POLICY, "default-src 'none'");

        // Los datos de la API (sesión, listados, archivos) no deben quedar en
        // cachés intermedias. Las rutas de infraestructura no entran aquí.
        if (isBusinessPath(request.getUriInfo().getPath())) {
            response.getHeaders().putSingle(CACHE_CONTROL, NO_STORE);
        }

        // HSTS solo tiene sentido si el transporte fue seguro; emitirlo por HTTP
        // no aporta y complicaría el desarrollo en local.
        if (isSecure(request)) {
            response.getHeaders().putSingle(STRICT_TRANSPORT_SECURITY, HSTS_VALUE);
        }
    }

    private static boolean isSecure(ContainerRequestContext request) {
        if (request.getSecurityContext().isSecure()) {
            return true;
        }
        String forwardedProto = request.getHeaderString("X-Forwarded-Proto");
        return forwardedProto != null
            && forwardedProto.toLowerCase(Locale.ROOT).startsWith("https");
    }

    /**
     * Determina si la ruta pertenece a la API de negocio.
     *
     * <p>Se normaliza la barra inicial porque {@code UriInfo#getPath()} la omite
     * en unas versiones y no en otras.
     */
    static boolean isBusinessPath(String path) {
        if (path == null) {
            return false;
        }
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        return normalized.startsWith(API_PREFIX);
    }
}

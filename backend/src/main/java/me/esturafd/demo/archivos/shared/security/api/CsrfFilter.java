package me.esturafd.demo.archivos.shared.security.api;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;
import me.esturafd.demo.archivos.shared.error.application.ApiException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

/**
 * Protección CSRF por el patrón de doble envío.
 *
 * <p>La autenticación es por cookie, así que un sitio externo podría provocar
 * peticiones con la sesión del usuario. Para evitarlo, el cliente recibe una
 * cookie {@code csrf-token} —legible por JavaScript— y debe reenviar su valor en
 * la cabecera {@code X-CSRF-TOKEN}. Un atacante puede provocar la petición, pero
 * no puede leer la cookie para copiar el valor en la cabecera.
 *
 * <p>Se implementa aquí, y no con una extensión, para que el rechazo use el
 * mismo cuerpo de error uniforme que el resto de la API.
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class CsrfFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final int TOKEN_BYTES = 32;

    /** Métodos que no modifican estado y, por tanto, no exigen token. */
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    /**
     * Generador seguro, creado en la primera llamada.
     *
     * <p>No se inicializa en el arranque de la clase para no incrustar un
     * {@code SecureRandom} en el montón de la imagen nativa.
     */
    private static volatile SecureRandom random;

    @ConfigProperty(name = "app.auth.csrf.cookie-name", defaultValue = "csrf-token")
    String cookieName;

    @ConfigProperty(name = "app.auth.csrf.header-name", defaultValue = "X-CSRF-TOKEN")
    String headerName;

    @ConfigProperty(name = "app.auth.cookie.secure", defaultValue = "true")
    boolean secure;

    @Override
    public void filter(ContainerRequestContext request) {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return;
        }

        Cookie cookie = request.getCookies().get(cookieName);
        String header = request.getHeaderString(headerName);
        if (cookie == null || cookie.getValue() == null || header == null
            || !constantTimeEquals(cookie.getValue(), header)) {
            throw ApiException.badRequest("No se pudo verificar el token CSRF.");
        }
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        // El cliente necesita la cookie antes de su primera petición que modifica
        // estado; se entrega en la primera respuesta que no la traiga.
        if (request.getCookies().containsKey(cookieName)) {
            return;
        }
        response.getHeaders().add(HttpHeaders.SET_COOKIE, newCookie());
    }

    private String newCookie() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        // Sin HttpOnly: el cliente debe poder leerla para reenviarla en la
        // cabecera. No es un secreto de sesión, solo un valor anti-falsificación.
        StringBuilder cookie = new StringBuilder()
            .append(cookieName).append('=').append(token)
            .append("; Path=/; SameSite=Lax");
        if (secure) {
            cookie.append("; Secure");
        }
        return cookie.toString();
    }

    private static boolean constantTimeEquals(String first, String second) {
        return MessageDigest.isEqual(
            first.getBytes(StandardCharsets.UTF_8),
            second.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static SecureRandom random() {
        SecureRandom current = random;
        if (current == null) {
            synchronized (CsrfFilter.class) {
                current = random;
                if (current == null) {
                    current = new SecureRandom();
                    random = current;
                }
            }
        }
        return current;
    }
}

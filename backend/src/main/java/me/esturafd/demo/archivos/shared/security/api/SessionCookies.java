package me.esturafd.demo.archivos.shared.security.api;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;

/**
 * Construye las cabeceras {@code Set-Cookie} de la sesión.
 *
 * <p>Se escriben a mano y no con {@code NewCookie} porque se necesita controlar
 * atributos como {@code SameSite}, que no forma parte del modelo de Jakarta REST
 * en esta versión.
 */
@ApplicationScoped
public class SessionCookies {

    @ConfigProperty(name = "app.auth.cookie.name", defaultValue = "session")
    String cookieName;

    @ConfigProperty(name = "app.auth.session.ttl")
    Duration ttl;

    public String name() {
        return cookieName;
    }

    public Duration ttl() {
        return ttl;
    }

    /** Cabecera que entrega el token al cliente. */
    public String create(String token) {
        return base(token) + "; Max-Age=" + ttl.toSeconds() + secureSuffix();
    }

    /** Cabecera que invalida la cookie en el cliente. */
    public String clear() {
        return base("") + "; Max-Age=0" + secureSuffix();
    }

    private String base(String value) {
        return cookieName + "=" + value + "; Path=/; HttpOnly; SameSite=Lax";
    }

    private String secureSuffix() {
        // En HTTPS, Secure. Se desactiva para desarrollo sobre HTTP. Se lee en
        // cada uso porque en la imagen nativa el valor de un campo se fija al
        // compilar.
        boolean secure = ConfigProvider.getConfig()
            .getOptionalValue("app.auth.cookie.secure", Boolean.class)
            .orElse(true);
        return secure ? "; Secure" : "";
    }
}

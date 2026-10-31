package me.esturafd.demo.archivos.auth.application;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Construye la clave con la que se indexan los intentos de inicio de sesión.
 *
 * <p>Es un HMAC de correo e IP, de modo que en {@code app.login_rate_limits} no
 * quede ningún dato personal en claro. La clave secreta se toma de la
 * configuración.
 */
@ApplicationScoped
public class LoginAttemptKey {

    @ConfigProperty(name = "app.auth.rate-limit.key")
    String secret;

    public String of(String email, String ipAddress) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String material = email.toLowerCase() + "|" + ipAddress;
            byte[] digest = mac.doFinal(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception failure) {
            // Sin la clave no se puede limitar; mejor fallar que dejar pasar.
            throw new IllegalStateException("No se pudo calcular la clave del limitador", failure);
        }
    }
}

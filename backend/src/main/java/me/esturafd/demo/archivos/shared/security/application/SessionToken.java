package me.esturafd.demo.archivos.shared.security.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generación y resumen de tokens de sesión.
 *
 * <p>El token en claro solo existe en la cookie del cliente. En la base de datos
 * se guarda su hash SHA-256, que es lo que se busca en cada petición. Así, una
 * lectura indebida de la tabla de sesiones no permite reutilizar ninguna.
 */
public final class SessionToken {

    /** 256 bits de entropía: suficiente para que el token sea impredecible. */
    private static final int TOKEN_BYTES = 32;

    /**
     * Generador seguro, creado en la primera llamada.
     *
     * <p>No se inicializa en el arranque de la clase porque en la imagen nativa un
     * {@code SecureRandom} en un campo estático quedaría incrustado en el montón
     * de la imagen con su semilla. Creándolo en tiempo de ejecución se evita.
     */
    private static volatile SecureRandom random;

    private SessionToken() {
    }

    /** Token opaco, seguro para una URL y una cookie. */
    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static SecureRandom random() {
        SecureRandom current = random;
        if (current == null) {
            synchronized (SessionToken.class) {
                current = random;
                if (current == null) {
                    current = new SecureRandom();
                    random = current;
                }
            }
        }
        return current;
    }

    /** Resumen SHA-256 en hexadecimal, tal como lo exige {@code app.sessions}. */
    public static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-256 no disponible", missing);
        }
    }
}

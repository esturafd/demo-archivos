package me.esturafd.demo.archivos.files.domain;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Resumen del contenido de un archivo.
 *
 * <p>Se guarda el SHA-256 en {@code app.files.sha256_hex} para dejar constancia
 * de qué contenido se registró, sin exponerlo.
 */
public final class ContentDigest {

    private ContentDigest() {
    }

    public static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-256 no disponible", missing);
        }
    }
}

package me.esturafd.demo.archivos.files.domain;

import java.util.Optional;
import java.util.Set;

/**
 * Reglas puras del contenido de un archivo: qué tipos se admiten, cómo se
 * reconoce el tipo real por su firma y cuándo el contenido no coincide con el
 * declarado.
 *
 * <p>El límite de tamaño coincide con el {@code CHECK} de {@code app.files}, que
 * es la última barrera; aquí se rechaza antes de llegar a la base.
 */
public final class FileContentPolicy {

    /** 20 MB, en bytes. Igual que el CHECK de app.files. */
    public static final long MAX_BYTES = 20_971_520L;

    /**
     * Tipos permitidos. La elección es del proyecto y se documenta en el plan;
     * puede crecer sin tocar nada más que esta lista y el reconocimiento de
     * firmas.
     */
    private static final Set<String> ALLOWED = Set.of(
        "application/pdf",
        "image/png",
        "image/jpeg",
        "image/gif",
        "image/webp",
        "text/plain",
        "text/csv"
    );

    /** Motivo por el que un contenido no se admite. */
    public enum Violation {
        EMPTY,
        TOO_LARGE,
        TYPE_NOT_ALLOWED,
        TYPE_MISMATCH
    }

    private FileContentPolicy() {
    }

    public static Set<String> allowed() {
        return ALLOWED;
    }

    /**
     * Comprueba tamaño y coherencia entre el tipo declarado y la firma real.
     *
     * @return el motivo del rechazo, o vacío si el contenido es aceptable
     */
    public static Optional<Violation> validate(String declaredType, long size, byte[] content) {
        if (size <= 0) {
            return Optional.of(Violation.EMPTY);
        }
        if (size > MAX_BYTES) {
            return Optional.of(Violation.TOO_LARGE);
        }

        String declared = normalize(declaredType);
        if (!ALLOWED.contains(declared)) {
            return Optional.of(Violation.TYPE_NOT_ALLOWED);
        }

        String detected = detect(content);
        if (detected != null) {
            return detected.equals(declared)
                ? Optional.empty()
                : Optional.of(Violation.TYPE_MISMATCH);
        }

        // Sin firma reconocible: solo se admite si el tipo declarado es de texto.
        return declared.startsWith("text/")
            ? Optional.empty()
            : Optional.of(Violation.TYPE_MISMATCH);
    }

    /** Tipo real reconocido por la firma del contenido, o {@code null} si no se reconoce. */
    public static String detect(byte[] content) {
        if (matches(content, 0x25, 0x50, 0x44, 0x46)) {
            return "application/pdf";
        }
        if (matches(content, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (matches(content, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (matches(content, 'G', 'I', 'F', '8')) {
            return "image/gif";
        }
        if (matches(content, 'R', 'I', 'F', 'F') && content.length >= 12
            && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    /** Normaliza un tipo MIME: sin parámetros y en minúsculas. */
    public static String normalize(String type) {
        if (type == null) {
            return "";
        }
        int separator = type.indexOf(';');
        String base = separator >= 0 ? type.substring(0, separator) : type;
        return base.trim().toLowerCase();
    }

    private static boolean matches(byte[] data, int... signature) {
        if (data.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if ((data[index] & 0xFF) != signature[index]) {
                return false;
            }
        }
        return true;
    }
}

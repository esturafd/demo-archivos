package me.esturafd.demo.archivos.files.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.files.application.model.NewFile;
import me.esturafd.demo.archivos.files.application.model.StoredFile;
import me.esturafd.demo.archivos.files.domain.ContentDigest;
import me.esturafd.demo.archivos.files.domain.FileContentPolicy;
import me.esturafd.demo.archivos.shared.error.application.ApiException;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;
import org.jboss.logging.Logger;

import java.util.Optional;
import java.util.UUID;

/**
 * Caso de uso de carga de un archivo.
 *
 * <p>Valida el contenido, lo guarda y registra sus metadatos. Como la base y el
 * almacenamiento no comparten transacción, si el registro falla **compensa**
 * borrando el objeto ya subido.
 */
@ApplicationScoped
public class UploadFileUseCase {

    private static final Logger LOG = Logger.getLogger(UploadFileUseCase.class);

    /** Prefijo de las claves de objeto; el resto es el UUID del archivo. */
    private static final String STORAGE_PREFIX = "objects/";

    @Inject
    FilesDataPort data;

    @Inject
    FilesStoragePort storage;

    public StoredFile upload(
        SessionUser actor,
        String originalName,
        String declaredType,
        byte[] content,
        String requestId,
        String ipAddress
    ) {
        Optional<FileContentPolicy.Violation> violation =
            FileContentPolicy.validate(declaredType, content.length, content);
        if (violation.isPresent()) {
            throw rejection(violation.get());
        }

        String mimeType = FileContentPolicy.normalize(declaredType);
        UUID fileId = UUID.randomUUID();
        String storageKey = STORAGE_PREFIX + fileId;

        storage.store(storageKey, content, mimeType);

        NewFile file = new NewFile(
            fileId,
            actor.id(),
            actor.email(),
            areaFor(actor),
            storageKey,
            originalName,
            mimeType,
            content.length,
            ContentDigest.sha256Hex(content)
        );

        try {
            return data.insertFile(file, requestId, ipAddress);
        } catch (RuntimeException failure) {
            compensate(storageKey, failure);
            throw failure;
        }
    }

    /** El área la decide el servidor: la propia para estándar y jefe de área, ninguna para gerente y admin. */
    private static UUID areaFor(SessionUser actor) {
        return switch (actor.role()) {
            case "standard", "area_head" -> actor.areaId();
            default -> null;
        };
    }

    private static ApiException rejection(FileContentPolicy.Violation violation) {
        return switch (violation) {
            case EMPTY -> ApiException.badRequest("El archivo está vacío.");
            case TOO_LARGE -> ApiException.payloadTooLarge();
            case TYPE_NOT_ALLOWED -> ApiException.unsupportedMediaType();
            case TYPE_MISMATCH ->
                ApiException.unprocessableContent("El contenido no coincide con el tipo declarado.");
        };
    }

    /**
     * Compensación: borra el objeto recién subido si falla el registro. Si el
     * borrado también falla, se registra y el objeto queda huérfano; no se
     * enmascara el error original.
     */
    private void compensate(String storageKey, RuntimeException failure) {
        try {
            storage.delete(storageKey);
        } catch (RuntimeException compensationFailure) {
            LOG.errorf(compensationFailure,
                "No se pudo compensar el objeto %s tras fallar el registro; queda huérfano", storageKey);
            failure.addSuppressed(compensationFailure);
        }
    }
}

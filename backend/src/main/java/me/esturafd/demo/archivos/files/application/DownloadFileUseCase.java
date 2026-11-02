package me.esturafd.demo.archivos.files.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.files.application.model.AccessIntent;
import me.esturafd.demo.archivos.files.application.model.DownloadedFile;
import me.esturafd.demo.archivos.files.application.model.FileForDownload;
import me.esturafd.demo.archivos.shared.error.application.ApiException;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;

import java.util.Optional;
import java.util.UUID;

/**
 * Caso de uso de la descarga.
 *
 * <p>Autoriza y registra la descarga; si el archivo no existe o queda fuera de
 * alcance, registra el intento como acceso denegado y responde igual: {@code 404}.
 */
@ApplicationScoped
public class DownloadFileUseCase {

    @Inject
    FilesDataPort data;

    @Inject
    FilesStoragePort storage;

    public DownloadedFile download(
        UUID fileId,
        SessionUser actor,
        String requestId,
        String ipAddress
    ) {
        Optional<FileForDownload> found =
            data.openForDownload(fileId, actor.id(), actor.email(), requestId, ipAddress);

        if (found.isEmpty()) {
            data.recordAccessDenied(fileId, actor.id(), AccessIntent.DOWNLOAD, actor.email(), requestId, ipAddress);
            throw ApiException.notFound();
        }

        FileForDownload file = found.get();
        return new DownloadedFile(
            file.originalName(),
            file.mimeType(),
            file.sizeBytes(),
            storage.retrieve(file.storageKey())
        );
    }
}

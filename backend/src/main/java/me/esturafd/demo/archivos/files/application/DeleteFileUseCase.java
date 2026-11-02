package me.esturafd.demo.archivos.files.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.files.application.model.AccessIntent;
import me.esturafd.demo.archivos.shared.error.application.ApiException;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;

import java.util.UUID;

/**
 * Caso de uso de la eliminación lógica.
 *
 * <p>La autorización la aplica {@code app.soft_delete_file}; si devuelve
 * {@code false}, se registra el intento como acceso denegado y se responde
 * {@code 404}, sin distinguir «no existe» de «fuera de alcance».
 */
@ApplicationScoped
public class DeleteFileUseCase {

    @Inject
    FilesDataPort data;

    public void delete(UUID fileId, SessionUser actor, String requestId, String ipAddress) {
        boolean deleted = data.softDeleteFile(fileId, actor.id(), actor.email(), requestId, ipAddress);
        if (!deleted) {
            data.recordAccessDenied(fileId, actor.id(), AccessIntent.DELETE, actor.email(), requestId, ipAddress);
            throw ApiException.notFound();
        }
    }
}

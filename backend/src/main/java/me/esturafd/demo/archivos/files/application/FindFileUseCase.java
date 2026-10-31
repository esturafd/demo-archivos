package me.esturafd.demo.archivos.files.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.files.application.model.StoredFile;
import me.esturafd.demo.archivos.shared.error.application.ApiException;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;

import java.util.UUID;

/**
 * Caso de uso de consulta de los metadatos visibles de un archivo.
 *
 * <p>Si no existe o queda fuera de alcance, responde {@code 404} sin
 * distinguirlos. No registra acceso denegado: el requisito solo lo exige para
 * descargar y eliminar.
 */
@ApplicationScoped
public class FindFileUseCase {

    @Inject
    FilesDataPort data;

    public StoredFile find(UUID fileId, SessionUser actor, String requestId, String ipAddress) {
        return data.findVisibleFile(fileId, actor.id(), requestId, ipAddress)
            .orElseThrow(ApiException::notFound);
    }
}

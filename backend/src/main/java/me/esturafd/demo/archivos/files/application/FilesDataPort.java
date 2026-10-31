package me.esturafd.demo.archivos.files.application;

import me.esturafd.demo.archivos.files.application.model.AccessIntent;
import me.esturafd.demo.archivos.files.application.model.FileForDownload;
import me.esturafd.demo.archivos.files.application.model.FileSearch;
import me.esturafd.demo.archivos.files.application.model.NewFile;
import me.esturafd.demo.archivos.files.application.model.StoredFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de datos del módulo `files`.
 *
 * <p>Declara solo lo que `files` necesita. El alcance no se filtra aquí: lo
 * aplican las políticas RLS de la base de datos, que dependen del usuario del
 * contexto. Por eso cada operación que lo necesita recibe el {@code actorId},
 * que el adaptador fija con {@code app.set_context}.
 */
public interface FilesDataPort {

    /**
     * Archivos visibles para el actor, con búsqueda y cursor. Devuelve hasta
     * {@code limit + 1} para saber si hay siguiente página.
     */
    List<StoredFile> listVisibleFiles(
        FileSearch search,
        UUID actorId,
        String requestId,
        String ipAddress
    );

    /** Metadatos visibles de un archivo, o vacío si no existe o queda fuera de alcance. */
    Optional<StoredFile> findVisibleFile(
        UUID fileId,
        UUID actorId,
        String requestId,
        String ipAddress
    );

    /**
     * Autoriza una descarga y la registra en el historial, en una única
     * transacción. Devuelve vacío si el archivo no existe o queda fuera de
     * alcance; en ese caso no registra nada (el acceso denegado lo registra el
     * caso de uso).
     */
    Optional<FileForDownload> openForDownload(
        UUID fileId,
        UUID actorId,
        String actorEmail,
        String requestId,
        String ipAddress
    );

    /** Inserta los metadatos y registra {@code file_uploaded} en una única transacción. */
    StoredFile insertFile(NewFile file, String requestId, String ipAddress);

    /**
     * Elimina lógicamente y, si lo consigue, registra {@code file_deleted} en la
     * misma transacción. Devuelve {@code false} si no está autorizado o no existe.
     */
    boolean softDeleteFile(
        UUID fileId,
        UUID actorId,
        String actorEmail,
        String requestId,
        String ipAddress
    );

    /** Registra un intento de acceso fuera de alcance. */
    void recordAccessDenied(
        UUID fileId,
        UUID actorId,
        AccessIntent intent,
        String actorEmail,
        String requestId,
        String ipAddress
    );
}

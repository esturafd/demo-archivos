package me.esturafd.demo.archivos.files.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.files.application.model.FileCursor;
import me.esturafd.demo.archivos.files.application.model.FilePage;
import me.esturafd.demo.archivos.files.application.model.FileSearch;
import me.esturafd.demo.archivos.files.application.model.StoredFile;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Caso de uso del listado y la búsqueda de archivos.
 *
 * <p>Pide una fila de más para saber si hay siguiente página y calcula el cursor
 * de continuación sin exponer su forma.
 */
@ApplicationScoped
public class ListFilesUseCase {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;

    @Inject
    FilesDataPort data;

    public FilePage list(
        SessionUser actor,
        String search,
        UUID ownerId,
        String cursor,
        Integer limit,
        String requestId,
        String ipAddress
    ) {
        int pageSize = pageSize(limit);
        Optional<FileCursor> position = FileCursor.decode(cursor);

        FileSearch query = new FileSearch(
            blankToNull(search),
            ownerId,
            position.map(FileCursor::uploadedAt).orElse(null),
            position.map(FileCursor::id).orElse(null),
            pageSize
        );

        List<StoredFile> rows = data.listVisibleFiles(query, actor.id(), requestId, ipAddress);
        boolean hasMore = rows.size() > pageSize;
        List<StoredFile> items = hasMore ? List.copyOf(rows.subList(0, pageSize)) : rows;

        String nextCursor = hasMore && !items.isEmpty()
            ? FileCursor.encode(
                items.get(items.size() - 1).uploadedAt(),
                items.get(items.size() - 1).id())
            : null;

        return new FilePage(items, nextCursor);
    }

    private static int pageSize(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}

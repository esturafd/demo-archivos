package me.esturafd.demo.archivos.files.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.esturafd.demo.archivos.files.application.model.FileOwner;
import me.esturafd.demo.archivos.shared.security.application.model.SessionUser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Caso de uso del listado de propietarios visibles.
 *
 * <p>Devuelve los usuarios cuyos archivos ve el actor y lo incluye **siempre a él
 * mismo**, aunque aún no tenga archivos: el selector de la interfaz apunta por
 * defecto al propio usuario.
 */
@ApplicationScoped
public class ListFileOwnersUseCase {

    @Inject
    FilesDataPort data;

    public List<FileOwner> list(SessionUser actor, String requestId, String ipAddress) {
        List<FileOwner> owners = new ArrayList<>(
            data.listVisibleOwners(actor.id(), requestId, ipAddress));

        boolean selfPresent = owners.stream().anyMatch(owner -> owner.id().equals(actor.id()));
        if (!selfPresent) {
            owners.add(new FileOwner(actor.id(), actor.email(), actor.name()));
        }

        owners.sort(Comparator
            .comparing(FileOwner::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(FileOwner::email, String.CASE_INSENSITIVE_ORDER));

        return owners;
    }
}

package me.esturafd.demo.archivos.files.api.dto;

import me.esturafd.demo.archivos.files.application.model.FileOwner;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Vista pública de un propietario visible, para el selector de usuario.
 */
@Schema(description = "Propietario cuyos archivos puede ver el usuario actual.")
public record OwnerView(
    @Schema(description = "Identificador del usuario.",
        example = "3f14e45f-ea2e-4f5b-9b2a-1c2d3e4f5a6b")
    String id,
    @Schema(description = "Correo del usuario.", example = "maria@ejemplo.com")
    String email,
    @Schema(description = "Nombre del usuario, o ausente si no es visible.", example = "María Pérez")
    String name
) {

    public static OwnerView from(FileOwner owner) {
        return new OwnerView(owner.id().toString(), owner.email(), owner.name());
    }
}

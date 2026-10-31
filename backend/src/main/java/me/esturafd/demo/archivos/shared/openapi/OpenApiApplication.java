package me.esturafd.demo.archivos.shared.openapi;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeIn;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.security.SecuritySchemes;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Punto de definición del contrato OpenAPI.
 *
 * <p>Es una clase de {@link Application} vacía, solo para poder colgar de ella las
 * anotaciones globales: la información del documento, las etiquetas y el esquema de
 * seguridad. Quarkus no necesita una clase de {@code Application}, así que aquí no
 * registra nada; los recursos se descubren solos.
 *
 * <p>La autenticación no es {@code Bearer}: viaja en la cookie de sesión y, en las
 * operaciones que modifican estado, exige además el token CSRF. Por eso se declaran
 * dos esquemas de tipo clave de API en lugar de reutilizar el de portador.
 */
@OpenAPIDefinition(
    info = @Info(
        title = "API de gestión de archivos",
        version = "1.0.0",
        description = """
            Plataforma de gestión segura de archivos por usuarios, roles y áreas.

            La autenticación es por cookie de sesión. Toda operación que modifica
            estado exige, además, el token CSRF (patrón de doble envío): el valor de
            la cookie `csrf-token` reenviado en la cabecera `X-CSRF-TOKEN`.

            El alcance (qué archivos, usuarios, áreas e historial ve cada rol) lo
            aplica la base de datos con sus políticas, no solo el servidor.
            """
    ),
    tags = {
        @Tag(name = "Autenticación", description = "Inicio y cierre de sesión y sesiones activas."),
        @Tag(name = "Archivos", description = "Carga, consulta, descarga y eliminación de archivos."),
        @Tag(name = "Usuarios", description = "Administración de usuarios. Reservado al administrador."),
        @Tag(name = "Áreas", description = "Consulta de áreas; la escritura es del administrador."),
        @Tag(name = "Áreas gestionadas", description = "Áreas que gestiona un gerente."),
        @Tag(name = "Historial", description = "Consulta del historial de acciones.")
    },
    security = @SecurityRequirement(name = "sessionCookie")
)
@SecuritySchemes({
    @SecurityScheme(
        securitySchemeName = "sessionCookie",
        type = SecuritySchemeType.APIKEY,
        apiKeyName = "session",
        in = SecuritySchemeIn.COOKIE,
        description = """
            Cookie de sesión `HttpOnly` que emite el inicio de sesión. El token de
            sesión nunca aparece en el cuerpo de la respuesta.
            """
    ),
    @SecurityScheme(
        securitySchemeName = "csrfHeader",
        type = SecuritySchemeType.APIKEY,
        apiKeyName = "X-CSRF-TOKEN",
        in = SecuritySchemeIn.HEADER,
        description = """
            Token CSRF de doble envío. Debe coincidir con la cookie `csrf-token` y
            se exige en toda operación que modifica estado (POST, PUT, PATCH,
            DELETE). Un GET previo entrega la cookie.
            """
    )
})
public class OpenApiApplication extends Application {
}

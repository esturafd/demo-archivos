package me.esturafd.demo.archivos.shared.error.api;

import jakarta.inject.Inject;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import me.esturafd.demo.archivos.shared.error.application.ApiException;
import me.esturafd.demo.archivos.shared.request.api.RequestIdFilter;
import me.esturafd.demo.archivos.shared.request.application.RequestContext;
import org.jboss.logging.MDC;

/**
 * Recurso exclusivo de las pruebas.
 *
 * <p>Vive en el código de prueba a propósito: permite ejercitar el manejo de errores sin
 * añadir endpoints artificiales a la aplicación real. No se empaqueta con el binario.
 */
@Path("/probe")
public class ErrorProbeResource {

    @Inject
    RequestContext context;

    @GET
    @Path("/ok")
    @Produces(MediaType.TEXT_PLAIN)
    public String ok() {
        return "ok";
    }

    @GET
    @Path("/request-id")
    @Produces(MediaType.TEXT_PLAIN)
    public String requestId() {
        return context.requestId();
    }

    /** Devuelve el valor que los registros usan para correlacionar la petición. */
    @GET
    @Path("/mdc")
    @Produces(MediaType.TEXT_PLAIN)
    public String mdc() {
        return String.valueOf(MDC.get(RequestIdFilter.MDC_KEY));
    }

    @GET
    @Path("/bad-request")
    public String badRequest() {
        throw ApiException.badRequest(null);
    }

    @GET
    @Path("/unauthenticated")
    public String unauthenticated() {
        throw ApiException.unauthenticated();
    }

    @GET
    @Path("/forbidden")
    public String forbidden() {
        throw ApiException.forbidden();
    }

    @GET
    @Path("/not-found")
    public String notFound() {
        throw ApiException.notFound();
    }

    @GET
    @Path("/conflict")
    public String conflict() {
        throw ApiException.conflict("El correo ya está registrado.");
    }

    @GET
    @Path("/payload-too-large")
    public String payloadTooLarge() {
        throw ApiException.payloadTooLarge();
    }

    @GET
    @Path("/unsupported-media-type")
    public String unsupportedMediaType() {
        throw ApiException.unsupportedMediaType();
    }

    @GET
    @Path("/unprocessable")
    public String unprocessable() {
        throw ApiException.unprocessableContent(null);
    }

    @GET
    @Path("/too-many-requests")
    public String tooManyRequests() {
        throw ApiException.tooManyRequests();
    }

    @GET
    @Path("/service-unavailable")
    public String serviceUnavailable() {
        throw ApiException.serviceUnavailable(null);
    }

    @GET
    @Path("/boom")
    public String boom() {
        throw new IllegalStateException("detalle interno que no debe llegar al cliente");
    }

    @GET
    @Path("/validate")
    public String validate(@QueryParam("nombre") @Size(min = 3) String nombre) {
        return nombre;
    }
}

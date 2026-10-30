package me.esturafd.demo.archivos.shared.application.request;

import jakarta.enterprise.context.RequestScoped;

/**
 * Datos de la petición en curso.
 *
 * <p>Vive en la capa de aplicación, no en la de API, porque los casos de uso necesitan el
 * identificador de petición para la auditoría y para el contexto de autorización de la base de
 * datos.
 */
@RequestScoped
public class RequestContext {

    private String requestId;

    public String requestId() {
        return requestId;
    }

    /** Lo establece el filtro de la capa de API al inicio de cada petición. */
    public void requestId(String requestId) {
        this.requestId = requestId;
    }
}

package me.esturafd.demo.archivos.shared.request.application;

import jakarta.enterprise.context.RequestScoped;

/**
 * Datos de la petición en curso.
 *
 * <p>Vive en la capa de aplicación, no en la de API, porque los casos de uso
 * necesitan el identificador de petición para la auditoría y el contexto de
 * autorización de la base de datos, y la IP para el historial y el límite de
 * intentos.
 */
@RequestScoped
public class RequestContext {

    private String requestId;
    private String clientIp;

    public String requestId() {
        return requestId;
    }

    public String clientIp() {
        return clientIp;
    }

    /** Lo establece el filtro de la capa de API al inicio de cada petición. */
    public void requestId(String requestId) {
        this.requestId = requestId;
    }

    /** Lo establece el filtro de la capa de API al inicio de cada petición. */
    public void clientIp(String clientIp) {
        this.clientIp = clientIp;
    }
}

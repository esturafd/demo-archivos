package me.esturafd.demo.archivos.shared.application.data;

/**
 * Único punto de acceso a la información persistente.
 *
 * <p>Es el mediador de datos: sabe cómo se guardan y se protegen los datos, y esconde el
 * proveedor concreto. El resto de la aplicación depende solo de esta interfaz, de modo que
 * cambiar de proveedor —o mover a otro sitio responsabilidades que hoy están en la base de
 * datos— no obligue a tocar los casos de uso.
 *
 * <p>La interfaz crece con las necesidades reales: por ahora solo la comprobación de
 * disponibilidad, que es lo que necesita el estado de salud.
 */
public interface DataMediator {

    /**
     * Comprueba que el proveedor responde.
     *
     * <p>No lanza excepciones: un fallo de conexión es precisamente la respuesta que se espera
     * evaluar.
     */
    boolean isAvailable();
}

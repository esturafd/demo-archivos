package me.esturafd.demo.archivos.shared.application.storage;

/**
 * Único punto de acceso a los objetos.
 *
 * <p>Es el mediador de almacenamiento: sabe dónde viven los bytes y con qué credenciales se
 * accede, y esconde el proveedor concreto. La clave de un objeto es un identificador opaco para
 * el resto del sistema.
 *
 * <p>La interfaz crece con las necesidades reales: por ahora solo la comprobación de
 * disponibilidad, que es lo que necesita el estado de salud.
 */
public interface StorageMediator {

    /**
     * Comprueba que el proveedor responde y que el bucket configurado es accesible.
     *
     * <p>No lanza excepciones: un fallo es precisamente la respuesta que se espera evaluar.
     */
    boolean isAvailable();
}

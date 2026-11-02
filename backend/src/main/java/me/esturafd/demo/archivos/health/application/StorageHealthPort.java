package me.esturafd.demo.archivos.health.application;

/**
 * Puerto que el estado de salud necesita del almacenamiento de objetos.
 *
 * <p>Es específico de `health`: solo pregunta si el almacenamiento responde. Los
 * módulos que suben y descargan objetos declaran su propio puerto con las
 * operaciones que necesitan.
 */
public interface StorageHealthPort {

    /** Comprueba que el proveedor responde y que el bucket es accesible. */
    boolean isAvailable();
}

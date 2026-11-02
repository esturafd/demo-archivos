package me.esturafd.demo.archivos.files.application;

import java.io.InputStream;

/**
 * Puerto de almacenamiento del módulo `files`.
 *
 * <p>Solo sabe guardar, leer y borrar objetos por su clave. La clave es un
 * identificador opaco: ningún otro componente interpreta su significado, y
 * nunca se expone al cliente.
 */
public interface FilesStoragePort {

    /** Guarda el objeto bajo la clave indicada. */
    void store(String key, byte[] content, String contentType);

    /** Devuelve el contenido del objeto; el llamante cierra el flujo. */
    InputStream retrieve(String key);

    /** Elimina el objeto. Se usa en la compensación de una carga fallida. */
    void delete(String key);
}

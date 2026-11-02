package me.esturafd.demo.archivos.shared.jdbc;

/**
 * Trabajo que se ejecuta sobre una conexión activa.
 *
 * <p>Recibe un {@link SqlExecutor} y no una conexión: así quien lo escribe no
 * maneja JDBC ni tiene que cerrar recursos.
 */
@FunctionalInterface
public interface SqlWork<T> {

    T run(SqlExecutor sql);
}

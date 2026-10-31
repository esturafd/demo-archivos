package me.esturafd.demo.archivos.shared.data.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Convierte la fila actual de un {@link ResultSet} en un objeto del dominio.
 *
 * <p>Es el único punto donde el adaptador lee columnas: el resto del código no
 * ve JDBC.
 */
@FunctionalInterface
public interface RowMapper<T> {

    T map(ResultSet rows) throws SQLException;
}

package me.esturafd.demo.archivos.shared.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Ejecuta sentencias sobre una conexión ya abierta.
 *
 * <p>Esconde el ciclo de vida de {@link PreparedStatement} y {@link ResultSet} y
 * el enlace de parámetros. La conexión y la transacción no las gestiona aquí,
 * sino quien la construye: el ejecutor solo trabaja dentro de una conexión
 * activa, de modo que el contexto de autorización y las consultas compartan
 * siempre la misma.
 *
 * <p>Los fallos de SQL se convierten en {@link DataAccessException}, para que el
 * código que compone las consultas no tenga que tratar excepciones marcadas.
 */
public final class SqlExecutor {

    private final Connection connection;

    public SqlExecutor(Connection connection) {
        this.connection = connection;
    }

    /** Ejecuta una sentencia que no devuelve filas (o cuyo resultado no interesa). */
    public void run(String sql, Object... params) {
        try (PreparedStatement statement = prepare(sql, params)) {
            statement.execute();
        } catch (SQLException failure) {
            throw new DataAccessException(failure);
        }
    }

    /**
     * Ejecuta una consulta y mapea la primera fila.
     *
     * <p>Devuelve vacío si no hay filas o si la fila mapea a {@code null}, que es
     * el caso de las funciones que devuelven {@code NULL}.
     */
    public <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        try (PreparedStatement statement = prepare(sql, params);
             ResultSet rows = statement.executeQuery()) {
            return rows.next() ? Optional.ofNullable(mapper.map(rows)) : Optional.empty();
        } catch (SQLException failure) {
            throw new DataAccessException(failure);
        }
    }

    /** Ejecuta una consulta y mapea todas las filas. */
    public <T> List<T> queryMany(String sql, RowMapper<T> mapper, Object... params) {
        try (PreparedStatement statement = prepare(sql, params);
             ResultSet rows = statement.executeQuery()) {
            List<T> results = new ArrayList<>();
            while (rows.next()) {
                results.add(mapper.map(rows));
            }
            return results;
        } catch (SQLException failure) {
            throw new DataAccessException(failure);
        }
    }

    private PreparedStatement prepare(String sql, Object... params) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        for (int index = 0; index < params.length; index++) {
            Object value = params[index];
            int position = index + 1;
            if (value == null) {
                // Los parámetros anulables llevan un cast explícito en el SQL
                // (?::text, ?::uuid...); sin él PostgreSQL no puede inferir el tipo.
                statement.setNull(position, Types.OTHER);
            } else {
                statement.setObject(position, value);
            }
        }
        return statement;
    }
}

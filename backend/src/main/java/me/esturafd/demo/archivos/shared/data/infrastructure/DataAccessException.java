package me.esturafd.demo.archivos.shared.data.infrastructure;

/**
 * Fallo al ejecutar una sentencia SQL.
 *
 * <p>Envuelve la {@code SQLException} original para no obligar a tratar
 * excepciones marcadas en el código que compone las consultas. El detalle queda
 * en la causa y se registra; nunca llega al cliente.
 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(Throwable cause) {
        super(cause.getMessage(), cause);
    }
}

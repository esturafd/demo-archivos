package me.esturafd.demo.archivos.auth.domain;

/**
 * Resultado posible de un intento de inicio de sesión.
 *
 * <p>Los tres casos de fallo comparten respuesta para el cliente; esta
 * distinción existe solo dentro de la aplicación.
 */
public enum LoginOutcome {

    /** Credenciales correctas y cuenta activa. */
    SUCCESS,

    /** Correo inexistente, contraseña incorrecta o cuenta desactivada. */
    INVALID_CREDENTIALS,

    /** Se superó el límite de intentos. */
    TOO_MANY_ATTEMPTS
}

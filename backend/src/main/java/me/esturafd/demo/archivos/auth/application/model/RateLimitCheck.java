package me.esturafd.demo.archivos.auth.application.model;

import me.esturafd.demo.archivos.auth.domain.RateLimitState;

import java.time.Instant;

/**
 * Estado del limitador de intentos junto con la hora con que se evaluó.
 *
 * <p>La hora la produce la base de datos en la misma consulta que lee el estado.
 * Así la ventana y su comparación viven en un único reloj —el de la base— y un
 * desfase entre el reloj del servidor de la aplicación y el de la base no cambia
 * la decisión.
 *
 * @param state     estado actual, o {@code null} si aún no hay intentos
 * @param checkedAt hora de la base con la que se evalúa la ventana
 */
public record RateLimitCheck(RateLimitState state, Instant checkedAt) {
}

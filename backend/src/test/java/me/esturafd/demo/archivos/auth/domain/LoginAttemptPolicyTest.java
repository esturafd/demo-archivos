package me.esturafd.demo.archivos.auth.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Reglas del límite de intentos, sin base de datos.
 *
 * <p>La política es pura: recibe el estado y la hora con la que evaluarlo. La hora
 * la aporta la base de datos, de modo que la ventana y su comparación comparten
 * un único reloj.
 */
class LoginAttemptPolicyTest {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Instant NOW = Instant.parse("2026-02-01T12:00:00Z");

    private final LoginAttemptPolicy policy = new LoginAttemptPolicy(MAX_ATTEMPTS, WINDOW);

    @Test
    void sinEstadoNoEstaBloqueado() {
        assertThat(policy.isBlocked(null, NOW), is(false));
    }

    @Test
    void seBloqueaAlAlcanzarElMaximoDentroDeLaVentana() {
        // Cinco intentos dentro de la ventana: el sexto queda bloqueado.
        RateLimitState state = new RateLimitState(
            MAX_ATTEMPTS, NOW.minus(Duration.ofMinutes(1)), null);
        assertThat(policy.isBlocked(state, NOW), is(true));
    }

    @Test
    void noSeBloqueaSiNoAlcanzaElMaximo() {
        RateLimitState state = new RateLimitState(
            MAX_ATTEMPTS - 1, NOW.minus(Duration.ofMinutes(1)), null);
        assertThat(policy.isBlocked(state, NOW), is(false));
    }

    @Test
    void laVentanaExpiradaReiniciaElContador() {
        // Los intentos son de hace más de la ventana: ya no cuentan.
        RateLimitState state = new RateLimitState(
            MAX_ATTEMPTS, NOW.minus(WINDOW).minusSeconds(1), null);
        assertThat(policy.isBlocked(state, NOW), is(false));
    }

    @Test
    void unBloqueoExplicitoVigenteMandaAunqueNoHayaIntentos() {
        RateLimitState state = new RateLimitState(1, NOW.minus(Duration.ofMinutes(1)), NOW.plusSeconds(60));
        assertThat(policy.isBlocked(state, NOW), is(true));
    }

    @Test
    void unBloqueoExplicitoVencidoNoBloquea() {
        RateLimitState state = new RateLimitState(1, NOW.minus(Duration.ofMinutes(1)), NOW.minusSeconds(60));
        assertThat(policy.isBlocked(state, NOW), is(false));
    }
}

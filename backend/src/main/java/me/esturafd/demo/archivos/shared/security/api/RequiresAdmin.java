package me.esturafd.demo.archivos.shared.security.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un recurso o un método reservado al rol administrador.
 *
 * <p>Lo comprueba {@link SessionFilter}: sin sesión responde {@code 401} y con
 * un rol distinto responde {@code 403}. La base de datos vuelve a comprobarlo
 * con sus políticas, como segunda barrera.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresAdmin {
}

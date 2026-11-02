package me.esturafd.demo.archivos.shared.security.api;

import me.esturafd.demo.archivos.shared.security.application.model.AppRole;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exige que el usuario tenga al menos el nivel del rol indicado.
 *
 * <p>Al expresarse por nivel y no por rol exacto, permite guardas como
 * {@code @RequiresRole(AppRole.MANAGER)} que valen para manager y para cualquier
 * rol superior, presente o futuro.
 *
 * <p>Lo comprueba {@link SessionFilter}: sin sesión responde {@code 401} y con un
 * nivel insuficiente responde {@code 403}. La base de datos vuelve a comprobarlo
 * con sus políticas, como segunda barrera.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresRole {

    AppRole value();
}

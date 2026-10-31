package me.esturafd.demo.archivos.shared.security.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un recurso o un método que exige una sesión válida.
 *
 * <p>Lo comprueba {@link SessionFilter}: sin sesión responde {@code 401}. La
 * decisión de <em>qué</em> puede hacer cada rol vive en la base de datos, no
 * aquí.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresSession {
}

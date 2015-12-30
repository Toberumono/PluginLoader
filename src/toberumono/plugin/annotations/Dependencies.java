package toberumono.plugin.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container for multiple {@link Dependency} annotations. This can either be used directly, or implicitly created by the JVM
 * at runtime by placing multiple {@link Dependency} annotations on a single class.
 * 
 * @author Toberumono
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Dependencies {
	/**
	 * Indicates the <em>containing annotation type</em> for the repeatable annotation type.
	 * 
	 * @return the containing annotation type
	 */
	Dependency[] value();
}

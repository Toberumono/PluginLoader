package toberumono.plugin.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation that, when placed on a plugin, indicates that the plugin depends on a plugin that matches the
 * {@link #id() ID} and {@link #version() version} given.
 * 
 * @author Toberumono
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(Dependencies.class)
public @interface Dependency {
	/**
	 * @return the {@code ID} of the {@code plugin} that satisfies the dependency
	 */
	String id();
	
	/**
	 * @return the specific version or range of versions of the {@code plugin} that satisfy the dependency
	 */
	String version() default "[any]";
	
	/**
	 * @return whether this dependency is required ({@code true}) or optional ({@code false})
	 */
	boolean required() default true;
}

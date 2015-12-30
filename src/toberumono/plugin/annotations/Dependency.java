package toberumono.plugin.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(Dependencies.class)
public @interface Dependency {
	/**
	 * @return the <tt>ID</tt> of the required <tt>plugin</tt>
	 */
	String id();
	
	/**
	 * @return the specific version or range of versions of the required <tt>plugin</tt> that are needed
	 */
	String version() default "[any]";
}

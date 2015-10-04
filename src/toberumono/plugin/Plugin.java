package toberumono.plugin;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;

/**
 * This annotation contains the basic information used to determine what classes in a package are plugins and how they should
 * be named for future referencing.
 * 
 * @author Joshua Lipstone
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {TYPE})
public @interface Plugin {
	/**
	 * A unique ID for the annotated <tt>plugin</tt>.<br>
	 * Generally, this ID should follow standard package naming conventions, in other words, the id should be
	 * <tt>package_name.class_name</tt>
	 * 
	 * @return the ID of the annotated plugin
	 */
	String id();
	
	/**
	 * @return the annotated <tt>plugin</tt>'s version
	 */
	String version();
	
	/**
	 * @return a description of the annotated <tt>plugin</tt>
	 */
	String description();
	
	/**
	 * @return the author of the annotated <tt>plugin</tt>
	 */
	String author();
}

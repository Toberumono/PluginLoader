package toberumono.plugin.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation contains the basic information used to determine what classes in a package are plugins and how they should
 * be named for future referencing.
 * 
 * @author Toberumono
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PluginDescription {
	/**
	 * A unique ID for the annotated {@code plugin}.<br>
	 * Generally, this ID should follow standard package naming conventions, in other words, the id should be
	 * {@code package_name.class_name}.<br>
	 * If this is a child plugin, using {@code [inherit].[classname]} is recommended.<br>
	 * This will default to calling {@link Class#getName()} on the plugin's raw class.
	 * 
	 * @return the ID of the annotated plugin
	 */
	String id() default "[generate]";
	
	/**
	 * @return the annotated {@code plugin's} version
	 */
	String version() default "[inherit|generate]";
	
	/**
	 * @return a description of the annotated {@code plugin}
	 */
	String description() default "[inherit|generate]";
	
	/**
	 * @return the author of the annotated {@code plugin}
	 */
	String author() default "[inherit|generate]";
	
	/**
	 * @return the parent of the annotated {@code plugin}
	 */
	String parent() default "[none]";
	
	/**
	 * @return the annotated {@code plugin's} children
	 */
	Class<?>[] children() default {};
	
	/**
	 * @return the type of the plugin
	 * @see PluginType
	 */
	PluginType type() default PluginType.STANDARD;
}

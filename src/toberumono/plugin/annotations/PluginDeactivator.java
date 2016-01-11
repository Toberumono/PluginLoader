package toberumono.plugin.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import toberumono.plugin.user.PluginUser;

/**
 * Methods marked with this annotation are called by the {@link PluginUser} when the plugin is being deactivated (the first
 * stage of the deinitialization process). These methods should be used to clean up fields and unregister plugin features.<br>
 * The arguments passed to methods marked with this annotation are entirely dependent on the {@link PluginUser}.<br>
 * Deactivators <i>must</i> be public, non-static methods.
 * 
 * @author Toberumono
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PluginDeactivator {
	/**
	 * This field can be used to control the order in which deactivators are called for a plugin.
	 * 
	 * @return the priority of the annotated method
	 */
	public int priority() default 0;
}

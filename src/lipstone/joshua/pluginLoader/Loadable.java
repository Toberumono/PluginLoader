package lipstone.joshua.pluginLoader;

/**
 * Represents a class that can be loaded by this library.
 * 
 * @author Joshua Lipstone
 */
public interface Loadable {
	
	default String getID() {
		return getClass().getAnnotation(Plugin.class).id();
	}
}

package lipstone.joshua.pluginLoader;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Map;

/**
 * This represents a {@link PluginUser}, and is effective with or without a {@link PluginManager}.
 * 
 * @param <T>
 *            the class that will be used for <tt>plugins</tt> in this system. All instances of <tt>T</tt> must be annotated
 *            with the {@link Plugin} annotation in order to be loaded.
 * @author Joshua Lipstone
 */
public interface PluginUser<T> {
	
	/**
	 * @return the location that this {@link PluginUser} is running from. This is <i>not</i> the default plugin location.
	 */
	public abstract Path getBaseLocation();
	
	/**
	 * Gets the default full path to the default plugin location. In this case it is found by:
	 * <code>getBaseLocation + "/plugins/"</code> on UNIX or <code>getBaseLocation + "\plugins\"</code>
	 * 
	 * @return the default plugin location for this {@link PluginUser}
	 */
	public default Path getDefaultPluginLocation() {
		return getBaseLocation().resolve("plugins" + FileSystems.getDefault().getSeparator());
	}
	
	/**
	 * Loads the specified plugin into this {@link PluginUser}
	 * <p>
	 * Upon successful loading, the plugin MUST be added to the plugins {@link java.util.HashMap HashMap}
	 * </p>
	 * 
	 * @param pluginClass
	 *            the plugin to load
	 * @throws PluginException
	 *             a catch-all exception thrown for all plugin-related errors
	 */
	public void loadPlugin(Class<? extends T> pluginClass) throws PluginException;
	
	/**
	 * Unloads the specified plugin from this {@link PluginUser}
	 * <p>
	 * Upon successful unloading, the plugin MUST be removed from the plugins HashMap
	 * </p>
	 * 
	 * @param pluginID
	 *            the ID of the plugin to unload
	 * @throws PluginException
	 *             a catch-all exception thrown for all plugin-related errors
	 */
	public void unloadPlugin(String pluginID) throws PluginException;
	
	/**
	 * Refreshes all of the plugins in this {@link PluginUser} by unloading them and then reloading them.
	 * 
	 * @throws PluginException
	 *             a catch-all exception thrown for all plugin-related errors
	 */
	public void reloadPlugins() throws PluginException;
	
	/**
	 * @return the plugins loaded by this {@link PluginUser}
	 */
	public Map<String, T> getPlugins();
}

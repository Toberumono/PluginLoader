package toberumono.plugin.user;

import toberumono.plugin.exceptions.PluginActivationException;
import toberumono.plugin.exceptions.PluginDeactivationException;
import toberumono.plugin.manager.PluginData;

/**
 * This interface details the core methods needed in order to implement a class that can use plugins as defined in this
 * library.
 * 
 * @author Toberumono
 * @param <T>
 *            the type of plugin being used
 */
public interface PluginUser<T> {

	public boolean activatePlugin(PluginData<T> plugin) throws PluginActivationException;

	public boolean deactivatePlugin(PluginData<T> plugin) throws PluginDeactivationException;
}

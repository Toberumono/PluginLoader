package toberumono.plugin.annotations;

import toberumono.plugin.manager.PluginManager;
/**
 * An enumeration of the possible types of plugins.
 * 
 * @author Toberumono
 */
public enum PluginType {
	/**
	 * Used for plugins with no special initialization instructions.
	 */
	STANDARD(true),
	/**
	 * Used for classes can satisfy dependencies for other plugins but should not themselves be initialized.
	 */
	LIBRARY(false);
	
	private final boolean shouldInitialize;
	
	PluginType(boolean shouldInitialize) {
		this.shouldInitialize = shouldInitialize;
	}
	
	/**
	 * @return {@code true} iff the plugin should be initialized by the appropriate {@link PluginManager}
	 */
	public boolean shouldInitialize() {
		return shouldInitialize;
	}
}

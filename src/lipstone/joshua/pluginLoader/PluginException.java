package lipstone.joshua.pluginLoader;

import java.util.Objects;

/**
 * @author Joshua Lipstone
 */
public class PluginException extends Exception {
	static final long serialVersionUID = 2L;
	
	/**
	 * The plugin that threw this exception
	 */
	private transient Object thrower;
	
	/**
	 * @param thrower
	 *            the plugin that threw this exception
	 */
	public PluginException(Object thrower) {
		super();
		this.thrower = thrower;
	}
	
	/**
	 * @param message
	 *            a message describing the error and/or its cause
	 * @param thrower
	 *            the plugin that threw this exception
	 */
	public PluginException(String message, Object thrower) {
		super(message);
		this.thrower = thrower;
	}
	
	/**
	 * @return the plugin that threw this error
	 */
	public Object getThrower() {
		return thrower;
	}
	
	/**
	 * Sets the plugin that threw this exception. If the plugin has already been set, this throws an
	 * {@link java.lang.IllegalStateException IllegalStateException}.
	 * 
	 * @param thrower
	 *            the plugin that threw this error
	 * @throws IllegalStateException
	 *             if the thrower has already been set
	 */
	public void setThrower(Object thrower) {
		if (this.thrower != null)
			throw new IllegalStateException("Can't overwrite thrower with " + Objects.toString(thrower, "a null plugin"), this);
		this.thrower = thrower;
	}
	
	@Override
	public String toString() {
		if (thrower != null)
			return getMessage() + "\nOccured in Plugin: " + thrower.getClass().getAnnotation(Plugin.class).id();
		return getMessage() + "\nOccured in an unknown plugin.";
	}
}

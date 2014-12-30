package lipstone.joshua.pluginLoader;

import java.util.Objects;

/**
 * @author Joshua Lipstone
 */
public class PluginException extends Exception {
	static final long serialVersionUID = 1L;
	
	/**
	 * Stores whether Exceptions should print a stackTrace automatically
	 */
	private static transient boolean printStack = false;
	
	/**
	 * The plugin that threw this exception
	 */
	private transient Loadable thrower;
	
	/**
	 * @param thrower
	 *            the plugin that threw this exception
	 */
	public PluginException(Loadable thrower) {
		super();
		this.thrower = thrower;
		if (printStack)
			printStackTrace();
	}
	
	/**
	 * @param message
	 *            a message describing the error and/or its cause
	 * @param thrower
	 *            the plugin that threw this exception
	 */
	public PluginException(String message, Loadable thrower) {
		super(message);
		this.thrower = thrower;
		if (printStack)
			printStackTrace();
	}
	
	/**
	 * @return the plugin that threw this error
	 */
	public Loadable getThrower() {
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
	public void setThrower(Loadable thrower) {
		if (this.thrower != null)
			throw new IllegalStateException("Can't overwrite thrower with " + Objects.toString(thrower, "a null plugin"), this);
		this.thrower = thrower;
	}
	
	/**
	 * Check the status of automatic stack trace printing
	 * 
	 * @return whether {@link PluginException PluginExceptions} will automatically print stack traces
	 */
	public static boolean isAutomaticallyPrintingStack() {
		return printStack;
	}
	
	/**
	 * Set whether {@link PluginException PluginExceptions} should automatically print stack traces
	 * 
	 * @param automaticallyPrintStack
	 *            whether {@link PluginException PluginExceptions} should automatically print stack traces
	 */
	public static void setAutomaticStackPrinting(boolean automaticallyPrintStack) {
		printStack = automaticallyPrintStack;
	}
	
	@Override
	public String toString() {
		if (thrower != null)
			return getMessage() + "\nOccured in Plugin: " + thrower.getID();
		return getMessage() + "\nOccured in an unknown plugin.";
	}
}

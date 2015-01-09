package lipstone.joshua.pluginLoader;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * This class provides advanced control over plugins in this system including methods for enabling plugins, disabling
 * plugins, and loading plugins from a location external to this program
 * 
 * @param <U>
 *            the class of the {@link PluginUser} that this {@link PluginManager} will manage
 * @param <T>
 *            the class that each plugin managed by this {@link PluginManager} will extend
 * @author Joshua Lipstone
 */
public class PluginManager<U extends PluginUser<T>, T> {
	public final U pluginUser;
	public static final String PATH_SEPARATOR = System.getProperty("file.separator"), PATH_SEPARATORS = "[\\Q/\\\\E]";
	private HashMap<String, Class<T>> plugins;
	private HashMap<String, Boolean> enabled;
	private ArrayList<Path> pluginLocations;
	private final Loader loader;
	private final ArrayList<JarFile> jars;
	
	/**
	 * Constructs a new {@link PluginManager} for the specified {@link PluginUser} and loads the plugins from the
	 * {@link PluginUser PluginUser's} default plugin directory.<br>
	 * The default plugin directory is determined by:<br>
	 * {@link PluginUser#getBaseLocation()} + &quot;/plugins/&quot;
	 * 
	 * @param pluginUser
	 *            the {@link PluginUser} to be linked with this {@link PluginManager}
	 * @see #PluginManager(PluginUser, boolean) PluginController(pluginUser, true)
	 */
	public PluginManager(U pluginUser) {
		this(pluginUser, true);
	}
	
	/**
	 * Constructs a new {@link PluginManager} for the specified {@link PluginUser} and loads the plugins from the
	 * {@link PluginUser PluginUser's} default plugin directory if loadDefaultPluginDirectory is {@code true}<br>
	 * The default plugin directory is determined by:<br>
	 * {@link PluginUser#getBaseLocation()} + &quot;/plugins/&quot;
	 * 
	 * @param pluginUser
	 *            the {@link PluginUser} to be linked with this {@link PluginManager}
	 * @param loadDefaultPluginDirectory
	 *            whether to load plugins from the {@link PluginUser PluginUser's} default plugin directory
	 */
	public PluginManager(U pluginUser, boolean loadDefaultPluginDirectory) {
		this.pluginUser = pluginUser;
		addSystemPackage(pluginUser.getClass().getPackage().getName());
		plugins = new HashMap<>();
		enabled = new HashMap<>();
		pluginLocations = new ArrayList<>();
		loader = new Loader();
		jars = new ArrayList<>();
		Runtime.getRuntime().addShutdownHook(new Thread() { //Close the classloader when we're done
			@Override
			public void run() {
				try {
					loader.close();
				}
				catch (IOException e) {
					e.printStackTrace();
				}
				for (JarFile jar : jars)
					try {
						jar.close();
					}
					catch (IOException e) {
						e.printStackTrace();
					}
			}
		});
		if (loadDefaultPluginDirectory)
			loadPlugins(pluginUser.getDefaultPluginLocation());
	}
	
	//Loads all classes that are annotated with the Plugin annotation from the given .jar file
	private final void loadClassesFromJar(JarFile jar) {
		for (JarEntry entry : Collections.list(jar.entries())) {
			if (entry.getName().endsWith(".class")) {
				String className = entry.getName().replaceAll(FileSystems.getDefault().getSeparator(), ".");
				className = className.substring(0, className.lastIndexOf(".class"));
				try {
					Class<?> possiblePlugin = loader.loadClass(className);
					Plugin annotation = possiblePlugin.getAnnotation(Plugin.class);
					if (annotation != null) {
						String id = annotation.id();
						if (plugins.containsKey(id))
							logError("Encountered a duplicate plugin id: " + id);
						else {
							try {
								@SuppressWarnings("unchecked")
								Class<T> plugin = (Class<T>) possiblePlugin;
								pluginUser.loadPlugin(plugin);
								plugins.put(id, plugin);
							}
							catch (PluginException e) {
								logError("Unable to load the plugin: " + id);
							}
						}
					}
				}
				catch (ClassNotFoundException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * Loads all plugins from the given directory. Plugins are simply '.jar' files that contain {@link Object Objects}
	 * annotated with {@link Plugin}.<br>
	 * If <tt>location</tt> does not exist, this method returns 0 plugins loaded.
	 * 
	 * @param location
	 *            the directory to load the plugins from
	 * @return the number of plugins loaded
	 */
	public int loadPlugins(Path location) {
		if (!Files.exists(location) || !(Files.isDirectory(location) || location.toString().endsWith(".jar"))) //If it is not a directory or jar file (or does not exist), stop, and return 0 plugins loaded
			return 0;
		int initSize = plugins.size(); //Store the initial size of the plugins list so that the change in size can be returned at the end.
		FileVisitor<Path> pluginLoader = new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				if (file.getFileName().toString().endsWith(".jar")) {
					try {
						JarFile jar = new JarFile(file.toFile());
						jars.add(jar);
						loader.addPath(file);
						loadClassesFromJar(jar);
					}
					catch (IOException e) {
						logError("Unable to open a plugin in " + location);
					}
				}
				return FileVisitResult.CONTINUE;
			}
		};
		try {
			Files.walkFileTree(location, pluginLoader);
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		pluginLocations.add(location);
		return plugins.size() - initSize;
	}
	
	/**
	 * Removes the plugin with the specified ID from the plugin list and unloads it from the {@link PluginUser}
	 * 
	 * @param ids
	 *            the IDs of one or more plugins to use as {@link java.lang.String Strings}
	 * @throws PluginException
	 *             a catch-all exception thrown for all plugin-related errors
	 */
	public void unloadPlugins(String... ids) throws PluginException {
		for (String id : ids) {
			if (plugins.containsKey(id)) {
				pluginUser.unloadPlugin(id);
				plugins.remove(id);
			}
		}
	}
	
	/**
	 * Removes all the plugins from the plugin list and unloads them from the {@link PluginUser}
	 * <p>
	 * Convenience method - forwards to {@link #unloadPlugins(String...) unloadPlugins(}{@link #getPluginIDs()
	 * getPluginIDs())};
	 * </p>
	 * 
	 * @throws PluginException
	 *             a catch-all exception thrown for all plugin-related errors
	 */
	public void unloadAllPlugins() throws PluginException {
		unloadPlugins(getPluginIDs());
	}
	
	/**
	 * Unloads any plugins with the specified IDs from the {@link PluginUser} without removing them from the plugins registry
	 * in this {@link PluginManager}
	 * 
	 * @param ids
	 *            an ArrayList of plugin IDs to disable
	 * @throws PluginException
	 *             a catch-all exception thrown for all plugin-related errors
	 */
	public void disablePlugins(ArrayList<String> ids) throws PluginException {}
	
	/**
	 * Unloads the plugin with the specified ID from the {@link PluginUser} without removing it from the plugins registry in
	 * this {@link PluginManager}
	 * 
	 * @param ids
	 *            the IDs of the plugins to remove as {@link java.lang.String Strings}
	 * @throws PluginException
	 *             a catch-all exception thrown for all plugin-related errors
	 */
	public void disablePlugins(String... ids) throws PluginException {
		for (String id : ids)
			if (plugins.containsKey(id) && isEnabled(id)) {
				pluginUser.unloadPlugin(id);
				enabled.put(id, false);
			}
	}
	
	/**
	 * Unloads all the plugins from the {@link PluginUser} without removing them from the plugins registry in this
	 * {@link PluginManager}
	 * <p>
	 * Convenience method - forwards to {@link #disablePlugins(ArrayList) disablePlugins(} {@link #getPluginIDs()
	 * getPluginIDs())};
	 * </p>
	 * 
	 * @throws PluginException
	 *             a catch-all exception thrown for all plugin-related errors
	 */
	public void disableAllPlugins() throws PluginException {
		disablePlugins(getPluginIDs());
	}
	
	/**
	 * Loads the plugin with the specified ID into the {@link PluginUser} from this {@link PluginManager PluginManager's}
	 * registry
	 * 
	 * @param ids
	 *            the ID of the plugin to be enabled as a {@link String}
	 * @throws PluginException
	 *             a catch-all exception thrown for all plugin-related errors
	 */
	public void enablePlugins(String... ids) throws PluginException {
		for (String id : ids)
			if (plugins.containsKey(id) && !isEnabled(id)) {
				pluginUser.loadPlugin(plugins.get(id));
				enabled.put(id, true);
			}
	}
	
	/**
	 * Loads all the plugins into the {@link PluginUser} from this {@link PluginManager PluginManager's} registry.
	 * <p>
	 * Convenience method - forwards to {@link #enablePlugins(String...) enablePlugins(}{@link #getPluginIDs()
	 * getPluginIDs())};
	 * </p>
	 * 
	 * @throws PluginException
	 *             a catch-all exception thrown for all plugin-related errors
	 */
	public void enableAllPlugins() throws PluginException {
		enablePlugins(getPluginIDs());
	}
	
	/**
	 * Reload all of the plugins in this {@link PluginManager PluginManager's} registry
	 * 
	 * @throws PluginException
	 *             a catch-all exception thrown for all plugin-related errors
	 */
	public void reloadPlugins() throws PluginException {
		ArrayList<Path> pluginLocations = new ArrayList<>(this.pluginLocations);
		unloadAllPlugins();
		this.pluginLocations.clear(); //So that the list of loaded locations starts fresh
		for (Path location : pluginLocations)
			loadPlugins(location);
	}
	
	/**
	 * Add a package name that cannot be loaded from by plugins in this {@link PluginManager} unless the class is already
	 * loaded.<br>
	 * Specificity goes from root onward, so adding "java" will block all of the everything in java, java.lang, etc. whereas
	 * adding java.lang would only block those classes in java.lang and its sub-packages.
	 * 
	 * @param packageName
	 *            the name or partial name of the package to block.
	 * @return true if the package was successfully added to the list.
	 */
	public final boolean addBlockedPackage(String packageName) {
		return Loader.addBlockedPackage(packageName);
	}
	
	/**
	 * Removes a blocked package from the list, allowing it to be loaded from plugins without being previously loaded
	 * elsewhere. NOTE: this will NOT unblock any sub-packages of the named package, but it WILL unblock parent-packages.
	 * 
	 * @param packageName
	 *            the name or partial name of the package to unblock.
	 * @return true if the removal of the named package or any of its parent-packages were successful.
	 */
	public final boolean removeBlockedPackage(String packageName) {
		return Loader.removeBlockedPackage(packageName);
	}
	
	/**
	 * Adds a system package to this {@link PluginManager}'s blocked list. System packages are those that should NEVER be
	 * loaded into by plugins, such as this {@link PluginManager}'s package.<br>
	 * NOTE: once added, system packages cannot be removed.
	 * 
	 * @param packageName
	 *            the name of the package to add
	 * @return true if the package was successfully added
	 */
	public final boolean addSystemPackage(String packageName) {
		return Loader.addSystemPackage(packageName);
	}
	
	/**
	 * @return the IDs of all the plugins loaded into this {@link PluginManager}'s {@link PluginUser} in a
	 *         {@link java.lang.String String} array.
	 */
	public String[] getPluginIDs() {
		return plugins.keySet().toArray(new String[plugins.size()]);
	}
	
	/**
	 * @return all the plugins loaded into this {@link PluginManager} in an ArrayList
	 */
	public ArrayList<T> getPlugins() {
		return new ArrayList<>(pluginUser.plugins.values());
	}
	
	/**
	 * @param ID
	 *            the plugin's ID as a String
	 * @return whether the plugin is enabled
	 */
	public boolean isEnabled(String ID) {
		return enabled.get(ID);
	}
	
	/**
	 * @return the locations that this {@link PluginManager} has loaded plugins from
	 */
	public ArrayList<Path> getPluginLocations() {
		return new ArrayList<>(pluginLocations);
	}
	
	/**
	 * Override this for error message logging. Defaults to:
	 * 
	 * <pre>
	 * System.err.println(&quot;ERROR: &quot; + error);
	 * </pre>
	 * 
	 * @param error
	 *            the error to log
	 */
	public void logError(String error) {
		System.err.println("ERROR: " + error);
	}
	
	/**
	 * Override this for information message logging. Defaults to:
	 * 
	 * <pre>
	 * System.out.println(&quot;INFO: &quot; + info);
	 * </pre>
	 * 
	 * @param info
	 *            the information to log
	 */
	public void logInfo(String info) {
		System.out.println("INFO: " + info);
	}
}

final class Loader extends URLClassLoader {
	private static final ArrayList<String> blockedPackages = new ArrayList<>();
	private static final ArrayList<String> systemPackages = new ArrayList<>();
	
	Loader(URL... urls) {
		super(urls);
		addSystemPackage(getClass().getPackage().getName());
	}
	
	public void addPath(Path path) throws MalformedURLException {
		super.addURL(path.toUri().toURL());
	}
	
	static final boolean addBlockedPackage(String packageName) {
		if (!blockedPackages.contains(packageName))
			return blockedPackages.add(packageName);
		return false;
	}
	
	static final boolean removeBlockedPackage(String packageName) {
		if (systemPackages.contains(packageName))
			return false;
		boolean removed = false;
		for (int i = 0; i < blockedPackages.size(); i++)
			if (blockedPackages.get(i).startsWith(packageName) && blockedPackages.get(i).length() > packageName.length()) {
				blockedPackages.remove(i--);
				removed = true;
			}
		return blockedPackages.remove(packageName) || removed;
	}
	
	static final boolean addSystemPackage(String packageName) {
		addBlockedPackage(packageName);
		if (!systemPackages.contains(packageName))
			return systemPackages.add(packageName);
		return false;
	}
	
	@Override
	protected final Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		for (String pkg : blockedPackages) {
			if (name.startsWith(pkg)) {
				return getSystemClassLoader().loadClass(name);
			}
		}
		return super.loadClass(name, resolve);
	}
}

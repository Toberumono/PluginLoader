package toberumono.plugin.manager;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.security.SecureClassLoader;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A ClassLoader implementation that internally stores a {@link Collection} of ClassLoaders that it can use to find classes.
 * This is ideal for plugin systems where one might need to dynamically expand the sources on which a single ClassLoader can
 * draw. Individual {@link ClassLoader ClassLoaders} are queried in the order that they were added for the purpose of
 * resolving classes.
 * 
 * @author Toberumono
 */
public final class PluginClassLoader extends SecureClassLoader {
	private final Set<String> blockedPackages;
	private final Set<String> systemPackages;
	private final Collection<ClassLoader> classloaders;
	private final Map<Path, ClassLoader> paths;
	
	/**
	 * Creates a new {@link PluginClassLoader} with the given parent {@link ClassLoader} and no blocked packages, system
	 * packages, or starting {@link ClassLoader ClassLoaders}.
	 * 
	 * @param parent
	 *            the parent {@link ClassLoader} of this {@link PluginClassLoader}
	 */
	public PluginClassLoader(ClassLoader parent) {
		this(parent, null, null, null);
	}
	
	/**
	 * Creates a new {@link PluginClassLoader} with the given parent {@link ClassLoader} and the given blockedPackages,
	 * systemPackages, and {@link ClassLoader ClassLoaders}. The {@link PluginClassLoader} stores a reference to the sets and
	 * collection if they are non-null (otherwise it simply constructs empty {@link Set Sets} or {@link Collection
	 * Collections} as appropriate), thereby allowing the object that initialized the {@link PluginClassLoader} access to
	 * those fields without exposing them to any other objects. However, it is important to note that removing elements from
	 * the <tt>classloaders</tt> {@link Collection} will result in undefined behavior.
	 * 
	 * @param parent
	 *            the parent {@link ClassLoader} of this {@link PluginClassLoader}
	 * @param blockedPackages
	 *            a {@link Set} of packages from which this {@link PluginClassLoader} will not load classes
	 * @param systemPackages
	 *            the same as <tt>blockedPackages</tt> however, once a package is added to this set, only the object that
	 *            created this {@link PluginClassLoader} can remove it (this is just to provide additional security)
	 * @param classloaders
	 *            a {@link Collection} of {@link ClassLoader ClassLoaders} that this {@link PluginClassLoader} can use
	 */
	public PluginClassLoader(ClassLoader parent, Set<String> blockedPackages, Set<String> systemPackages, Collection<ClassLoader> classloaders) {
		super(parent);
		this.blockedPackages = blockedPackages == null ? new LinkedHashSet<>() : blockedPackages;
		this.systemPackages = systemPackages == null ? new LinkedHashSet<>() : systemPackages;
		addSystemPackage(getClass().getPackage().getName());
		this.classloaders = classloaders == null ? new LinkedHashSet<>() : classloaders;
		this.paths = new LinkedHashMap<>();
	}
	
	/**
	 * Adds a {@link ClassLoader} that does not have a corresponding {@link Path}.
	 * 
	 * @param cl
	 *            the {@link ClassLoader} to add
	 */
	public final void addClassLoader(ClassLoader cl) {
		synchronized (classloaders) {
			classloaders.add(cl);
		}
	}
	
	/**
	 * Adds a {@link ClassLoader} that corresponds to the given {@link Path}.
	 * 
	 * @param cl
	 *            the {@link ClassLoader} to add
	 * @param path
	 *            the {@link Path} to which this {@link ClassLoader} corresponds
	 * @return {@code true} if a {@link ClassLoader} for the given {@link Path} was not already registered
	 */
	public final boolean addClassLoader(ClassLoader cl, Path path) {
		synchronized (paths) {
			synchronized (classloaders) {
				if (paths.containsKey(path))
					return false;
				paths.put(path, cl);
				addClassLoader(cl);
				return true;
			}
		}
	}
	
	/**
	 * Adds a {@link ClassLoader} that corresponds to the given {@link Path}.
	 * 
	 * @param path
	 *            the {@link Path} in which the {@link ClassLoader} should look for classes
	 * @return {@code true} if a {@link ClassLoader} for the given {@link Path} was not already registered
	 * @throws MalformedURLException
	 *             if the given {@link Path} could not be converted to a valid {@link URL}
	 */
	public final boolean addClassLoader(Path path) throws MalformedURLException {
		synchronized (paths) {
			synchronized (classloaders) {
				if (paths.containsKey(path))
					return false;
				ClassLoader cl = new URLClassLoader(new URL[]{path.toUri().toURL()}, this);
				paths.put(path, cl);
				addClassLoader(cl);
				return true;
			}
		}
	}
	
	/**
	 * Currently an unsupported operation - this is just a placeholder for potential future work.
	 * 
	 * @param path
	 *            the {@link Path} corresponding to the {@link ClassLoader} to remove
	 * @return {@code true} if the {@link ClassLoader} corresponding to <tt>path</tt> was removed
	 * @throws UnsupportedOperationException
	 *             whenever this method is called - this is currently unsupported
	 */
	public boolean removeClassLoader(Path path) {
		throw new UnsupportedOperationException("Cannot remove a classloader.");
	}
	
	final synchronized boolean addBlockedPackage(String packageName) {
		return blockedPackages.add(packageName);
	}
	
	final synchronized boolean removeBlockedPackage(String packageName) {
		if (systemPackages.contains(packageName))
			return false;
		return blockedPackages.remove(packageName);
	}
	
	final synchronized boolean addSystemPackage(String packageName) {
		return systemPackages.add(packageName);
	}
	
	@Override
	protected final Class<?> findClass(String name) throws ClassNotFoundException {
		//If the package is a blocked or system package, forward it to the parent classloader (so only preloaded classes from that package can be used). 
		for (String pkg : systemPackages)
			if (name.startsWith(pkg))
				return super.loadClass(name);
		for (String pkg : blockedPackages)
			if (name.startsWith(pkg))
				return super.loadClass(name);
		synchronized (classloaders) {
			for (ClassLoader cl : classloaders) {
				try {
					return cl.loadClass(name);
				}
				catch (ClassNotFoundException e) {}
			}
			return super.findClass(name);
		}
	}
}

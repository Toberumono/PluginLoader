package toberumono.plugin.manager;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.security.SecureClassLoader;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class PluginClassLoader extends SecureClassLoader {
	private final Set<String> blockedPackages;
	private final Set<String> systemPackages;
	private final Collection<ClassLoader> classloaders;
	private final Map<Path, ClassLoader> paths;
	
	public PluginClassLoader(ClassLoader parent) {
		this(parent, null, null, null);
	}
	
	public PluginClassLoader(ClassLoader parent, Set<String> blockedPackages, Set<String> systemPackages, Collection<ClassLoader> classloaders) {
		super(parent);
		this.blockedPackages = blockedPackages == null ? new LinkedHashSet<>() : blockedPackages;
		this.systemPackages = systemPackages == null ? new LinkedHashSet<>() : systemPackages;
		addSystemPackage(getClass().getPackage().getName());
		this.classloaders = classloaders == null ? new LinkedHashSet<>() : classloaders;
		this.paths = new LinkedHashMap<>();
	}
	
	public final synchronized void addClassLoader(ClassLoader cl) {
		classloaders.add(cl);
	}
	
	public final synchronized boolean addClassLoader(Path path) throws MalformedURLException {
		if (paths.containsKey(path))
			return false;
		ClassLoader cl = new URLClassLoader(new URL[]{path.toUri().toURL()}, this);
		paths.put(path, cl);
		addClassLoader(cl);
		return true;
	}
	
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
		for (ClassLoader cl : classloaders) {
			try {
				return cl.loadClass(name);
			}
			catch (ClassNotFoundException e) {}
		}
		return super.findClass(name);
	}
}

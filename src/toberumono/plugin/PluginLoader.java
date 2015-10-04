package toberumono.plugin;

import java.io.IOException;
import java.net.URL;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PropertyPermission;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import toberumono.utils.general.MutedLogger;

import sun.security.util.SecurityConstants;

public final class PluginLoader {
	static {
		PermissionCollection perms = new Permissions();
		perms.add(new AllPermission());
		perms.add(new PropertyPermission("file.separator", SecurityConstants.PROPERTY_RW_ACTION));
		//PluginLoader.class.getProtectionDomain().getPermissions().add(new AllPermission());
		ProtectionDomain[] arr = {new ProtectionDomain(PluginLoader.class.getProtectionDomain().getCodeSource(), perms)};
		//System.setSecurityManager(new PluginSecurityManager());
		//SecurityManager sm = System.getSecurityManager();
		//acc = (AccessControlContext) sm.getSecurityContext();
		//p = new java.security.AccessControlContext(arr);
		//p.checkPermission(new PropertyPermission("file.separator", SecurityConstants.PROPERTY_RW_ACTION));
	}
	
	public static void main(String[] args) throws IOException {
		System.out.println(PluginLoader.class.getProtectionDomain().getCodeSource());
		System.out.println(Policy.getPolicy().implies(PluginLoader.class.getProtectionDomain(), new AllPermission()));
		Set<CodeSource> roots = new HashSet<>();
		roots.add(PluginLoader.class.getProtectionDomain().getCodeSource());
		PluginLoader pl = new PluginLoader(roots, null, null, null, null, new URL[0]);
		System.out.println(System.getProperty("user.home"));
		try {
			Files.lines(Paths.get("/Users/joshualipstone/Dropbox/Eclipse Dictionary.txt")).forEach(System.out::println);
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static final String separator = FileSystems.getDefault().getSeparator();
	
	private final PluginClassLoader loader;
	private final Map<Path, FileSystem> openFileSystems;
	private final Map<Path, WatchKey> loaded;
	private final WatchService watcher;
	private final DirectoryMonitor monitor;
	private final LoadQueue<String> foundClasses;
	
	private final ReadWriteLock lock;
	private final Logger log;
	
	private final Set<String> blacklistedPackages;
	
	private BiConsumer<Plugin, Class<?>> onPluginLoad, onPluginUnload;
	
	public PluginLoader(Set<CodeSource> rootCodeSources, BiConsumer<Plugin, Class<?>> toLoad, BiConsumer<Plugin, Class<?>> toUnload, URL... urls) throws IOException {
		this(rootCodeSources, toLoad, toUnload, MutedLogger.getMutedLogger(), urls);
	}
	
	public PluginLoader(Set<CodeSource> rootCodeSources, BiConsumer<Plugin, Class<?>> toLoad, BiConsumer<Plugin, Class<?>> toUnload, Logger log, URL... urls) throws IOException {
		this(rootCodeSources, toLoad, toUnload, new HashSet<>(), log, urls);
	}
	
	public PluginLoader(Set<CodeSource> rootCodeSources, BiConsumer<Plugin, Class<?>> toLoad, BiConsumer<Plugin, Class<?>> toUnload, Set<String> blockedPackages, URL... urls) throws IOException {
		this(rootCodeSources, toLoad, toUnload, blockedPackages, MutedLogger.getMutedLogger(), urls);
	}
	
	public PluginLoader(Set<CodeSource> rootCodeSources, BiConsumer<Plugin, Class<?>> toLoad, BiConsumer<Plugin, Class<?>> toUnload, Set<String> blockedPackages, Logger log, URL... urls) throws IOException {
		System.setSecurityManager(new PluginSecurityManager());//rootCodeSources));
		loader = new PluginClassLoader(urls);
		this.log = (log == null ? MutedLogger.getMutedLogger() : log);
		this.blacklistedPackages = (blockedPackages == null ? new HashSet<>() : Collections.unmodifiableSet(blockedPackages));
		onPluginLoad = toLoad;
		onPluginUnload = toUnload;
		lock = new ReentrantReadWriteLock();
		//This way, the object that instantiated this instance will be able to control access to the ability to modify the blocked and system packages.
		openFileSystems = new HashMap<>();
		loaded = new TreeMap<>((p1, p2) -> {
			try {
				if (Files.isSameFile(p1, p2))
					return 0;
			}
			catch (IOException e) {/* Nothing to do here */}
			return p1.compareTo(p2);
		});
		watcher = FileSystems.getDefault().newWatchService();
		monitor = new DirectoryMonitor();
		foundClasses = new LoadQueue<>(lock);
		Runtime.getRuntime().addShutdownHook(new Thread() { //Close the classloader when we're done
					@Override
					public void run() {
						monitor.shutDown();
						try {
							watcher.close();
						}
						catch (IOException e) {
							e.printStackTrace();
							log.warning("Could not close a WatchService");
						}
						try {
							loader.close();
						}
						catch (IOException e) {
							e.printStackTrace();
						}
						openFileSystems.forEach((name, fs) -> {
							try {
								fs.close();
							}
							catch (IOException e) {
								log.warning("Could not close " + name);
							}
						});
					}
				});
	}
	
	/**
	 * @param path
	 *            the {@link Path} to a directory tree containing .jar and .class files. If a .class file is in the root
	 *            directory of the tree, then it is assumed to be in the default package. Subdirectories are assumed to
	 *            indicate packages.
	 * @return the number of plugins loaded from {@code path}
	 * @throws IOException
	 *             if the directory pointed to by {@code path} could not be registered for watching
	 */
	public int load(Path path) throws IOException {
		path = path.toAbsolutePath().normalize();
		try {
			lock.writeLock().lock();
			if (loaded(path))
				return 0;
			WatchKey key;
			try {
				key = path.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
			}
			catch (IOException e) {
				log.severe("Unable to register " + path + " for watching.");
				return 0;
			}
			if (Files.isDirectory(path)) {
				try (Stream<Path> items = Files.list(path)) {
					PluginWalker walker = new PluginWalker(loaded::containsKey, foundClasses, blacklistedPackages, log, loader);
					items.forEach(p -> {
						SecurityManager sm = System.getSecurityManager();
						try {
							Files.walkFileTree(p, walker);
						}
						catch (Exception e) {
							e.printStackTrace();
						}
					});
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
			else if (Files.isRegularFile(path)) {
				String fpath = path.toString();
				if (fpath.endsWith(".jar") || fpath.endsWith(".zip")) {
					loadJar(path);
				}
				else {
					if (fpath.endsWith(".class"))
						log.warning("Cannot load '" + path + "' - it is a bare .class file.");
					else
						log.warning("Cannot load '" + path + "' - it is neither a .jar file, .zip file, or directory.");
					return 0;
				}
			}
			register(path, key);
			return processFoundClasses();
		}
		finally {
			lock.writeLock().unlock();
		}
	}
	
	private int loadJar(Path path) {
		try {
			lock.writeLock().lock();
			try (FileSystem jar = FileSystems.newFileSystem(path, null)) {
				loader.addPath(path);
				//openFileSystems.put(path, jar); //Depending on how .jar files are handled by the ClassLoader, we might be able to use try-with-resources
				PluginWalker walker = new PluginWalker(loaded::containsKey, foundClasses, blacklistedPackages, log, loader);
				for (Path p : jar.getRootDirectories())
					Files.walkFileTree(p, walker);
				return walker.getNumLoaded();
			}
			catch (IOException e) {
				log.warning("Unable to open " + path.toString());
				return 0;
			}
		}
		finally {
			lock.writeLock().unlock();
		}
	}
	
	/**
	 * Determine whether {@code path} has already been loaded by the {@link PluginLoader}.<br>
	 * A {@link Path} has been loaded if it or any of its parent {@link Path Paths} have been loaded.
	 * 
	 * @param path
	 *            the {@link Path} to check
	 * @return true if {@code path} or any of its parent {@link Path Paths} have been loaded
	 */
	public boolean loaded(Path path) {
		for (Path p : loaded.keySet())
			if (path.startsWith(p))
				return true;
		return false;
	}
	
	private int processFoundClasses() {
		int successful = 0;
		try {
			lock.writeLock().lock();
			while (foundClasses.size() > 0) {
				String plugin = foundClasses.poll();
				try {
					Class<?> potential = loader.loadClass(plugin);
					Plugin p = potential.getAnnotation(Plugin.class);
					if (p != null) {
						onPluginLoad.accept(p, potential); //TODO might need some method of error throwing
						successful++;
					}
				}
				catch (ClassNotFoundException e) {
					log.warning("Unable to find " + plugin + ".  Was it deleted?");
				}
			}
			return successful;
		}
		finally {
			lock.writeLock().unlock();
		}
	}
	
	private void loadPlugin(String className) {
		try {
			lock.writeLock().lock();
			//TODO load plugin here
		}
		finally {
			lock.writeLock().unlock();
		}
	}
	
	private void unloadPlugin(String pluginID) {
		try {
			lock.writeLock().lock();
			//TODO unload plugin here
		}
		finally {
			lock.writeLock().unlock();
		}
	}
	
	/**
	 * Attempts to register {@code path} with {@link #watcher}.
	 * 
	 * @param path
	 *            the {@link Path} to register
	 * @return true if {@code path} was registered successfully or false if {@code path} could not be registered because it
	 *         was already registered or is a sub-directory of an already-registered path
	 * @throws IOException
	 *             if the path could not be registered for any other reason (all of which indicate I/O problems)
	 */
	private boolean register(Path path, WatchKey key) {
		path = path.toAbsolutePath().normalize();
		try {
			lock.writeLock().lock();
			if (!Files.isDirectory(path)) {
				if (loaded.containsKey(path))
					return false;
				loaded.put(path, null);
				return true;
			}
			loaded.put(path, key);
			Iterator<Entry<Path, WatchKey>> i = loaded.entrySet().iterator();
			while (i.hasNext()) {
				Entry<Path, WatchKey> e = i.next();
				if (path.startsWith(e.getKey()))
					return false;
				if (e.getKey().startsWith(path)) {
					e.getValue().cancel();
					i.remove();
				}
			}
		}
		finally {
			lock.writeLock().unlock();
		}
		return true;
	}
	
	private class DirectoryMonitor implements Runnable {
		private boolean done;
		
		public DirectoryMonitor() {
			done = false;
		}
		
		@Override
		public void run() {
			WatchKey key;
			while (!isShutDown()) {
				try {
					while (!isShutDown() && (key = watcher.poll(1500, TimeUnit.MILLISECONDS)) != null) {
						Path root = (Path) key.watchable();
						List<WatchEvent<?>> events = key.pollEvents();
						key.reset();
						for (WatchEvent<?> event : events) {
							Path rel = (Path) event.context();
							String fname = rel.toString();
							if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
								String lowercase = fname.toLowerCase();
								if (fname.endsWith(".class")) {
									fname = fname.replaceAll(separator, ".");
									fname = fname.substring(0, fname.lastIndexOf(".class"));
									foundClasses.add(fname);
								}
								else if (lowercase.endsWith(".jar") || lowercase.endsWith(".zip")) {
									loadJar(root.resolve(rel));
								}
								else {
									log.info("Ignoring '" + rel + "' in '" + root + "' - it is neither a .jar, .zip, or .class file.");
								}
							}
							//TODO Some way to handle modification and/or deletion
						}
					}
					processFoundClasses(); //This delays loading found classes in the case of batch changes
				}
				catch (ClosedWatchServiceException e) {
					if (!done)
						log.warning("Watcher closed prior to DirectoryMonitor shutdown.");
				}
				catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		
		public boolean isShutDown() {
			synchronized (this) {
				return done;
			}
		}
		
		public void shutDown() {
			synchronized (this) {
				done = true;
			}
		}
	}
	
	/**
	 * @return an unmodifiable {@link Set} of packages from which plugins loaded by this {@link PluginLoader} cannot load
	 *         classes
	 */
	public Set<String> getBlacklistedPackages() {
		return blacklistedPackages;
	}
	
	/**
	 * @return the action performed by this {@link PluginLoader} when a plugin is loaded
	 */
	public BiConsumer<Plugin, Class<?>> getPluginLoadAction() {
		return onPluginLoad;
	}
	
	/**
	 * @param action
	 *            the action to be performed by this {@link PluginLoader} when a plugin is loaded
	 */
	public void setPluginLoadAction(BiConsumer<Plugin, Class<?>> action) {
		this.onPluginLoad = action;
	}
	
	/**
	 * @return the action performed by this {@link PluginLoader} when a plugin is loaded
	 */
	public BiConsumer<Plugin, Class<?>> getPluginUnloadAction() {
		return onPluginUnload;
	}
	
	/**
	 * @param action
	 *            the action to be performed by this {@link PluginLoader} when a plugin is loaded
	 */
	public void setPluginUnloadAction(BiConsumer<Plugin, Class<?>> action) {
		this.onPluginUnload = action;
	}
}

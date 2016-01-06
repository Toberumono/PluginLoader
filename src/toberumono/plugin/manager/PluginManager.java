package toberumono.plugin.manager;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import toberumono.plugin.annotations.Dependency;
import toberumono.plugin.annotations.PluginDescription;
import toberumono.utils.files.FileManager;
import toberumono.utils.functions.ExceptedConsumer;
import toberumono.utils.functions.IOExceptedFunction;

/**
 * A system for loading and managing plugins at runtime.
 * 
 * @author Toberumono
 * @param <T>
 *            the type of the plugins being loaded and managed
 */
public class PluginManager<T> extends FileManager {
	private static final ExecutorService pool;
	private static final String PROJECT_ROOT_PACKAGE = "toberumono.plugin";
	private static final Collection<String> DEFAULT_BLACKLISTED_PACKAGES;
	
	static {
		String maxThreads = System.getProperty("PluginManager.maxThreads", "0"); //If no value is specified use the default value
		int threads = 0;
		try {
			threads = Integer.parseInt(maxThreads);
		}
		catch (NumberFormatException e) {
			e.printStackTrace();
		}
		pool = threads > 0 ? Executors.newWorkStealingPool(threads) : Executors.newWorkStealingPool();
		DEFAULT_BLACKLISTED_PACKAGES = new LinkedHashSet<>();
		DEFAULT_BLACKLISTED_PACKAGES.add(PROJECT_ROOT_PACKAGE);
	}
	
	private final PluginClassLoader pcl;
	private final IOExceptedFunction<Path, FileSystem> fileSystemMaker;
	private final Collection<String> blacklistedPackages;
	private final Map<String, PluginData<T>> plugins;
	private final List<RequestedDependency<T>> requestedDependencies;
	private final Lock dependencySatisfiersLock;
	private final ReadWriteLock requestedDependenciesLock, pluginMapLock;
	private final Logger logger;
	private final Map<FileSystem, Path> opened;
	private final ExceptedConsumer<T> onInitialization;
	private final Collection<T> postInitFailures;
	
	private Path activePath;
	
	/**
	 * Creates a new {@link PluginManager} that does not allow plugins to be loaded in the {@code toberumono.plugin} package,
	 * on the {@link FileSystem} returned by {@link FileSystems#getDefault()} using the {@link ClassLoader} returned by
	 * {@link ClassLoader#getSystemClassLoader()} for its parent {@link ClassLoader}.
	 * 
	 * @param onInitialization
	 *            a function that processes plugins when they are initialized
	 * @throws IOException
	 *             if an I/O exception occurs while initializing the {@link PluginManager}
	 */
	public PluginManager(ExceptedConsumer<T> onInitialization) throws IOException {
		this(DEFAULT_BLACKLISTED_PACKAGES, onInitialization);
	}
	
	/**
	 * Creates a new {@link PluginManager} on the {@link FileSystem} returned by {@link FileSystems#getDefault()} using the
	 * {@link ClassLoader} returned by {@link ClassLoader#getSystemClassLoader()} for its parent {@link ClassLoader}.
	 * 
	 * @param blacklistedPackages
	 *            a <i>modifiable</i> {@link Collection} of packages from which plugins cannot be loaded (all packages in the
	 *            set automatically include any sub-packages)
	 * @param onInitialization
	 *            a function that processes plugins when they are initialized
	 * @throws IOException
	 *             if an I/O exception occurs while initializing the {@link PluginManager}
	 */
	public PluginManager(Collection<String> blacklistedPackages, ExceptedConsumer<T> onInitialization) throws IOException {
		this(blacklistedPackages, onInitialization, ClassLoader.getSystemClassLoader());
	}
	
	/**
	 * Creates a new {@link PluginManager} on the {@link FileSystem} returned by {@link FileSystems#getDefault()}, that uses
	 * <tt>blacklistedPackages</tt> to determine whether a plugin should be allowed to be loaded from a given package and the
	 * {@link ClassLoader} in <tt>parent</tt> for its parent {@link ClassLoader}.
	 * 
	 * @param blacklistedPackages
	 *            a <i>modifiable</i> {@link Collection} of packages from which plugins cannot be loaded (all packages in the
	 *            set automatically include any sub-packages)
	 * @param onInitialization
	 *            a function that processes plugins when they are initialized
	 * @param parentClassLoader
	 *            the parent {@link ClassLoader} for the plugins loaded by the {@link PluginManager}
	 * @throws IOException
	 *             if an I/O exception occurs while initializing the {@link PluginManager}
	 */
	public PluginManager(Collection<String> blacklistedPackages, ExceptedConsumer<T> onInitialization, ClassLoader parentClassLoader)
			throws IOException {
		this(blacklistedPackages, onInitialization, parentClassLoader, FileSystems.getDefault());
	}
	
	/**
	 * Creates a new {@link PluginManager} on the {@link FileSystem} given in <tt>fileSystem</tt>, that uses
	 * <tt>blacklistedPackages</tt> to determine whether a plugin should be allowed to be loaded from a given package and the
	 * {@link ClassLoader} in <tt>parent</tt> for its parent {@link ClassLoader}.
	 * 
	 * @param blacklistedPackages
	 *            a <i>modifiable</i> {@link Collection} of packages from which plugins cannot be loaded (all packages in the
	 *            set automatically include any sub-packages)
	 * @param onInitialization
	 *            a function that processes plugins when they are initialized
	 * @param parentClassLoader
	 *            the parent {@link ClassLoader} for the plugins loaded by the {@link PluginManager}
	 * @param fileSystem
	 *            the {@link FileSystem} on which changes will be watched for
	 * @throws IOException
	 *             if an I/O exception occurs while initializing the {@link PluginManager}
	 */
	public PluginManager(Collection<String> blacklistedPackages, ExceptedConsumer<T> onInitialization, ClassLoader parentClassLoader,
			FileSystem fileSystem) throws IOException {
		super(null, p -> {}, p -> {}, p -> {}, k -> {}, fileSystem); //All null functions are implemented through overriding methods
		pcl = new PluginClassLoader(parentClassLoader);
		opened = new LinkedHashMap<>();
		fileSystemMaker = p -> {
			FileSystem f = FileSystems.newFileSystem(p, pcl);
			opened.put(f, p);
			return f;
		};
		activePath = null;
		logger = Logger.getLogger(this.getClass().getName());
		this.blacklistedPackages = blacklistedPackages;
		try {
			if (!this.blacklistedPackages.contains(PROJECT_ROOT_PACKAGE))
				this.blacklistedPackages.addAll(DEFAULT_BLACKLISTED_PACKAGES);
		}
		catch (UnsupportedOperationException e) { //This accounts for unmodifiable collections
			logger.log(Level.WARNING, "Unable to add '" + PROJECT_ROOT_PACKAGE +
					"' to the blocked packages for a PluginManager.  This poses a significant security risk.");
		}
		this.onInitialization = onInitialization;
		pluginMapLock = new ReentrantReadWriteLock();
		dependencySatisfiersLock = new ReentrantLock();
		requestedDependenciesLock = new ReentrantReadWriteLock();
		plugins = new LinkedHashMap<>();
		requestedDependencies = new LinkedList<>();
		postInitFailures = new LinkedHashSet<>();
	}
	
	@Override
	public synchronized boolean add(Path path) throws IOException {
		if (!pcl.addClassLoader(path))
			return false;
		try {
			activePath = path;
			boolean changed = super.add(path);
			if (changed)
				logger.log(Level.INFO, "Added " + path.toString());
			return changed;
		}
		finally {
			activePath = null;
		}
	}
	
	@Override
	public synchronized boolean remove(Path path) throws IOException {
		if (!pcl.removeClassLoader(path))
			return false;
		try {
			activePath = path;
			boolean changed = super.remove(path);
			if (changed)
				logger.log(Level.INFO, "Removed " + path.toString());
			return changed;
		}
		finally {
			activePath = null;
		}
	}
	
	@Override
	protected void onAddFile(Path p) throws IOException {
		Path pa = activePath.relativize(p);
		if (pa.endsWith(".class")) {
			String name = pa.toString();
			name = name.substring(0, name.lastIndexOf(".class"));
			while (name.indexOf('/') >= 0) //replaceAll uses regex, so this is faster. (Probably)
				name = name.replace('/', '.');
			queueClassName(name);
		}
		else if ((p.endsWith(".jar") || p.endsWith(".zip")) && pcl.addClassLoader(p)) { //We create the ClassLoader for the .jar/.zip file here.
			try (FileSystem fs = fileSystemMaker.apply(p)) {
				PluginWalker pw =
						new PluginWalker(blacklistedPackages, path -> !this.getPaths().contains(path), this::queueClassName, fileSystemMaker,
								Logger.getLogger(PluginWalker.class.getName()));
				for (Path dir : fs.getRootDirectories())
					Files.walkFileTree(dir, pw);
			}
		}
	}
	
	private void queueClassName(String name) {
		if (testBlacklist(name)) {
			logger.log(Level.WARNING, "Attempted to add a class in a blacklisted package (" + name + ")");
			return;
		}
		try {
			pool.submit(new PluginAnalyser(name));
		}
		catch (ClassNotFoundException e) {
			logger.log(Level.WARNING, "Unable to find the class for " + name, e);
		}
	}
	
	/**
	 * Tests whether a named item is in a blacklisted package.
	 * 
	 * @param name
	 *            the name of the item
	 * @return {@code true} if <tt>name</tt> is in a blacklisted package (it starts with a blacklisted package)
	 */
	private boolean testBlacklist(String name) {
		for (String pack : blacklistedPackages)
			if (name.startsWith(pack))
				return true;
		return false;
	}
	
	@Override
	public synchronized void close() throws IOException {
		IOException except = null;
		try {
			super.close();
		}
		catch (IOException e) {
			logger.log(Level.SEVERE, "Error while attempting to close a PluginManager", e);
			if (except == null)
				except = e;
		}
		Iterator<Entry<FileSystem, Path>> iter = opened.entrySet().iterator();
		while (iter.hasNext()) {
			Entry<FileSystem, Path> open = iter.next();
			try {
				open.getKey().close();
				iter.remove();
			}
			catch (IOException e) {
				logger.log(Level.SEVERE,
						"Error while attempting to close the FileSystem object for " + open.getValue().toString() + " while closing a PluginManager",
						e);
				if (except == null)
					except = e;
			}
		}
		if (except != null)
			throw except;
	}
	
	class PluginAnalyser implements Runnable {
		private final Class<? extends T> clazz;
		
		@SuppressWarnings("unchecked")
		public PluginAnalyser(String name) throws ClassNotFoundException {
			clazz = (Class<? extends T>) pcl.loadClass(name);
		}
		
		@Override
		public void run() {
			synchronized (pluginMapLock.writeLock()) {
				PluginDescription info = clazz.getAnnotation(PluginDescription.class);
				String id = info.id();
				if (plugins.containsKey(id)) { //TODO Implement plugin removal
					logger.log(Level.WARNING,
							"Attempted to load a plugin with the ID, " + id + ", but another plugin with that ID has already been loaded.");
					return;
				}
				PluginData<T> pd = new PluginData<>(clazz);
				plugins.put(id, pd);
				synchronized (requestedDependenciesLock.writeLock()) {
					for (RequestedDependency<T> rd : pd.generateDependencyRequests(requestedDependenciesLock))
						requestedDependencies.add(rd);
				}
			}
		}
	}
	
	class DependencySatisfier implements Runnable {
		private final RequestedDependency<T> dependency;
		
		public DependencySatisfier(RequestedDependency<T> dependency) {
			this.dependency = dependency;
		}
		
		@Override
		public void run() {
			mainLoop: while (true)
				try {
					pluginMapLock.readLock().lockInterruptibly();
					for (PluginData<T> plugin : plugins.values())
						if (plugin.satisfyDependency(dependency))
							break;
					if (!dependency.isSatisfied()) {
						try {
							dependencySatisfiersLock.lockInterruptibly();
							pluginMapLock.readLock().unlock();
							dependencySatisfiersLock.wait();
						}
						finally {
							pluginMapLock.readLock().lock();
							dependencySatisfiersLock.unlock();
						}
					}
				}
				catch (InterruptedException e1) {
					break mainLoop;
				}
				finally {
					pluginMapLock.readLock().unlock();
				}
		}
	}
	
	/**
	 * Attempts to resolve the {@link Dependency Dependencies} that plugins have requested.
	 * 
	 * @return {@code true} iff all requested {@link Dependency Dependencies} have been satisfied when this method returns
	 */
	public boolean resolve() {
		synchronized (pluginMapLock.readLock()) {
			synchronized (requestedDependenciesLock.writeLock()) {
				Iterator<RequestedDependency<T>> iter = null;
				RequestedDependency<T> request = null;
				for (PluginData<T> satisfier : plugins.values()) {
					iter = requestedDependencies.iterator();
					while (iter.hasNext()) {
						request = iter.next();
						if (satisfier.satisfyDependency(request))
							iter.remove();
					}
				}
				return requestedDependencies.size() == 0;
			}
		}
	}
	
	/**
	 * Initializes all of the linkable plugins that have not already been initialized using the given arguments and passes
	 * them to the <tt>onInitialization</tt> {@link ExceptedConsumer} that was provided when the {@link PluginManager} was
	 * constructed.
	 * 
	 * @param args
	 *            the arguments with which plugins should be initialized
	 * @throws Exception
	 *             if an error occurs either during initialization or in <tt>onInitialization</tt>
	 */
	public void initializePlugins(Object... args) throws Exception {
		synchronized (pluginMapLock) {
			resolve(); //We cannot initialize plugins without resolving their dependencies first
			for (PluginData<T> pd : plugins.values()) { //TODO implement plugin initialization ordering
				if (!pd.getDescription().type().shouldInitialize())
					continue;
				if (pd.isLinkable() && !pd.isConstructed()) {
					T plugin = pd.construct(args);
					try {
						onInitialization.accept(plugin);
					}
					catch (Exception e) {
						postInitFailures.add(plugin);
						throw e;
					}
				}
			}
			//TODO replace this stopgap system with dynamic removal of plugins
			Iterator<T> iter = postInitFailures.iterator();
			while (iter.hasNext()) {
				T plugin = iter.next();
				onInitialization.accept(plugin);
				iter.remove();
			}
		}
	}
}

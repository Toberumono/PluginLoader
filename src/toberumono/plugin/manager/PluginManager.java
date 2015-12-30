package toberumono.plugin.manager;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import toberumono.plugin.annotations.Dependency;
import toberumono.plugin.annotations.PluginDescription;
import toberumono.plugin.exceptions.PluginConstructionException;
import toberumono.plugin.exceptions.UnlinkablePluginException;
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
	}
	
	private final PluginClassLoader pcl;
	private final IOExceptedFunction<Path, FileSystem> fileSystemMaker;
	private final Collection<String> blacklistedPackages;
	private final Map<String, PluginData<T>> plugins;
	private final List<RequestedDependency<T>> requestedDependencies;
	private final Lock pluginMapLock;
	private final Logger logger;
	private final Map<FileSystem, Path> opened;
	private final ExceptedConsumer<T> onConstruction;
	
	private Path activePath;
	
	public PluginManager(Collection<String> blacklistedPackages, ExceptedConsumer<T> onConstruction) throws IOException {
		this(blacklistedPackages, onConstruction, ClassLoader.getSystemClassLoader());
	}
	
	public PluginManager(Collection<String> blacklistedPackages, ExceptedConsumer<T> onConstruction, ClassLoader rootClassLoader) throws IOException {
		this(blacklistedPackages, onConstruction, rootClassLoader, FileSystems.getDefault());
	}
	
	public PluginManager(Collection<String> blacklistedPackages, ExceptedConsumer<T> onConstruction, ClassLoader rootClassLoader, FileSystem fs) throws IOException {
		super(null, p -> {}, p -> {}, p -> {}, k -> {}, fs); //All null functions are implemented through overriding methods
		pcl = new PluginClassLoader(rootClassLoader);
		opened = new LinkedHashMap<>();
		fileSystemMaker = p -> {
			FileSystem f = FileSystems.newFileSystem(p, pcl);
			opened.put(f, p);
			return f;
		};
		activePath = null;
		this.blacklistedPackages = blacklistedPackages;
		this.onConstruction = onConstruction;
		logger = Logger.getLogger(this.getClass().getName());
		pluginMapLock = new ReentrantLock();
		plugins = new LinkedHashMap<>();
		requestedDependencies = new ArrayList<>();
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
				PluginWalker pw = new PluginWalker(blacklistedPackages, path -> !this.getPaths().contains(path), this::queueClassName, fileSystemMaker,
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
				logger.log(Level.SEVERE, "Error while attempting to close the FileSystem object for " + open.getValue().toString() + " while closing a PluginManager", e);
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
			synchronized (pluginMapLock) {
				PluginDescription info = clazz.getAnnotation(PluginDescription.class);
				String id = info.id();
				if (plugins.containsKey(id)) {
					logger.log(Level.WARNING, "Attempted to load a plugin with the ID, " + id + ", but another plugin with that ID has already been loaded.");
					return;
				}
				PluginData<T> pd = new PluginData<>(clazz);
				plugins.put(id, pd);
				synchronized (requestedDependencies) {
					requestedDependencies.addAll(pd.generateDependencyRequests());
				}
			}
		}
	}
	
	/**
	 * Attempts to resolve the {@link Dependency Dependencies} that plugins have requested.
	 * 
	 * @return {@code true} iff all requested {@link Dependency Dependencies} have been satisfied when this method returns
	 */
	public boolean resolve() {
		synchronized (pluginMapLock) {
			synchronized (requestedDependencies) { //While this is a bit wordy, it runs in O(plugins * requestedDependencies) instead of O(plugins * requestedDependencies ^ 2)
				List<RequestedDependency<T>> unsatisfied = new ArrayList<>(requestedDependencies.size());
				for (PluginData<T> satisfier : plugins.values()) {
					for (RequestedDependency<T> rd : requestedDependencies) {
						if (!rd.trySatisfy(satisfier))
							unsatisfied.add(rd);
					}
					requestedDependencies.clear();
					requestedDependencies.addAll(unsatisfied);
					unsatisfied.clear();
				}
				return requestedDependencies.size() == 0;
			}
		}
	}
	
	public void initialize(Object... args) throws Exception {
		synchronized (pluginMapLock) {
			resolve(); //We cannot initialize plugins without resolving their dependencies first
			for (PluginData<T> pd : plugins.values()) {
				if (pd.isLinkable() && !pd.isConstructed())
					onConstruction.accept(pd.construct(args));
			}
		}
	}
}

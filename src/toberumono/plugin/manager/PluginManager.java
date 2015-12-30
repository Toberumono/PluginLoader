package toberumono.plugin.manager;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import toberumono.plugin.annotations.PluginDescription;
import toberumono.plugin.exceptions.PluginConstructionException;
import toberumono.utils.files.FileManager;
import toberumono.utils.functions.IOExceptedFunction;

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
	private final Collection<FileSystem> opened;
	
	private Path activePath;
	
	public PluginManager(ClassLoader rootClassLoader, Collection<String> blacklistedPackages) throws IOException {
		this(rootClassLoader, blacklistedPackages, FileSystems.getDefault());
	}
	
	public PluginManager(ClassLoader rootClassLoader, Collection<String> blacklistedPackages, FileSystem fs) throws IOException {
		super(null, p -> {}, p -> {}, p -> {}, k -> {}, fs); //All null functions are implemented through overriding methods
		pcl = new PluginClassLoader(rootClassLoader);
		opened = new LinkedHashSet<>();
		fileSystemMaker = p -> {
			FileSystem f = FileSystems.newFileSystem(p, pcl);
			opened.add(f);
			return f;
		};
		activePath = null;
		this.blacklistedPackages = blacklistedPackages;
		logger = Logger.getLogger(this.getClass().getName());
		pluginMapLock = new ReentrantLock();
		plugins = new LinkedHashMap<>();
		requestedDependencies = Collections.synchronizedList(new ArrayList<>());
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
			if (!testBlacklist(name))
				queueClassName(name);
		}
		else if ((p.endsWith(".jar") || p.endsWith(".zip")) && pcl.addClassLoader(p)) { //We create the ClassLoader for the .jar/.zip file here.
			try (FileSystem fs = fileSystemMaker.apply(p)) {
				PluginWalker pw =
						new PluginWalker(blacklistedPackages, path -> !this.getPaths().contains(path), name -> {
							if (!testBlacklist(name))
								queueClassName(name);
						}, fileSystemMaker, Logger.getLogger(PluginWalker.class.getName()));
				for (Path dir : fs.getRootDirectories())
					Files.walkFileTree(dir, pw);
			}
		}
	}
	
	private void queueClassName(String name) {
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
			if (name.startsWith(pack)) {
				logger.log(Level.WARNING, "Attempted to add a class in a blacklisted package (" + name + ")");
				return true;
			}
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
		Iterator<FileSystem> iter = opened.iterator();
		while (iter.hasNext()) {
			FileSystem open = iter.next();
			try {
				open.close();
				try {
					iter.remove();
				}
				catch (UnsupportedOperationException e) {}
			}
			catch (IOException e) {
				logger.log(Level.SEVERE, "Error while attempting to close a PluginManager", e);
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
				if (plugins.containsKey(id))
					return; //TODO Implement handling duplicate IDs
				PluginData<T> pd = new PluginData<>(clazz);
				plugins.put(id, pd);
				requestedDependencies.addAll(pd.generateDependencyRequests());
			}
		}
	}
	
	public synchronized void resolve() {
		synchronized (pluginMapLock) {
			for (PluginData<T> satisfier : plugins.values()) {
				Iterator<RequestedDependency<T>> iter = requestedDependencies.iterator();
				while (iter.hasNext()) {
					RequestedDependency<T> rd = iter.next();
					if (rd.satisfy(satisfier))
						iter.remove();
				}
			}
		}
	}
	
	public synchronized void initialize(Object... args) throws PluginConstructionException {
		for (PluginData<T> pd : plugins.values()) {
			if (pd.isLinkable() && !pd.isConstructed())
				pd.construct(args);
		}
	}
}

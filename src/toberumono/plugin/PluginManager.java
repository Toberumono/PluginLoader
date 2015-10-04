package toberumono.plugin;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.logging.Logger;

import toberumono.structures.tuples.Pair;
import toberumono.utils.general.MutedLogger;

import static java.nio.file.StandardWatchEventKinds.*;

public class PluginManager<P extends ManageablePlugin> {
	private final Set<String> blacklistedPackages;
	private final Class<P> pluginClass;
	private final ClassLoader loader;
	private final Map<Path, WatchKey> keys;
	private final DirectoryMonitor monitor;
	private final ReadWriteLock lock;
	private final LoadQueue<Pair<String, ClassLoader>> loadQueue;
	
	private final Logger log, monitorLog, pwLog;
	private int active;
	
	public PluginManager(Class<P> pluginClass, FileSystem fileSystem, Set<String> blacklistedPackages, ClassLoader loader, ReadWriteLock lock, Logger log) throws IOException {
		this.blacklistedPackages = blacklistedPackages == null ? Collections.emptySet() : Collections.unmodifiableSet(blacklistedPackages);
		this.loader = loader == null ? ClassLoader.getSystemClassLoader() : loader;
		if (log == null)
			this.log = this.monitorLog = this.pwLog = MutedLogger.getMutedLogger();
		else {
			this.log = log;
			this.monitorLog = this.log.getLogger(this.log.getName() + ".DirectoryMonitor");
			this.pwLog = this.log.getLogger(this.log.getName() + ".PluginWalker");
		}
		this.pluginClass = pluginClass;
		this.lock = lock;
		this.loadQueue = new LoadQueue<>(lock);
		this.keys = new HashMap<>();
		this.monitor = new DirectoryMonitor(fileSystem.newWatchService(), lock, null, monitorLog);
		active = 0;
	}
	
	public void addPluginLocation(Path folder) throws IOException {
		try {
			startingActivity();
			if (!Files.isDirectory(folder) && !folder.getFileName().endsWith(".jar") && !folder.getFileName().endsWith(".zip"))
				throw new UnsupportedOperationException("Cannot directly load files other than .jar and .zip.");
			try {
				lock.writeLock().lock();
				if (keys.containsKey(folder))
					return;
				WatchKey key = folder.register(monitor, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
				keys.put(folder, key);
			}
			finally {
				lock.writeLock().unlock();
			}
			PluginWalker pw = new PluginWalker(loader, this::rcl, blacklistedPackages, keys::containsKey, (n, p) -> loadQueue.add(new Pair<>(n, p)), lock, pwLog);
			Files.walkFileTree(folder, pw);
		}
		finally {
			endingActivity();
		}
	}
	
	private ClassLoader rcl(Path path, ClassLoader parent) throws MalformedURLException, IOException {
		try (URLClassLoader ucl = new URLClassLoader(new URL[]{path.toUri().toURL()}, parent)) {
			
			return ucl;
		}
	}
	
	public void processPluginLoadQueue() throws ClassNotFoundException {
		for (Pair<String, ClassLoader> directive : loadQueue) {
			Class<?> clazz = Class.forName(directive.getX(), true, directive.getY());
			if (!pluginClass.isAssignableFrom(clazz))
				continue;
			Plugin a = clazz.getAnnotation(Plugin.class);
			//TODO add checks to ensure that plugins have unique IDs.
			
		}
	}
	
	public synchronized void startingActivity() {
		active++;
	}
	
	public synchronized void endingActivity() {
		active--;
	}
}

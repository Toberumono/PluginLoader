package toberumono.plugin;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.Watchable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static java.nio.file.StandardWatchEventKinds.*;

public class DirectoryMonitor implements Runnable, WatchService {
	private final WatchService watcher;
	private final ReadWriteLock lock;
	private final Supplier<Integer> processFoundClasses;
	
	private final Logger log;
	private boolean done;
	
	public DirectoryMonitor(WatchService watcher, ReadWriteLock lock, Supplier<Integer> processFoundClasses, Logger log) {
		this.watcher = watcher;
		this.lock = lock;
		this.processFoundClasses = processFoundClasses;
		this.log = log;
		done = false;
	}
	
	private class WatchKeyProcessor extends Thread {
		private final WatchKey key;
		
		public WatchKeyProcessor(WatchKey key) {
			this.key = key;
		}

		@Override
		public void run() {
			Path root = (Path) key.watchable();
			for (WatchEvent<?> event : key.pollEvents()) {
				if (event.count() > 1)
					continue;
				WatchEvent.Kind<?> kind = event.kind();
				if (kind == OVERFLOW)
					continue;
				Path rel = root.relativize((Path) event.context());
				String fname = rel.toString();
				if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
					String lowercase = fname.toLowerCase();
					if (fname.endsWith(".class") || fname.endsWith(".jar") || fname.endsWith(".zip")) {
						
					}
					if (fname.endsWith(".class")) {
						fname = fname.replaceAll(rel.getFileSystem().getSeparator(), ".");
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
			key.reset();
		}
	}
	
	@Override
	public void run() {
		WatchKey key;
		while (!isShutDown()) {
			try {
				while (!isShutDown() && (key = poll(500, TimeUnit.MILLISECONDS)) != null)
					new WatchKeyProcessor(key).start();
				processFoundClasses.get(); //This delays loading found classes in the case of batch changes
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
	
	/**
	 * @return {@code true} if this {@link DirectoryMonitor} has been signaled to shut down. NOTE: this does not necessarily
	 *         mean that it has shut down.
	 */
	public synchronized boolean isShutDown() {
		return done;
	}
	
	/**
	 * Signals this {@link DirectoryMonitor} to shut down.
	 */
	public synchronized void shutDown() {
		done = true;
	}

	@Override
	public void close() throws IOException {
		shutDown();
		watcher.close();
	}

	@Override
	public WatchKey poll() {
		return watcher.poll();
	}

	@Override
	public WatchKey poll(long timeout, TimeUnit unit) throws InterruptedException {
		return watcher.poll(timeout, unit);
	}

	@Override
	public WatchKey take() throws InterruptedException {
		return watcher.take();
	}
}

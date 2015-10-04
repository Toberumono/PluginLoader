package toberumono.plugin;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import toberumono.utils.files.LoggedFileWalker;
import toberumono.utils.functions.ExceptedBiFunction;

public class PluginWalker extends LoggedFileWalker {
	private final PackageKernel pk;
	private final ReadWriteLock lock;
	private final Predicate<Path> isLoaded;
	private final ClassLoader loader;
	private final BiConsumer<String, ClassLoader> onFind;
	private final ExceptedBiFunction<Path, ClassLoader, ClassLoader> registerClassLoader;
	private final Set<String> blacklistedPackages;
	
	public PluginWalker(ClassLoader loader, ExceptedBiFunction<Path, ClassLoader, ClassLoader> registerClassLoader, Set<String> blacklistedPackages, Predicate<Path> isLoaded,
			BiConsumer<String, ClassLoader> onFind, ReadWriteLock lock, Logger log) {
		this(loader, registerClassLoader, Collections.unmodifiableSet(blacklistedPackages), isLoaded, onFind, lock, log, new PackageKernel());
	}
	
	private PluginWalker(ClassLoader loader, ExceptedBiFunction<Path, ClassLoader, ClassLoader> registerClassLoader, Set<String> blacklistedPackages, Predicate<Path> isLoaded,
			BiConsumer<String, ClassLoader> onFind, ReadWriteLock lock, Logger log, PackageKernel pk) {
		super("Started Scanning", "Scanned", "Finished Scanning", p -> {
			String name = p.getFileName().toString();
			return (name.endsWith(".jar") || name.endsWith(".zip") || name.endsWith(".class")) && !isLoaded.test(p);
		}, p -> pk.depth == 0 || (!blacklistedPackages.contains(pk.pack + p.getFileName().toString()) && !isLoaded.test(p)), null, null, log);
		this.pk = pk;
		this.lock = lock;
		this.onFind = onFind;
		this.loader = loader;
		this.isLoaded = isLoaded;
		this.registerClassLoader = registerClassLoader;
		this.blacklistedPackages = blacklistedPackages; //This set is guaranteed to be unmodifiable when passed to this constructor
	}
	
	@Override
	public FileVisitResult preVisitDirectoryAction(Path dir, BasicFileAttributes attrs) throws IOException {
		if (pk.depth > 0)
			pk.pack += dir.getFileName().toString() + ".";
		pk.depth++;
		return FileVisitResult.CONTINUE;
	}
	
	@Override
	public FileVisitResult visitFileAction(Path file, BasicFileAttributes attrs) throws IOException {
		String fname = file.getFileName().toString();
		if (fname.endsWith(".class"))
			onFind.accept(pk.pack + fname.substring(0, fname.length() - 6), loader);
		else {//Then this is a .jar or .zip file
			ClassLoader ucl;
			try {
				ucl = registerClassLoader.apply(file, loader);
				PluginWalker pw = new PluginWalker(ucl, registerClassLoader, blacklistedPackages, isLoaded, onFind, lock, log);
				try (FileSystem fs = FileSystems.newFileSystem(file, ucl)) {
					for (Path p : fs.getRootDirectories())
						Files.walkFileTree(p, pw);
				}
			}
			catch (IOException e) {
				throw e;
			}
			catch (Exception e) {
				log.log(Level.SEVERE, "Unable to load plugins from " + file, e);
			}
		}
		return FileVisitResult.CONTINUE;
	}
	
	@Override
	public FileVisitResult postVisitDirectoryAction(Path dir, IOException exc) throws IOException {
		pk.depth--;
		//Remove the directory name and the . from the computed package name
		//This also correctly handles directories with .'s in their names
		if (pk.depth > 0)
			pk.pack = pk.pack.substring(pk.pack.length() - (dir.getFileName().toString().length() + 1));
		return FileVisitResult.CONTINUE;
	}
	
}

class PackageKernel {
	String pack;
	int depth;
	
	PackageKernel() {
		init();
	}
	
	public void init() {
		pack = "";
		depth = 0;
	}
}

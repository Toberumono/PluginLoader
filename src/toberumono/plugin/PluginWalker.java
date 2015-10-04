package toberumono.plugin;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Logger;

public class PluginWalker implements FileVisitor<Path> {
	private final Function<Path, Boolean> loaded;
	private String pack;
	private int successful, depth;
	private BiFunction<Path, IOException, FileVisitResult> visitFailureResult;
	private final Queue<String> foundClasses;
	private final Set<String> blacklistedPackages;
	private final PluginClassLoader loader;
	private final Logger log;
	
	public PluginWalker(Function<Path, Boolean> loaded, Queue<String> foundClasses, Set<String> blacklistedPackages, final Logger log, PluginClassLoader loader) {
		this.loaded = loaded;
		init();
		visitFailureResult = (p, e) -> {
			log.warning("Failed to visit " + p);
			return FileVisitResult.CONTINUE;
		};
		this.foundClasses = foundClasses;
		this.loader = loader;
		this.blacklistedPackages = blacklistedPackages;
		this.log = log;
	}
	
	public void init() {
		pack = "";
		successful = 0;
		depth = 0;
	}
	
	@Override
	public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
		if (loaded.apply(dir))
			return FileVisitResult.SKIP_SUBTREE;
		String newPack = pack + dir.getFileName(); //Update the package name to include the directory
		if (blacklistedPackages.contains(newPack)) {
			log.warning("Encountered blacklisted package '" + newPack + "' in '" + dir.subpath(0, dir.getNameCount() - depth - 1));
			return FileVisitResult.SKIP_SUBTREE;
		}
		pack = newPack + "."; //We add the '.' here because the blacklist check requires it to not be there
		depth++;
		return FileVisitResult.CONTINUE;
	}
	
	@Override
	public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
		if (loaded.apply(file))
			return FileVisitResult.CONTINUE;
		String fname = file.getFileName().toString();
		if (fname.endsWith(".jar") || fname.endsWith(".zip")) {
			loader.addPath(file);
			FileSystem j = FileSystems.newFileSystem(file, null);
			PluginWalker walker = new PluginWalker(loaded, foundClasses, blacklistedPackages, log, loader);
			for (Path p : j.getRootDirectories())
				Files.walkFileTree(p, walker);
			successful += walker.getNumLoaded();
		}
		else if (fname.endsWith(".class")) {
			foundClasses.add(fname.substring(0, fname.lastIndexOf(".class")).replaceAll(PluginLoader.separator, "."));
			successful++;
		}
		return FileVisitResult.CONTINUE;
	}
	
	@Override
	public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
		return visitFailureResult.apply(file, exc);
	}
	
	@Override
	public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
		depth--;
		pack = pack.substring(0, pack.length() - dir.getFileName().toString().length() - 1); //Roll the package name back
		return FileVisitResult.CONTINUE;
	}
	
	/**
	 * @return the names of the classes that this {@link PluginWalker} has encountered
	 */
	public Queue<String> getFoundClasses() {
		return foundClasses;
	}
	
	/**
	 * @return the {@link PluginClassLoader} that the {@link PluginWalker} is updating
	 */
	public PluginClassLoader getClassLoader() {
		return loader;
	}
	
	public int getNumLoaded() {
		return successful;
	}
	
	/**
	 * @return the {@link FileVisitResult} returned by {@link #visitFileFailed(Path, IOException)}
	 */
	public final BiFunction<Path, IOException, FileVisitResult> getVisitFailureResult() {
		return visitFailureResult;
	}
	
	/**
	 * @param visitFailureResult
	 *            the {@link FileVisitResult} to be returned by {@link #visitFileFailed(Path, IOException)}
	 */
	public final void setVisitFailureResult(BiFunction<Path, IOException, FileVisitResult> visitFailureResult) {
		this.visitFailureResult = visitFailureResult;
	}
}

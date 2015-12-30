package toberumono.plugin.manager;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import toberumono.utils.files.LoggedFileWalker;
import toberumono.utils.functions.IOExceptedFunction;

public class PluginWalker extends LoggedFileWalker {
	private final PackageKernel pk;
	private final Predicate<Path> isLoaded;
	private final Collection<String> blacklistedPackages;
	private final Consumer<String> onFind;
	private final IOExceptedFunction<Path, FileSystem> fileSystemMaker;
	
	public PluginWalker(Collection<String> blacklistedPackages, Predicate<Path> isLoaded, Consumer<String> onFind, IOExceptedFunction<Path, FileSystem> fileSystemMaker, Logger log) {
		this(blacklistedPackages, isLoaded, onFind, fileSystemMaker, log, new PackageKernel());
	}
	
	private PluginWalker(Collection<String> blacklistedPackages, Predicate<Path> isLoaded, Consumer<String> onFind, IOExceptedFunction<Path, FileSystem> fileSystemMaker, Logger log,
			PackageKernel pk) {
		super("Started Scanning", "Scanned", "Finished Scanning", p -> {
			String name = p.getFileName().toString();
			return (name.endsWith(".jar") || name.endsWith(".zip") || name.endsWith(".class")) && !isLoaded.test(p);
		}, p -> pk.depth == 0 || (!blacklistedPackages.contains(pk.pack + p.getFileName().toString()) && !isLoaded.test(p)), null, null, log);
		this.pk = pk;
		this.isLoaded = isLoaded;
		this.blacklistedPackages = blacklistedPackages;
		this.onFind = onFind;
		this.fileSystemMaker = fileSystemMaker;
	}
	
	@Override
	public FileVisitResult preVisitDirectoryAction(Path dir, BasicFileAttributes attrs) throws IOException {
		pk.descend(dir.getFileName().toString());
		return FileVisitResult.CONTINUE;
	}
	
	@Override
	public FileVisitResult visitFileAction(Path file, BasicFileAttributes attrs) throws IOException {
		String fname = file.getFileName().toString();
		if (fname.endsWith(".class"))
			onFind.accept(pk.makeClassName(fname));
		else {//Then this is a .jar or .zip file
			try {
				PluginWalker pw = new PluginWalker(blacklistedPackages, isLoaded, onFind, fileSystemMaker, log, new PackageKernel());
				try (FileSystem fs = fileSystemMaker.apply(file)) {
					for (Path p : fs.getRootDirectories())
						Files.walkFileTree(p, pw);
				}
			}
			catch (Exception e) {
				log.log(Level.SEVERE, "Unable to load plugins from " + file, e);
			}
		}
		return FileVisitResult.CONTINUE;
	}
	
	@Override
	public FileVisitResult postVisitDirectoryAction(Path dir, IOException exc) throws IOException {
		pk.ascend(dir.getFileName().toString());
		return FileVisitResult.CONTINUE;
	}
	
}

class PackageKernel {
	String pack;
	int depth;
	
	PackageKernel() {
		reset();
	}
	
	public void reset() {
		pack = "";
		depth = 0;
	}
	
	public void descend(String fileName) {
		if (depth > 0)
			pack += fileName + ".";
		depth++;
	}
	
	public void ascend(String fileName) {
		depth--;
		//Remove the directory name and the . from the computed package name
		//This also correctly handles directories with .'s in their names
		if (depth > 0)
			pack = pack.substring(pack.length() - (fileName.length() + 1));
	}
	
	public String makeClassName(String filename) {
		if (filename.endsWith(".class"))
			return pack + filename.substring(0, filename.length() - 6); //Removes the .class extension
		return pack + filename; //No .class extension to remove
	}
}

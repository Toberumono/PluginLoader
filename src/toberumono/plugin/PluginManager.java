package toberumono.plugin;

import java.io.FilePermission;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import toberumono.structures.Preferences;
import toberumono.utils.general.MutedLogger;

public class PluginManager {
	private static final String sep = FileSystems.getDefault().getSeparator();
	
	private final Map<Path, ClassLoader> loaders;
	private final Path settingsDirectory, dataDirectory;
	private final Logger log;
	
	public PluginManager(Path settingsDirectory, Path dataDirectory, Logger log) {
		loaders = new HashMap<>();
		this.log = log == null ? MutedLogger.getMutedLogger() : log;
		this.dataDirectory = dataDirectory;
		this.settingsDirectory = settingsDirectory;
		if (System.getSecurityManager() == null)
			System.setSecurityManager(new PluginSecurityManager());
	}
	
	private void loadPlugin(Class<?> pluginClass) {
	
	}
	
	public void loadPlugin(Path location) throws IOException, ClassNotFoundException {
		final CodeSource nullSource = new CodeSource(location.toUri().toURL(), (CodeSigner[]) null);
		PermissionCollection perms = new Permissions();
		FileSystem pluginSystem = FileSystems.newFileSystem(location, null);
		Path pluginFile = pluginSystem.getPath("Plugin");
		if (!Files.exists(pluginFile))
			throw new IOException("Unable to get the plugin data"); //TODO Replace this with a better exception
			
		Preferences config = Preferences.read(pluginFile);
		perms.add(new FilePermission(settingsDirectory.resolve(config.get("Name").get(0) + ".json").toString(), "read,write"));
		
		String tempData = dataDirectory.resolve(config.get("Name").get(0)).toString();
		if (!tempData.endsWith(sep))
			tempData += sep;
		tempData += "-";
		perms.add(new FilePermission(tempData, "read,write,execute,delete"));
		
		//This is going to include access to the plugin's data and settings, and a permission that will check access against a map of plugins
		AccessControlContext pluginContext = new AccessControlContext(new ProtectionDomain[]{new ProtectionDomain(nullSource, perms)});
		URLClassLoader classLoader = AccessController.doPrivileged(new PrivilegedAction<URLClassLoader>() {
			@Override
			public URLClassLoader run() {
				return URLClassLoader.newInstance(new URL[]{nullSource.getLocation()});
			}
		}, pluginContext);
		loaders.put(location, classLoader);
		for (String className : config.get("Classes")) {
			Class<?> plugin = classLoader.loadClass(className);
			
		}
	}
}

package toberumono.plugin;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

/**
 * This class exposes the addURL method in {@link URLClassLoader} method and adds a quick convenience
 * method for {@link Path Paths}.
 * 
 * @author Toberumono
 */
public class PluginClassLoader extends URLClassLoader {
	
	public PluginClassLoader(URL... urls) {
		super(urls);
	}
	
	protected void addPath(Path path) throws MalformedURLException {
		super.addURL(path.toUri().toURL());
	}
	
	@Override
	protected void addURL(URL url) {
		super.addURL(url);
	}
}

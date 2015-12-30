package toberumono.plugin.manager;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import toberumono.plugin.annotations.Dependency;
import toberumono.plugin.annotations.PluginDescription;
import toberumono.plugin.exceptions.PluginConstructionException;

public class PluginData<T> {
	private final Class<? extends T> clazz;
	private final PluginDescription annotation;
	private final String parent;
	private final Dependency[] dependencies;
	private final Map<String, PluginData<T>> resolvedDependencies;
	private PluginData<? extends T> resolvedParent;
	private T instance;
	private boolean linkable;
	
	public PluginData(Class<? extends T> clazz) {
		this.clazz = clazz;
		annotation = clazz.getAnnotation(PluginDescription.class);
		parent = annotation.parent().equalsIgnoreCase("[none]") ? null : annotation.parent();
		dependencies = clazz.getAnnotationsByType(Dependency.class);
		resolvedDependencies = new LinkedHashMap<>();
		resolvedParent = null;
		instance = null;
		linkable = false;
	}
	
	List<RequestedDependency<T>> generateDependencyRequests() {
		List<RequestedDependency<T>> requests = new ArrayList<>();
		if (parent != null && resolvedParent == null)
			requests.add(new RequestedDependency<>(parent, pd -> { //TODO We should probably get rid of the parent plugin being a dependency
				resolvedParent = pd;
				if (!resolvedDependencies.containsKey(pd.getDescription().id()))
					resolvedDependencies.put(pd.getDescription().id(), pd);
			}));
		for (Dependency dependency : dependencies)
			if (!resolvedDependencies.containsKey(dependency.id()))
				requests.add(new RequestedDependency<>(dependency, pd -> resolvedDependencies.put(pd.getDescription().id(), pd)));
		return requests;
	}
	
	Set<Entry<String, PluginData<T>>> getResolvedDependencies() {
		return resolvedDependencies.entrySet();
	}
	
	boolean isConstructed() {
		return instance != null;
	}
	
	T construct(Object[] args) throws PluginConstructionException {
		if (!isLinkable())
			throw new PluginConstructionException("Attempted to construct the plugin, " + annotation.id() + ", but it was not linkable.");
		if (instance != null) //Plugins can only be constructed once.
			return instance;
		Class<?>[] types = (Class[]) Array.newInstance(Class.class, args.length);
		for (int i = 0; i < args.length; i++)
			types[i] = args[i].getClass();
		try {
			return instance = clazz.getConstructor(types).newInstance(args);
		}
		catch (Exception e) {
			throw new PluginConstructionException(e);
		}
	}
	
	/**
	 * A plugin is linkable iff all of it's dependencies have been resolved and all of those dependencies are themselves
	 * linkable.
	 * 
	 * @return {@code true} iff the plugin is linkable
	 */
	public boolean isLinkable() {
		if (linkable)
			return true;
		LinkabilityTester<T> tester = new LinkabilityTester<>();
		return linkable = tester.isLinkable(this);
	}
	
	void markLinkable() {
		linkable = true;
	}
	
	/**
	 * Determines whether this plugin's dependencies have been resolved.
	 * 
	 * @return {@code true} iff this plugin's parent has been resolved (if the plugin has no parent, then the parent is
	 *         always considered to be resolved) and all of the plugin's required plugins have been resolved.
	 */
	public boolean isResolved() {
		if (parent != null && resolvedParent == null)
			return false;
		if (resolvedDependencies.size() < dependencies.length)
			return false;
		return true;
	}
	
	/**
	 * Determines whether the {@link ManageablePlugin} described by the {@link PluginData} satisfies the given requirement.
	 * 
	 * @param requirement
	 *            a {@link Dependency} annotation containing the required id and version
	 * @return {@code true} iff the {@link ManageablePlugin} described by the {@link PluginData} has been resolved and the ID
	 *         and version of the described {@link ManageablePlugin} match the ID and version of the given requirement
	 */
	public boolean satisfiesRequirement(Dependency requirement) {
		return annotation.id().equals(requirement.id()) && (requirement.version().equalsIgnoreCase("[any]") || annotation.version().equals(requirement.version()));
	}
	
	/**
	 * @return the {@link PluginDescription} annotation that describes the plugin
	 */
	public PluginDescription getDescription() {
		return this.annotation;
	}
}

class LinkabilityTester<T> {
	private final Map<String, PluginData<T>> visited;
	private final Map<String, PluginData<T>> dependencies;
	
	public LinkabilityTester() {
		visited = new HashMap<>();
		dependencies = new HashMap<>();
	}
	
	public boolean isLinkable(PluginData<T> plugin) {
		if (!generateDependencies(plugin))
			return false;
		//At this point, dependencies holds every dependency that must be satisfied other than those that are explicitly circular.
		//Therefore, so long as every plugin in this map can be resolved, every visited plugin must be linkable.
		//Furthermore, because every plugin in the map is tested for resolvability prior to being added, we know that all plugins
		//in the map are resolvable
		//Thus, we can flag every plugin in the visited map as linkable.
		for (PluginData<T> pd : visited.values())
			pd.markLinkable();
		return true;
	}
	
	private boolean generateDependencies(PluginData<T> plugin) {
		if (plugin.isLinkable())
			return true;
		if (!plugin.isResolved())
			return false;
		if (visited.containsKey(plugin.getDescription().id())) {
			dependencies.remove(plugin.getDescription().id()); //If we're already checking all of the dependencies for a plugin, then we don't need to directly check it.
			return true;
		}
		visited.put(plugin.getDescription().id(), plugin);
		for (Entry<String, PluginData<T>> dependency : plugin.getResolvedDependencies()) {
			if (visited.containsKey(dependency.getKey()))
				continue;
			dependencies.put(dependency.getKey(), dependency.getValue());
			if (!generateDependencies(dependency.getValue()))
				return false;
		}
		return true;
	}
}

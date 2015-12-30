package toberumono.plugin.manager;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import toberumono.plugin.annotations.Dependency;
import toberumono.plugin.annotations.PluginDescription;
import toberumono.plugin.exceptions.PluginConstructionException;
import toberumono.plugin.exceptions.UnlinkablePluginException;

public class PluginData<T> {
	private final Class<? extends T> clazz;
	private final PluginDescription annotation;
	private final String parent;
	private final Dependency[] dependencies;
	private final Map<String, PluginData<T>> resolvedDependencies;
	private final Logger logger;
	private final Lock constructionLock;
	private final ReadWriteLock linkabilityTestLock, dependenciesLock;
	private PluginData<? extends T> resolvedParent;
	private T instance;
	private Boolean linkable;
	
	public PluginData(Class<? extends T> clazz) {
		this(clazz, Logger.getLogger("toberumono.plugin.manager.PluginData"));
	}
	public PluginData(Class<? extends T> clazz, Logger logger) {
		this.clazz = clazz;
		this.logger = logger;
		annotation = clazz.getAnnotation(PluginDescription.class);
		parent = annotation.parent().equalsIgnoreCase("[none]") ? null : annotation.parent();
		dependencies = clazz.getAnnotationsByType(Dependency.class);
		resolvedDependencies = new LinkedHashMap<>();
		constructionLock = new ReentrantLock();
		linkabilityTestLock = new ReentrantReadWriteLock();
		dependenciesLock = new ReentrantReadWriteLock();
		resolvedParent = null;
		instance = null;
		linkable = false;
	}
	
	protected List<RequestedDependency<T>> generateDependencyRequests() {
		List<RequestedDependency<T>> requests = new ArrayList<>();
		synchronized (resolvedParent) {
			if (parent != null && resolvedParent == null)
				requests.add(new RequestedDependency<>(this.getDescription().id(), parent, "[any]", pd -> { //TODO We might want to get rid of the parent plugin being a dependency
					synchronized (dependenciesLock.writeLock()) {
						resolvedParent = pd;
						if (!resolvedDependencies.containsKey(pd.getDescription().id()))
							resolvedDependencies.put(pd.getDescription().id(), pd);
						logger.log(Level.INFO, "Resolved the parent plugin, " + parent + ", of " + getDescription().id() + " with {" +
								pd.getDescription().id() + ", " + pd.getDescription().version() + "}");
					}
				}));
		}
		synchronized (dependenciesLock.readLock()) {
			for (Dependency dependency : dependencies)
				if (!resolvedDependencies.containsKey(dependency.id()))
					requests.add(new RequestedDependency<>(this.getDescription().id(), dependency, pd -> {
						synchronized (dependenciesLock.writeLock()) {
							resolvedDependencies.put(pd.getDescription().id(), pd);
							logger.log(Level.INFO, "Resolved the dependency, {" + dependency.id() + ", " + dependency.version() + "}, for " +
									getDescription().id() + " with {" + pd.getDescription() + ", " + pd.getDescription().version() + "}");
						}
					}));
		}
		return requests;
	}
	
	protected Set<Entry<String, PluginData<T>>> getResolvedDependencies() {
		synchronized (dependenciesLock.readLock()) {
			return resolvedDependencies.entrySet();
		}
	}
	
	public boolean isConstructed() {
		synchronized (constructionLock) {
			return instance != null;
		}
	}
	
	public T construct(Object[] args) throws PluginConstructionException, UnlinkablePluginException {
		synchronized (constructionLock) {
			if (!isLinkable(true))
				throw new UnlinkablePluginException("Attempted to construct the plugin, " + annotation.id() + ", but it was not linkable.");
			if (instance != null) { //Plugins can only be constructed once.
				logger.log(Level.WARNING, "Attempted to construct the plugin, " + annotation.id() +
						", but it has already been constructed.  Returning existing instance instead.");
				return instance;
			}
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
	}
	
	/**
	 * A plugin is linkable iff all of it's dependencies have been resolved and all of those dependencies are themselves
	 * linkable.
	 * 
	 * @return {@code true} iff the plugin is linkable
	 */
	public boolean isLinkable() {
		return isLinkable(true);
	}
	public boolean isLinkable(boolean performTest) {
		synchronized (linkabilityTestLock.readLock()) {
			if (linkable)
				return true;
			if (!performTest)
				return false;
		}
		return linkablilityTest();
	}
	
	private boolean linkablilityTest() {
		synchronized (linkabilityTestLock.writeLock()) {
			if (isLinkable(false))
				return true;
			Map<String, PluginData<T>> visited = new HashMap<>();
			if (generateDependencyMap(visited, this)) {
				/*
				 * At this point, visited holds every dependency that must be resolved in order for all of the plugins in any circular
				 * dependencies that this plugin is a part of to be linkable.
				 * Therefore, so long as every plugin in visited can be resolved, every plugin in visited is linkable.
				 * Furthermore, because every plugin in visited is tested for resolvability while being added, we know that all plugins
				 * in visited are resolvable.
				 * Thus, we can flag every plugin in visited as linkable.
				 */
				visited.values().forEach(PluginData<T>::markLinkable);
				return true;
			}
			return false;
		}
	}
	private boolean generateDependencyMap(Map<String, PluginData<T>> visited, PluginData<T> plugin) {
		if (plugin.isLinkable(false))
			return true;
		if (!plugin.isResolved())
			return false;
		if (visited.containsKey(plugin.getDescription().id()))
			return true;
		visited.put(plugin.getDescription().id(), plugin);
		for (Entry<String, PluginData<T>> dependency : plugin.getResolvedDependencies())
			if (!visited.containsKey(dependency.getKey()) && !generateDependencyMap(visited, dependency.getValue()))
				return false;
		return true;
	}
	
	private void markLinkable() {
		synchronized (linkabilityTestLock.writeLock()) {
			if (!isLinkable(false))
				linkable = true;
		}
	}
	
	/**
	 * Determines whether this plugin's dependencies have been resolved.
	 * 
	 * @return {@code true} iff this plugin's parent has been resolved (if the plugin has no parent, then the parent is
	 *         always considered to be resolved) and all of the plugin's required plugins have been resolved.
	 */
	public boolean isResolved() {
		synchronized (dependenciesLock.readLock()) {
			if (parent != null && resolvedParent == null)
				return false;
			if (resolvedDependencies.size() < dependencies.length)
				return false;
			return true;
		}
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

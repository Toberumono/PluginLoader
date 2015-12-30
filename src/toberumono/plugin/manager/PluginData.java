package toberumono.plugin.manager;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

/**
 * A container for managing the metadata associated with a plugin by a {@link PluginManager}.
 * 
 * @author Toberumono
 * @param <T>
 *            the type of the plugin being managed
 */
public class PluginData<T> {
	private final Class<? extends T> clazz;
	private final PluginDescription description;
	private final String parent;
	private final Dependency[] dependencies;
	private final Collection<String> requiredDependencies;
	private final Map<String, PluginData<T>> resolvedDependencies;
	private final Logger logger;
	private final Lock constructionLock, parentLock;
	private final ReadWriteLock linkabilityTestLock, dependenciesLock;
	private PluginData<? extends T> resolvedParent;
	private T instance;
	private Boolean linkable;
	private Integer hashCode;
	
	/**
	 * Constructs a new {@link PluginData} container with the default {@link Logger}
	 * ({@code Logger.getLogger("toberumono.plugin.manager.PluginData")}).
	 * 
	 * @param clazz
	 *            the {@link Class} corresponding to the plugin that this {@link PluginData} instance will be managing
	 * @see #PluginData(Class, Logger)
	 */
	public PluginData(Class<? extends T> clazz) {
		this(clazz, Logger.getLogger("toberumono.plugin.manager.PluginData"));
	}
	
	/**
	 * Constructs a new {@link PluginData} container with the given {@link Logger}.
	 * 
	 * @param clazz
	 *            the {@link Class} corresponding to the plugin that this {@link PluginData} instance will be managing
	 * @param logger
	 *            the {@link Logger} to which logging data should be sent
	 */
	public PluginData(Class<? extends T> clazz, Logger logger) {
		this.clazz = clazz;
		this.logger = logger;
		description = clazz.getAnnotation(PluginDescription.class);
		parent = description.parent().length() == 0 || description.parent().equalsIgnoreCase("[none]") ? null : description.parent();
		dependencies = clazz.getAnnotationsByType(Dependency.class);
		requiredDependencies = new LinkedHashSet<>();
		for (Dependency dep : dependencies)
			if (dep.required())
				requiredDependencies.add(dep.id());
		resolvedDependencies = new LinkedHashMap<>();
		constructionLock = new ReentrantLock();
		parentLock = new ReentrantLock();
		linkabilityTestLock = new ReentrantReadWriteLock();
		dependenciesLock = new ReentrantReadWriteLock();
		resolvedParent = null;
		instance = null;
		linkable = false;
		hashCode = null;
	}
	
	protected List<RequestedDependency<T>> generateDependencyRequests() {
		List<RequestedDependency<T>> requests = new ArrayList<>();
		synchronized (parentLock) {
			if (parent != null && resolvedParent == null)
				requests.add(new RequestedDependency<>(this.getID(), parent, "[any]", pd -> { //TODO We might want to get rid of the parent plugin being a dependency
					synchronized (parentLock) {
						if (resolvedParent != null)
							return;
						synchronized (dependenciesLock.writeLock()) {
							if (resolvedDependencies.containsKey(pd.getID())) //If we've already resolved a plugin that matches the parent plugin's ID, use that.
								pd = resolvedDependencies.get(pd.getID());
							else
								resolvedDependencies.put(pd.getID(), pd);
							resolvedParent = pd;
							logger.log(Level.INFO, "Resolved the parent plugin, " + parent + ", of " + getID() + " with {" +
									pd.getID() + ", " + pd.getVersion() + "}");
						}
					}
				}));
		}
		synchronized (dependenciesLock.readLock()) {
			for (Dependency dependency : dependencies)
				if (!resolvedDependencies.containsKey(dependency.id()))
					requests.add(new RequestedDependency<>(this.getID(), dependency, pd -> {
						synchronized (dependenciesLock.writeLock()) {
							if (resolvedDependencies.containsKey(pd.getID()))
								return;
							resolvedDependencies.put(pd.getID(), pd);
							logger.log(Level.INFO, "Resolved the dependency, {" + dependency.id() + ", " + dependency.version() + "}, for " +
									getID() + " with {" + pd.getID() + ", " + pd.getVersion() + "}");
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
	
	/**
	 * Determines whether this plugin has been constructed.<br>
	 * A plugin has been constructed if the instance stored in this {@link PluginData} is non-null.
	 * 
	 * @return {@code true} iff this plugin has been constructed
	 */
	public boolean isConstructed() {
		synchronized (constructionLock) {
			return instance != null;
		}
	}
	
	/**
	 * Attempts to construct an instance of the plugin with the given arguments if the plugin has not already been
	 * constructed. If the plugin has already been constructed, it returns the constructed instance of the plugin.<br>
	 * Plugins are only constructible if they are {@link #isLinkable() linkable}.
	 * 
	 * @param args
	 *            the arguments to pass to the constructor
	 * @return a constructed instance of the plugin
	 * @throws PluginConstructionException
	 *             if a constructor matching the given arguments cannot be found or some other problem occurs while
	 *             constructing the plugin
	 * @throws UnlinkablePluginException
	 *             if the plugin is not linkable when this method is called
	 * @see #isLinkable()
	 */
	public T construct(Object[] args) throws PluginConstructionException, UnlinkablePluginException {
		synchronized (constructionLock) {
			if (!isLinkable(true))
				throw new UnlinkablePluginException("Attempted to construct the plugin, " + description.id() + ", but it was not linkable.");
			if (instance != null) { //Plugins can only be constructed once.
				logger.log(Level.WARNING, "Attempted to construct the plugin, " + description.id() +
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
	 * linkable.<br>
	 * If the plugin has not already been marked as linkable, then calling this method will result in an attempt to determine
	 * if the plugin is linkable, which can become somewhat expensive as the number of plugins increases. Therefore, it is
	 * recommended that this method be called as infrequently as possible.<br>
	 * Forwards to {@link #isLinkable(boolean) isLinkable(true)}.
	 * 
	 * @return {@code true} iff the plugin is linkable
	 * @see #isLinkable(boolean)
	 */
	public boolean isLinkable() {
		return isLinkable(true);
	}
	
	/**
	 * /** A plugin is linkable iff all of it's dependencies have been resolved and all of those dependencies are themselves
	 * linkable.<br>
	 * If the plugin has not already been marked as linkable and <tt>performTest</tt> is {@code true}, then calling this
	 * method will result in an attempt to determine if the plugin is linkable, which can become somewhat expensive as the
	 * number of plugins increases. Therefore, it is recommended that this method be called with <tt>performTest</tt> as
	 * {@code true} as infrequently as possible.
	 * 
	 * @param performTest
	 *            whether to attempt to determine whether this plugin is linkable if it has not already been marked as
	 *            linkable
	 * @return {@code true} iff the plugin is linkable
	 * @see #isLinkable()
	 */
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
			if (isLinkable(false)) //To account for the brief window in which the thread calling this method does not have either of the linkabilityTestLock's sub-locks
				return true;
			Map<String, PluginData<T>> visited = new HashMap<>();
			if (generateDependencyMap(visited, this)) {
				/*
				 * At this point, visited holds every dependency (and those dependencies' dependencies (recursively)) that must be resolved in order
				 * for this plugin and all of the plugins in any circular dependencies that this plugin is a part of to be linkable.
				 * Therefore, so long as every plugin in visited can be resolved, every plugin in visited is linkable.
				 * Furthermore, because every plugin in visited is tested for resolvability while being added, and the generation aborts and returns false if any of
				 * the visited plugins fail the test, we know that all plugins in visited are resolvable.
				 * Thus, we can flag every plugin in visited as linkable.
				 */
				visited.values().forEach(PluginData<T>::markLinkable);
				return true;
			}
			return false;
		}
	}
	
	/**
	 * Fills in <tt>visited</tt> with all of the dependencies that must be resolved in order for this plugin and all plugins
	 * with which it has a circular dependency to be linkable.
	 * 
	 * @param visited
	 *            a {@link Map} containing all of the visited plugins
	 * @param plugin
	 *            the plugin currently being tested and (if applicable) added to the map
	 * @return {@code true} iff all plugins in the map are resolved
	 */
	private boolean generateDependencyMap(Map<String, PluginData<T>> visited, PluginData<T> plugin) {
		if (plugin.isLinkable(false))
			return true;
		if (!plugin.isResolved())
			return false;
		if (visited.containsKey(plugin.getID()))
			return true;
		visited.put(plugin.getID(), plugin);
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
			if (resolvedDependencies.keySet().containsAll(requiredDependencies))
				return false;
			return true;
		}
	}
	
	/**
	 * @return the {@link PluginDescription} annotation that describes the plugin
	 */
	public PluginDescription getDescription() {
		return description;
	}
	
	/**
	 * @return the ID of the plugin being managed
	 */
	public String getID() {
		return getDescription().id();
	}
	
	/**
	 * @return the version of the plugin being managed
	 */
	public String getVersion() {
		return getDescription().version();
	}
	
	@Override
	public boolean equals(Object other) {
		if (other == null || !(other instanceof PluginData))
			return false;
		PluginData<?> o = (PluginData<?>) other;
		if (!clazz.equals(o.clazz) || isLinkable(false) != o.isLinkable(false) || !description.equals(o.description) || dependencies.length != o.dependencies.length ||
				(parent == null && o.parent != null || (parent != null && !parent.equals(o.parent))) ||
				(resolvedParent == null && o.resolvedParent != null || (resolvedParent != null && !resolvedParent.equals(o.resolvedParent))) ||
				!resolvedDependencies.equals(o.resolvedDependencies) || (instance == null && o.instance != null || (instance != null && !instance.equals(o.instance))))
			return false;
		outerDependencyLoop: for (int i = 0; i < dependencies.length; i++) {
			for (int j = 0; j < o.dependencies.length; j++)
				if (dependencies[i].equals(o.dependencies[j]))
					continue outerDependencyLoop;
			return false;
		}
		return true;
	}
	
	@Override
	public int hashCode() {
		if (hashCode != null)
			return hashCode;
		int hash = 17;
		hash = hash * 31 + clazz.hashCode();
		hash = hash * 31 + description.hashCode();
		hash = hash * 31 + Arrays.hashCode(dependencies);
		return hashCode = hash;
	}
}

package toberumono.plugin.manager;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
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
import toberumono.plugin.annotations.PluginActivator;
import toberumono.plugin.annotations.PluginDeactivator;
import toberumono.plugin.annotations.PluginDescription;
import toberumono.plugin.exceptions.PluginActivationException;
import toberumono.plugin.exceptions.PluginConstructionException;
import toberumono.plugin.exceptions.PluginDeactivationException;
import toberumono.plugin.exceptions.UnlinkablePluginException;
import toberumono.plugin.user.PluginUser;
import toberumono.structures.collections.lists.SortedList;

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
	private final DependencyContainer parent;
	private final DependencyContainer[] dependencies;
	private final Collection<String> requiredDependencies;
	private final Map<String, PluginData<T>> resolvedDependencies;
	private final Logger logger;
	private final Lock constructionLock, parentLock;
	private final ReadWriteLock linkabilityTestLock, dependenciesLock;
	private final List<RequestedDependency<T>> satisfiedDependencies;
	private PluginData<? extends T> resolvedParent;
	private T instance;
	private Boolean linkable;
	private final int hashCode;
	private Method[] activators, deactivators;
	private int activatorsIndex, deactivatorsIndex;
	
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
		parent = description.parent().length() == 0 || description.parent().equalsIgnoreCase("[none]") ? null
				: new DependencyContainer(description.parent(), "[any]", true);
		Dependency[] dependencies = clazz.getAnnotationsByType(Dependency.class);
		this.dependencies = new DependencyContainer[dependencies.length];
		requiredDependencies = new LinkedHashSet<>();
		for (int i = 0; i < dependencies.length; i++) {
			this.dependencies[i] =
					new DependencyContainer(dependencies[i].id(), dependencies[i].version(), dependencies[i].required());
			if (this.dependencies[i].required())
				requiredDependencies.add(this.dependencies[i].id());
		}
		resolvedDependencies = new LinkedHashMap<>();
		satisfiedDependencies = new LinkedList<>();
		constructionLock = new ReentrantLock();
		parentLock = new ReentrantLock();
		linkabilityTestLock = new ReentrantReadWriteLock();
		dependenciesLock = new ReentrantReadWriteLock();
		resolvedParent = null;
		instance = null;
		linkable = false;
		int hash = 17;
		hash = hash * 31 + this.clazz.hashCode();
		hash = hash * 31 + this.description.hashCode();
		hash = hash * 31 + Arrays.hashCode(this.dependencies);
		hashCode = hash;
		activators = null;
		activatorsIndex = 0;
		deactivators = null;
		deactivatorsIndex = 0;
	}
	
	/**
	 * Attempts to satisfy the given {@link RequestedDependency}.
	 * 
	 * @param dependency
	 *            the {@link RequestedDependency}
	 * @return {@code true} iff the {@link RequestedDependency} was satisfied
	 */
	public boolean satisfyDependency(RequestedDependency<T> dependency) {
		synchronized (satisfiedDependencies) {
			if (!dependency.trySatisfy(this))
				return false;
			satisfiedDependencies.add(dependency);
			return true;
		}
	}
	
	/**
	 * Attempts to remove the plugin described by the {@link PluginData} from its {@link PluginManager}.
	 * 
	 * @return {@code true} iff the plugin was successfully removed
	 */
	public boolean removePlugin() {
		throw new UnsupportedOperationException("Plugin removal is not currently implemented."); //TODO May be implemented in the future
	}
	
	private class PluginDataRequestedDependency extends RequestedDependency<T> {
		
		public PluginDataRequestedDependency(DependencyContainer dependency, ReadWriteLock requestedDependenciesLock) {
			super(PluginData.this, dependency, requestedDependenciesLock);
		}
		
		@Override
		protected boolean applySatisfier(PluginData<T> satisfier) {
			synchronized (dependenciesLock.writeLock()) {
				String satID = satisfier.getID();
				if (resolvedDependencies.containsKey(satID))
					return false;
				resolvedDependencies.put(satID, satisfier);
				logger.log(Level.INFO, "Resolved the dependency, " + getDependency() + ", for " + getID() + " with {" +
						satID + ", " + satisfier.getVersion() + "}");
				return true;
			}
		}
		
		@Override
		protected boolean unapplySatisfier(PluginData<T> satisfier) {
			synchronized (dependenciesLock.writeLock()) {
				String satID = satisfier.getID();
				if (!resolvedDependencies.containsKey(satID))
					return false;
				resolvedDependencies.remove(satID);
				logger.log(Level.INFO, "Unresolved the dependency, " + getDependency() + ", for " + getID() + " with {" +
						satID + ", " + satisfier.getVersion() + "}");
				return true;
			}
		}
	}
	
	private class PluginDataParentRequestedDependency extends PluginDataRequestedDependency {
		
		public PluginDataParentRequestedDependency(DependencyContainer dependency, ReadWriteLock requestedDependenciesLock) {
			super(dependency, requestedDependenciesLock);
		}
		
		@Override
		protected boolean applySatisfier(PluginData<T> satisfier) {
			synchronized (parentLock) {
				if (resolvedParent != null)
					return false;
				synchronized (dependenciesLock.readLock()) {
					String satID = satisfier.getID();
					if (resolvedDependencies.containsKey(satID)) //If we've already resolved a plugin that matches the parent plugin's ID, use that.
						satisfier = resolvedDependencies.get(satID);
					resolvedParent = satisfier;
					logger.log(Level.INFO, "Resolved the parent plugin, " + parent + ", of " + getRequestorID() + " with {" +
							satID + ", " + satisfier.getVersion() + "}");
					return true;
				}
			}
		}
		
		@Override
		protected boolean unapplySatisfier(PluginData<T> satisfier) {
			synchronized (parentLock) {
				if (resolvedParent == null)
					return false;
				PluginData<? extends T> rpt = resolvedParent;
				resolvedParent = null;
				logger.log(Level.INFO, "Unresolved the parent plugin, " + rpt.getID() + ", of " + getRequestorID() +
						" with {" + rpt.getID() + ", " + rpt.getVersion() + "}");
				return true;
			}
		}
	}
	
	protected List<RequestedDependency<T>> generateDependencyRequests(ReadWriteLock requestedDependenciesLock) {
		List<RequestedDependency<T>> requests = new ArrayList<>();
		synchronized (parentLock) {
			if (parent != null && resolvedParent == null)
				requests.add(new PluginDataParentRequestedDependency(parent, requestedDependenciesLock));
		}
		synchronized (dependenciesLock.readLock()) {
			for (DependencyContainer dependency : dependencies)
				if (!resolvedDependencies.containsKey(dependency.id()))
					requests.add(new PluginDataRequestedDependency(dependency, requestedDependenciesLock));
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
	 * Plugins are only constructible if they are {@link #isLinkable() linkable}.<br>
	 * Once the plugin has been constructed, use of {@link #getInstance()} is recommended.
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
	 * @see #isConstructed()
	 * @see #getInstance()
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
	 * This method returns the already-constructed instance of the plugin that the {@link PluginData} or {@code null} if the
	 * plugin has not yet been constructed.
	 * 
	 * @return the constructed plugin if it has been constructed, otherwise {@code null}
	 */
	public T getInstance() {
		synchronized (constructionLock) {
			return instance;
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
	 * If the plugin has not already been marked as linkable and {@code performTest} is {@code true}, then calling this
	 * method will result in an attempt to determine if the plugin is linkable, which can become somewhat expensive as the
	 * number of plugins increases. Therefore, it is recommended that this method be called with {@code performTest} as
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
				for (PluginData<T> pd : visited.values())
					pd.markLinkable();
				return true;
			}
			return false;
		}
	}
	
	/**
	 * Fills in {@code visited} with all of the dependencies that must be resolved in order for this plugin and all plugins
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
	
	@SuppressWarnings("unused")
	private void unmarkLinkable() {
		synchronized (linkabilityTestLock.writeLock()) {
			if (isLinkable(false))
				linkable = false;
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
	 * This method is called by the {@link PluginUser} during the activation stage of the initialization process.
	 * 
	 * @param args
	 *            the arguments to be passed to the plugin's activators
	 * @return {@code true} if all of the activators were successfully called
	 * @throws PluginActivationException
	 *             if an error occurs while invoking an activator
	 */
	public synchronized boolean callActivators(Object[] args) throws PluginActivationException {
		if (!isConstructed())
			throw new PluginActivationException("Attempted to activate a plugin that is not yet constructed.");
		if (activators == null) {
			Method[] methods = clazz.getDeclaredMethods();
			List<Method> temp = new SortedList<>(methods.length, (a, b) -> ((Integer) a.getAnnotation(PluginActivator.class).priority()).compareTo(b.getAnnotation(PluginActivator.class).priority()));
			for (Method m : methods)
				if (m.getAnnotation(PluginActivator.class) != null) {
					if (Arrays.equals(args, m.getParameterTypes()))
						temp.add(m);
					else
						logger.log(Level.WARNING, "Encountered an activator, " + m.getName() + ", with invalid arguments, " + Arrays.toString(m.getParameterTypes()) +
								", while initializing the plugin, " + description.id() + ".");
				}
			activators = temp.toArray(new Method[temp.size()]);
		}
		try {
			AccessibleObject.setAccessible(activators, true); //TODO might not need to set accessible
			for (; activatorsIndex < activators.length; activatorsIndex++)
				try {
					activators[activatorsIndex].invoke(getInstance(), args);
				}
				catch (IllegalAccessException | IllegalArgumentException e) {
					throw new PluginActivationException(e);
				}
				catch (InvocationTargetException e) { //This exception wraps the actual exception
					throw new PluginActivationException(e.getCause());
				}
		}
		finally {
			AccessibleObject.setAccessible(activators, false);
		}
		return true;
	}
	
	/**
	 * This method is called by the {@link PluginUser} during the deactivation stage of the deinitialization process.
	 * 
	 * @param args
	 *            the arguments to be passed to the plugin's deactivators
	 * @return {@code true} if all of the deactivators were successfully called
	 * @throws PluginDeactivationException
	 *             if an error occurs while invoking a deactivator
	 */
	public synchronized boolean callDeactivators(Object[] args) throws PluginDeactivationException {
		if (!isConstructed())
			throw new PluginDeactivationException("Attempted to deactivate a plugin that is not yet constructed.");
		if (deactivators == null) {
			Method[] methods = clazz.getDeclaredMethods();
			List<Method> temp =
					new SortedList<>(methods.length, (a, b) -> ((Integer) a.getAnnotation(PluginDeactivator.class).priority()).compareTo(b.getAnnotation(PluginDeactivator.class).priority()));
			for (Method m : methods)
				if (m.getAnnotation(PluginDeactivator.class) != null) {
					if (Arrays.equals(args, m.getParameterTypes()))
						temp.add(m);
					else
						logger.log(Level.WARNING, "Encountered a deactivator, " + m.getName() + ", with invalid arguments, " + Arrays.toString(m.getParameterTypes()) +
								", while deinitializing the plugin, " + description.id() + ".");
				}
			deactivators = temp.toArray(new Method[temp.size()]);
		}
		try {
			AccessibleObject.setAccessible(deactivators, true); //TODO might not need to set accessible
			for (; deactivatorsIndex < deactivators.length; deactivatorsIndex++)
				try {
					deactivators[deactivatorsIndex].invoke(getInstance(), args);
				}
				catch (IllegalAccessException | IllegalArgumentException e) {
					throw new PluginDeactivationException(e);
				}
				catch (InvocationTargetException e) { //This exception wraps the actual exception
					throw new PluginDeactivationException(e.getCause());
				}
		}
		finally {
			AccessibleObject.setAccessible(deactivators, false);
		}
		return true;
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
		PluginData<?> o = basicEquals(other);
		if (o == null)
			return false;
		int j = 0;
		for (int i = 0; i < dependencies.length; i++) {
			if (dependencies[i].equals(o.dependencies[i]))
				continue;
			for (j = 0; j < o.dependencies.length; j++)
				if (dependencies[i].equals(o.dependencies[j]))
					break;
			if (j == o.dependencies.length) //No dependency was found in the other PluginData's dependency list equal to the ith one in this PluginData's dependency list
				return false;
		}
		return true;
	}
	
	/**
	 * Equivalent to {@link #equals(Object)}, but it additionally requires that the dependencies are in the same order in
	 * both this {@link PluginData} and the {@link PluginData} being tested.
	 * 
	 * @param other
	 *            the {@link PluginData} to test
	 * @return {@code true} iff {@link #equals(Object)} returns {@code true} and the dependencies are in the same order in
	 *         both this {@link PluginData} and the {@link PluginData} being tested
	 * @see #equals(Object)
	 */
	public boolean strictEquals(Object other) {
		PluginData<?> o = basicEquals(other);
		if (o == null)
			return false;
		for (int i = 0; i < dependencies.length; i++)
			if (!dependencies[i].equals(o.dependencies[i]))
				return false;
		return true;
	}
	
	private PluginData<?> basicEquals(Object other) {
		if (other == null || !(other instanceof PluginData))
			return null;
		PluginData<?> o = (PluginData<?>) other;
		if (clazz.equals(o.clazz) && isLinkable(false) == o.isLinkable(false) && description.equals(o.description) &&
				dependencies.length == o.dependencies.length &&
				resolvedDependencies.equals(o.resolvedDependencies) &&
				(parent == null && o.parent == null || (parent != null && parent.equals(o.parent))) &&
				(resolvedParent == null && o.resolvedParent == null ||
						(resolvedParent == null && resolvedParent.equals(o.resolvedParent))) &&
				(instance == null && o.instance == null || (instance != null && instance.equals(o.instance))))
			return o;
		return null;
	}
	
	@Override
	public int hashCode() {
		return hashCode;
	}
}

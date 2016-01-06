package toberumono.plugin.manager;

import java.util.concurrent.locks.ReadWriteLock;

/**
 * Base class for managing dependency requests.
 * 
 * @author Toberumono
 * @param <T>
 *            the type of the requesting plugin
 */
public abstract class RequestedDependency<T> {
	private final PluginData<T> requestor;
	private final DependencyContainer dependency;
	private final ReadWriteLock requestedDependenciesLock;
	private PluginData<T> satisfier;
	private final int hashCode;
	
	/**
	 * Creates a new {@link RequestedDependency}.
	 * 
	 * @param requestor
	 *            the {@link PluginData} representing the plugin making the request
	 * @param dependency
	 *            the {@link DependencyContainer Dependency} being requested
	 * @param requestedDependenciesLock
	 *            the {@link ReadWriteLock} being used for the dependency system
	 */
	public RequestedDependency(PluginData<T> requestor, DependencyContainer dependency, ReadWriteLock requestedDependenciesLock) {
		this.requestor = requestor;
		this.dependency = dependency;
		this.requestedDependenciesLock = requestedDependenciesLock;
		satisfier = null;
		int hash = 17;
		hash = hash * 31 + this.requestor.hashCode();
		hash = hash * 31 + this.dependency.hashCode();
		hashCode = hash;
	}
	
	/**
	 * Attempts to satisfy the {@link RequestedDependency} with the given {@link PluginData}.
	 * 
	 * @param satisfier
	 *            the {@link PluginData} with which the {@link RequestedDependency} might be satisfied
	 * @return {@code true} iff the {@link RequestedDependency} was satisfied
	 */
	public boolean trySatisfy(PluginData<T> satisfier) {
		synchronized (requestedDependenciesLock.writeLock()) {
			if (isSatisfied() || !satisfier.getID().equals(dependency.id()) || !applySatisfier(satisfier))
				return false;
			this.satisfier = satisfier;
			return true;
		}
	}
	
	protected abstract boolean applySatisfier(PluginData<T> satisfier);
	
	/**
	 * Attempts to notify the {@link RequestedDependency} that the plugin that satisfied it can no longer do so.
	 * 
	 * @return {@code true} iff the {@link RequestedDependency} was successfully desatisfied
	 */
	public boolean tryDesatisfy() {
		synchronized (requestedDependenciesLock.writeLock()) {
			if (!isSatisfied() || !unapplySatisfier(satisfier))
				return false;
			satisfier = null;
			return true;
		}
	}
	
	protected abstract boolean unapplySatisfier(PluginData<T> satisfier);
	
	/**
	 * @return {@code true} iff the {@link RequestedDependency} has been satisfied (that is, its satisfier is not null)
	 */
	public boolean isSatisfied() {
		synchronized (requestedDependenciesLock.readLock()) {
			return satisfier != null;
		}
	}
	
	/**
	 * @return the ID of the requesting plugin as a {@link String}
	 */
	public String getRequestorID() {
		return requestor.getID();
	}
	
	/**
	 * @return the {@link DependencyContainer Dependency} being requested
	 */
	public DependencyContainer getDependency() {
		return dependency;
	}
	
	@Override
	public boolean equals(Object other) {
		if (!(other instanceof RequestedDependency))
			return false;
		RequestedDependency<?> o = (RequestedDependency<?>) other;
		if (!(requestedDependenciesLock == o.requestedDependenciesLock && requestor.equals(o.requestor) &&
				getDependency().equals(o.getDependency())))
			return false;
		synchronized (requestedDependenciesLock.readLock()) {
			return isSatisfied() == o.isSatisfied();
		}
	}
	
	@Override
	public int hashCode() {
		return hashCode;
	}
	
	/**
	 * Generates a {@link String} of the form
	 * {@code "requestorId:}&#123;{@code requestedId, requestedVersion}&#125;{@code :satisfied"} (i.e.
	 * {@code "my.first.plugin:}&#123;{@code my.second.plugin, [any]}&#125;{@code :false"})
	 * 
	 * @return a {@link String} of the form
	 *         {@code "requestorId:}&#123;{@code requestedId, requestedVersion}&#125;{@code :satisfied"}
	 */
	@Override
	public String toString() {
		return requestor.getID() + ":" + getDependency() + ":" + isSatisfied();
	}
}

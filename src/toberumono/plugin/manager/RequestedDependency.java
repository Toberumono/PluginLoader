package toberumono.plugin.manager;

import java.util.function.Consumer;

import toberumono.plugin.annotations.Dependency;

class RequestedDependency<T> {
	private final String requestorId, requestedId, requestedVersion;
	private final Consumer<PluginData<T>> onSatisfaction;
	private Boolean satisfied;
	private Integer hashCode;
	
	public RequestedDependency(String requestorId, Dependency dependency, Consumer<PluginData<T>> onSatisfaction) {
		this(requestorId, dependency.id(), dependency.version(), onSatisfaction);
	}
	
	public RequestedDependency(String requestorId, String requestedId, String requestedVersion, Consumer<PluginData<T>> onSatisfaction) {
		this.requestorId = requestorId;
		this.requestedId = requestedId;
		this.requestedVersion = requestedVersion;
		this.onSatisfaction = onSatisfaction;
		satisfied = false;
		hashCode = null;
	}
	
	public boolean trySatisfy(PluginData<T> satisfier) {
		synchronized (satisfied) {
			if (isSatisfied())
				return false;
			if (!satisfier.getID().equals(requestedId))
				return false;
			satisfied = true;
			onSatisfaction.accept(satisfier);
			return true;
		}
	}
	
	public boolean isSatisfied() {
		synchronized (satisfied) {
			return satisfied;
		}
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
		return requestorId + ":{" + requestedId + ", " + requestedVersion + "}:" + satisfied;
	}
	
	@Override
	public boolean equals(Object other) {
		if (!(other instanceof RequestedDependency))
			return false;
		RequestedDependency<?> o = (RequestedDependency<?>) other;
		return requestorId.equals(o.requestorId) && requestedId.equals(o.requestedId) && requestedVersion.equals(o.requestedVersion) && satisfied == o.satisfied;
	}
	
	@Override
	public int hashCode() {
		if (hashCode == null)
			hashCode = (requestorId + requestedId + requestedVersion).hashCode();
		return hashCode;
	}
}

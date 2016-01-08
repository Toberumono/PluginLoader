package toberumono.plugin.manager;

import toberumono.plugin.annotations.Dependency;

/**
 * Wraps {@link Dependency} annotations so that additional dependencies can be added at runtime as needed by the
 * {@link PluginManager}.
 * 
 * @author Toberumono
 */
public class DependencyContainer {
	private final String id, version;
	private final Boolean required;
	private final int hashCode;
	
	/**
	 * Creates a {@link DependencyContainer} that wraps the given {@link Dependency}.
	 * 
	 * @param dependency
	 *            the {@link Dependency} to wrap
	 */
	public DependencyContainer(Dependency dependency) {
		this(dependency.id(), dependency.version(), dependency.required());
	}
	
	/**
	 * Creates a {@link DependencyContainer} that "wraps" the given values.
	 * 
	 * @param id
	 *            the ID of the plugin that satisfies the dependency
	 * @param version
	 *            the versions of the plugin that satisfies the dependency
	 * @param required
	 *            whether the dependency is required by the plugin that is requesting it
	 */
	public DependencyContainer(String id, String version, boolean required) {
		if (id == null)
			throw new IllegalArgumentException("The id of a Dependency cannot be null.");
		this.id = id;
		this.version = version == null ? "[any]" : version;
		this.required = required;
		int hash = 17;
		hash = hash * 31 + this.id.hashCode();
		hash = hash * 31 + this.version.hashCode();
		hash = hash * 31 + this.required.hashCode();
		hashCode = hash;
	}
	
	/**
	 * @return the {@code ID} of the {@code plugin} that satisfies the dependency
	 */
	public String id() {
		return id;
	}
	
	/**
	 * @return the specific version or range of versions of the {@code plugin} that satisfy the dependency
	 */
	public String version() {
		return version;
	}
	
	/**
	 * @return whether this dependency is required ({@code true}) or optional ({@code false})
	 */
	public Boolean required() {
		return required;
	}
	
	@Override
	public boolean equals(Object other) {
		if (this == other)
			return true;
		if (other != null && other instanceof DependencyContainer) {
			DependencyContainer o = (DependencyContainer) other;
			return id().equals(o.id()) && version().equals(o.version()) && required().equals(o.required());
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		return hashCode;
	}
	
	/**
	 * @return a {@link String} of the form, "{id, version, required}"
	 */
	@Override
	public String toString() {
		return "{" + id() + ", " + version() + ", " + required() + "}";
	}
}

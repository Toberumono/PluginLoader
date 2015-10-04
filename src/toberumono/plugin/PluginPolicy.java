package toberumono.plugin;

import java.security.AllPermission;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

public class PluginPolicy extends Policy {
	private final Collection<Policy> policies;
	private final Set<CodeSource> allowed, unmodifiableAllowed;
	private static final PermissionCollection allPermissions;
	static {
		allPermissions = new Permissions();
		allPermissions.add(new AllPermission());
	}
	
	PluginPolicy(Set<CodeSource> allPermissionsSources) {
		policies = new ArrayList<>();
		Policy original = Policy.getPolicy();
		policies.add(original);
		allowed = allPermissionsSources == null ? new HashSet<>() : allPermissionsSources;
		unmodifiableAllowed = Collections.unmodifiableSet(allowed);
	}
	
	@Override
	public PermissionCollection getPermissions(CodeSource codesource) {
		if (allowed.contains(codesource))
			return allPermissions;
		PermissionCollection out = new Permissions();
		for (Policy p : policies)
			for (Enumeration<Permission> e = p.getPermissions(codesource).elements(); e.hasMoreElements();)
				out.add(e.nextElement());
		out.setReadOnly();
		return out;
	}
	
	@Override
	public PermissionCollection getPermissions(ProtectionDomain domain) {
		if (allowed.contains(domain.getCodeSource()))
			return allPermissions;
		PermissionCollection out = new Permissions();
		for (Policy p : policies)
			for (Enumeration<Permission> e = p.getPermissions(domain).elements(); e.hasMoreElements();)
				out.add(e.nextElement());
		out.setReadOnly();
		return out;
	}
	
	@Override
	public boolean implies(ProtectionDomain domain, Permission permission) {
		if (allowed.contains(domain.getCodeSource()))
			return true;
		for (Policy p : policies)
			if (p.implies(domain, permission))
				return true;
		return false;
	}
	
	@Override
	public void refresh() {
		for (Policy p : policies)
			p.refresh();
	}
	
	/**
	 * @return the {@link CodeSource CodeSources} which are designated in this {@link PluginPolicy} as having
	 *         {@link AllPermission} as their permissions
	 */
	public Set<CodeSource> getRootCodeSources() {
		return unmodifiableAllowed;
	}
}

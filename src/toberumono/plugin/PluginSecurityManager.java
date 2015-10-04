package toberumono.plugin;

import java.security.Permission;

/**
 * This {@link SecurityManager} is used if there is not an active {@link SecurityManager} when the a {@link PluginManager} is
 * initialized.
 * 
 * @author Toberumono
 */
public class PluginSecurityManager extends SecurityManager {
	
	PluginSecurityManager() {
		super();
	}
	
	/**
	 * This allows for every action <i>except</i> plugin access.
	 */
	@Override
	public void checkPermission(Permission perm) {
		if (!(perm instanceof PluginPermission))
		return;
	}
}

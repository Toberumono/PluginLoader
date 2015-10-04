package toberumono.plugin;

import java.security.Permission;

public class PluginPermission extends Permission {

	public PluginPermission(String name, String actions) {
		super(name);
		// TODO Auto-generated constructor stub
	}

	@Override
	public boolean implies(Permission permission) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean equals(Object obj) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public int hashCode() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String getActions() {
		// TODO Auto-generated method stub
		return null;
	}

}

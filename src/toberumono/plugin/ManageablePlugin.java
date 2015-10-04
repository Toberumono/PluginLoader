package toberumono.plugin;

import java.util.List;

public interface ManageablePlugin {
	
	public ManageablePlugin getParent();
	
	public List<ManageablePlugin> getChildren();
	
	public void enable();
	
	public void disable();
}

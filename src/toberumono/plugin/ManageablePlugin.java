package toberumono.plugin;

import java.util.Collections;
import java.util.List;

public interface ManageablePlugin {
	
	public ManageablePlugin getParent(AccessKey key);
	
	/**
	 * This method should <i>always</i> return an unmodifiable {@link List}.
	 * 
	 * @param key
	 *            the caller's {@link AccessKey}
	 * @return an unmodifiable list of the child {@link ManageablePlugin plugins} of the {@link ManageablePlugin} that holds
	 *         this {@link AccessKey}
	 * @see Collections#unmodifiableList(List)
	 */
	public List<ManageablePlugin> getChildren(AccessKey key);
	
	public void enable();
	
	public void disable();
	
	public void addChild();
	
	public void getAccessKey();
}

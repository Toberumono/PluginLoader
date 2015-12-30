package toberumono.plugin.manager;

import java.util.function.Consumer;

import toberumono.plugin.annotations.PluginDescription;
import toberumono.plugin.annotations.Dependency;

class RequestedDependency<T> {
	private final String id;
	private final Consumer<PluginData<T>> onSatisfaction;
	private boolean satisfied;
	
	public RequestedDependency(String id, Consumer<PluginData<T>> onSatisfaction) {
		this.id = id;
		this.onSatisfaction = onSatisfaction;
		satisfied = false;
	}
	
	public RequestedDependency(Dependency dependency, Consumer<PluginData<T>> onSatisfaction) {
		this.id = dependency.id();
		this.onSatisfaction = onSatisfaction;
		satisfied = false;
	}
	
	public synchronized boolean satisfy(PluginData<T> satisfier) {
		if (satisfied)
			return false;
		PluginDescription pd = satisfier.getDescription();
		if (!pd.id().equals(id))
			return false;
		satisfied = true;
		onSatisfaction.accept(satisfier);
		return true;
	}
}

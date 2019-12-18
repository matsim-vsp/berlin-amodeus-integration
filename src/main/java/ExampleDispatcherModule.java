import org.matsim.core.controler.AbstractModule;

import ch.ethz.matsim.av.framework.AVUtils;

public class ExampleDispatcherModule extends AbstractModule {
	@Override
	public void install() {
		AVUtils.registerDispatcherFactory(binder(), "ExampleDispatcher", ExampleDispatcher.Factory.class);
	}
}

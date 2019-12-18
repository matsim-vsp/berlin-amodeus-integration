

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.router.util.TravelTime;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import ch.ethz.idsc.amodeus.dispatcher.core.RebalancingDispatcher;
import ch.ethz.idsc.amodeus.dispatcher.core.RoboTaxi;
import ch.ethz.idsc.amodeus.dispatcher.core.RoboTaxiStatus;
import ch.ethz.idsc.amodeus.net.MatsimAmodeusDatabase;
import ch.ethz.matsim.av.config.operator.OperatorConfig;
import ch.ethz.matsim.av.dispatcher.AVDispatcher;
import ch.ethz.matsim.av.framework.AVModule;
import ch.ethz.matsim.av.passenger.AVRequest;
import ch.ethz.matsim.av.router.AVRouter;

public class ExampleDispatcher extends RebalancingDispatcher {
	private final Network network;

	public ExampleDispatcher(Config config, OperatorConfig operatorConfig, TravelTime travelTime, AVRouter router,
			EventsManager eventsManager, MatsimAmodeusDatabase db, Network network) {
		super(config, operatorConfig, travelTime, router, eventsManager, db);
		this.network = network;
	}

	private boolean rebalancingDone = false;
	private final Random random = new Random(0);

	@Override
	protected void redispatch(double now) {	
		if (((int) now) % 60 == 0) {
			if (now > 11.0 * 3600.0 && !rebalancingDone) {
				List<Link> links = new LinkedList<>(network.getLinks().values());
				List<RoboTaxi> taxis = new LinkedList<>(getRoboTaxiSubset(RoboTaxiStatus.STAY));

				for (RoboTaxi taxi : taxis) {
					this.setRoboTaxiRebalance(taxi, links.get(random.nextInt(links.size())));
				}

				rebalancingDone = true;
			} else {
				List<AVRequest> requests = new LinkedList<>(getUnassignedAVRequests());
				List<RoboTaxi> taxis = new LinkedList<>(getRoboTaxiSubset(RoboTaxiStatus.STAY));

				while (requests.size() > 0 && taxis.size() > 0) {
					AVRequest request = requests.remove(0);
					RoboTaxi taxi = taxis.remove(0);

					this.setRoboTaxiPickup(taxi, request);
				}
			}
		}
	}

	public static class Factory implements AVDispatcherFactory {
		@Inject
		@Named(AVModule.AV_MODE)
		private TravelTime travelTime;

		@Inject
		private EventsManager eventsManager;

		@Inject
		private Config config;

		@Inject
		private MatsimAmodeusDatabase db;

		@Override
		public AVDispatcher createDispatcher(OperatorConfig operatorConfig, AVRouter router, Network network) {
			return new ExampleDispatcher(config, operatorConfig, travelTime, router, eventsManager, db, network);
		}
	}
}
import static org.matsim.core.config.groups.ControlerConfigGroup.RoutingAlgorithmType.FastAStarLandmarks;

import java.util.Arrays;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup.ModeRoutingParams;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.router.StageActivityTypes;
import org.matsim.core.router.StageActivityTypesImpl;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.Trip;
import org.matsim.core.scenario.ScenarioUtils;

public class RunAmodeusInBerlin {

	private static final Logger log = Logger.getLogger(RunAmodeusInBerlin.class);

	public static void main(String[] args) {

		for (String arg : args) {
			log.info(arg);
		}

		if (args.length == 0) {
			args = new String[] { "scenarios/berlin-v5.4-1pct.config.xml" };
		}

		Config config = prepareConfig(args);
		Scenario scenario = prepareScenario(config);
		Controler controler = prepareControler(scenario);
		controler.run();

	}

	public static Controler prepareControler(Scenario scenario) {
		// note that for something like signals, and presumably drt, one needs the
		// controler object

		Gbl.assertNotNull(scenario);

		final Controler controler = new Controler(scenario);

		// use the (congested) car travel time for the teleported ride mode
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				addTravelTimeBinding(TransportMode.ride).to(networkTravelTime());
				addTravelDisutilityFactoryBinding(TransportMode.ride).to(carTravelDisutilityFactoryKey());
			}
		});

		return controler;
	}

	public static Scenario prepareScenario(Config config) {
		Gbl.assertNotNull(config);

		// note that the path for this is different when run from GUI (path of original
		// config) vs.
		// when run from command line/IDE (java root). :-( See comment in method. kai,
		// jul'18
		// yy Does this comment still apply? kai, jul'19

		final Scenario scenario = ScenarioUtils.loadScenario(config);
		backportScenario(config, scenario);

		return scenario;
	}

	public static Config prepareConfig(String[] args) {
		OutputDirectoryLogging.catchLogEntries();

		String[] typedArgs = Arrays.copyOfRange(args, 1, args.length);

		final Config config = ConfigUtils.loadConfig(args[0]); // I need this to set the context

		config.controler().setRoutingAlgorithmType(FastAStarLandmarks);

		config.subtourModeChoice().setProbaForRandomSingleTripMode(0.5);

		config.plansCalcRoute().setRoutingRandomness(3.);
		config.plansCalcRoute().removeModeRoutingParams(TransportMode.ride);
		config.plansCalcRoute().removeModeRoutingParams(TransportMode.pt);
		config.plansCalcRoute().removeModeRoutingParams(TransportMode.bike);
		config.plansCalcRoute().removeModeRoutingParams("undefined");

		config.qsim().setInsertingWaitingVehiclesBeforeDrivingVehicles(true);

		// vsp defaults
		config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.info);
		config.plansCalcRoute().setInsertingAccessEgressWalk(true);
		config.qsim().setUsingTravelTimeCheckInTeleportation(true);
		config.qsim().setTrafficDynamics(QSimConfigGroup.TrafficDynamics.kinematicWaves);

		// activities: (have changed in the backport)

		// Doesn't exist in MATSim 11
		// ConfigUtils.applyCommandline(config, typedArgs);

		return config;
	}

	/**
	 * Backports scenario to MATSim 11
	 */
	public static void backportScenario(Config config, Scenario scenario) {
		// Old MATSIm needs the subpopulation in the PersonAttributes object
		for (Person person : scenario.getPopulation().getPersons().values()) {
			scenario.getPopulation().getPersonAttributes().putAttribute(person.getId().toString(), "subpopulation",
					person.getAttributes().getAttribute("subpopulation"));
		}

		// Old MainModeIdentifier does not understand non_network_walk (it returns it as
		// main mode)
		StageActivityTypes stageActivities = new StageActivityTypesImpl("pt interaction", "car interaction");

		for (Person person : scenario.getPopulation().getPersons().values()) {
			for (Plan plan : person.getPlans()) {
				for (Trip trip : TripStructureUtils.getTrips(plan, stageActivities)) {	
					boolean hasPt = false;
					
					for (Leg leg : trip.getLegsOnly()) {
						if (leg.getMode().equals("pt")) {
							hasPt = true;
						}
					}
					
					for (Leg leg : trip.getLegsOnly()) {
						if (leg.getMode().equals("non_network_walk")) {
							if (hasPt) {
								leg.setMode("transit_walk");
							} else {
								leg.setMode("access_walk");
							}
						}
					}
				}
			}
		}

		config.plansCalcRoute().removeModeRoutingParams("non_network_walk");
		config.plansCalcRoute().removeModeRoutingParams(TransportMode.transit_walk);

		config.transit().setUseTransit(false);
		
		ModeRoutingParams routingParams = new ModeRoutingParams("pt");
		routingParams.setTeleportedModeSpeed(config.plansCalcRoute().getModeRoutingParams().get("bicycle").getTeleportedModeSpeed());
		routingParams.setBeelineDistanceFactor(1.3);
		config.plansCalcRoute().addModeRoutingParams(routingParams);

		for (long ii = 600; ii <= 97200; ii += 600) {
			ActivityParams params;

			params = new ActivityParams("home_" + ii + ".0");
			params.setTypicalDuration(ii);
			config.planCalcScore().addActivityParams(params);

			params = new ActivityParams("work_" + ii + ".0");
			params.setTypicalDuration(ii);
			params.setOpeningTime(6. * 3600.);
			params.setClosingTime(20. * 3600.);
			config.planCalcScore().addActivityParams(params);

			params = new ActivityParams("leisure_" + ii + ".0");
			params.setTypicalDuration(ii);
			params.setOpeningTime(9. * 3600.);
			params.setClosingTime(27. * 3600.);
			config.planCalcScore().addActivityParams(params);

			params = new ActivityParams("shopping_" + ii + ".0");
			params.setTypicalDuration(ii);
			params.setOpeningTime(8. * 3600.);
			params.setClosingTime(20. * 3600.);
			config.planCalcScore().addActivityParams(params);

			params = new ActivityParams("other_" + ii + ".0");
			params.setTypicalDuration(ii);
			config.planCalcScore().addActivityParams(params);
		}

		ActivityParams params = new ActivityParams("freight");
		params.setTypicalDuration(12. * 3600.);
		config.planCalcScore().addActivityParams(params);

		params = new ActivityParams("pt interaction");
		params.setScoringThisActivityAtAll(false);
		config.planCalcScore().addActivityParams(params);

		params = new ActivityParams("car interaction");
		params.setScoringThisActivityAtAll(false);
		config.planCalcScore().addActivityParams(params);

		config.controler().setOutputDirectory("output");
	}
}

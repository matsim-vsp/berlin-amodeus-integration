import static org.matsim.core.config.groups.ControlerConfigGroup.RoutingAlgorithmType.FastAStarLandmarks;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import ch.ethz.idsc.amodeus.dispatcher.AdaptiveRealTimeRebalancingPolicy;
import ch.ethz.idsc.amodeus.prep.VirtualNetworkCreators;
import ch.ethz.idsc.amodeus.prep.VirtualNetworkPreparer;
import ch.ethz.matsim.av.data.AVOperator;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpModule;
import org.matsim.contrib.dvrp.trafficmonitoring.DvrpTravelTimeModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ModeParams;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.network.filter.NetworkFilterManager;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.StageActivityTypes;
import org.matsim.core.router.StageActivityTypesImpl;
import org.matsim.core.router.TripRouter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.Trip;
import org.matsim.core.scenario.ScenarioUtils;

import ch.ethz.idsc.amodeus.matsim.mod.AmodeusDatabaseModule;
import ch.ethz.idsc.amodeus.matsim.mod.AmodeusDispatcherModule;
import ch.ethz.idsc.amodeus.matsim.mod.AmodeusModule;
import ch.ethz.idsc.amodeus.matsim.mod.AmodeusVehicleGeneratorModule;
import ch.ethz.idsc.amodeus.matsim.mod.AmodeusVehicleToVSGeneratorModule;
import ch.ethz.idsc.amodeus.matsim.mod.AmodeusVirtualNetworkModule;
import ch.ethz.idsc.amodeus.net.DatabaseModule;
import ch.ethz.idsc.amodeus.net.MatsimAmodeusDatabase;
import ch.ethz.idsc.amodeus.net.SimulationServer;
import ch.ethz.idsc.amodeus.options.ScenarioOptions;
import ch.ethz.idsc.amodeus.options.ScenarioOptionsBase;
import ch.ethz.matsim.av.config.AVConfigGroup;
import ch.ethz.matsim.av.config.AVScoringParameterSet;
import ch.ethz.matsim.av.config.operator.OperatorConfig;
import ch.ethz.matsim.av.framework.AVModule;
import ch.ethz.matsim.av.framework.AVQSimModule;
import ch.ethz.matsim.av.generator.PopulationDensityGenerator;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;

public class RunAmodeusInBerlin {

	private static final Logger log = Logger.getLogger(RunAmodeusInBerlin.class);
	private static final boolean USE_VIRTUAL_NETWORK = false;


	/**
	 * this class basically represents a copy of the RunBerlinScenario v5.4 (@see <a href="https://github.com/matsim-scenarios/matsim-berlin">MATSim-Berlin repo</a>)
	 * with is modified an backported to MATSim 11.0.
	 * This is necessary as AMODeus is compatible with MATSim 11.0 only at the time of creation of this class.
	 *
	 *
	 * NOTE:
	 *
	 * arguments have to be in this order: workingDirectory, config file, additional arguemnts...
	 *
	 * before running, you need to set the working directory in the run configuration to the scenarios folder in this repo.
	 */
	public static void main(String[] args) throws IOException, URISyntaxException {

		for (String arg : args) {
			log.info(arg);
		}

		if (args.length == 0) {
			args = new String[] { "scenarios", "berlin-v5.4-1pct.config.xml" };
		}

		File workingDirectory = getWorkingDirectory(args[0]);

		Config config = prepareConfig(args);



		config.qsim().setNumberOfThreads(4);
		config.global().setNumberOfThreads(4);

		Scenario scenario = prepareScenario(config);
		ScenarioOptions scenarioOptions = createScenarioOptions(config, workingDirectory);

		Controler controler = prepareControler(scenario, workingDirectory, scenarioOptions);


		//if you do not use the simple dispatching algorithm, you may need a virtualNetwork
		if(ConfigUtils.addOrGetModule(config, AVConfigGroup.class).getOperatorConfig(OperatorConfig.DEFAULT_OPERATOR_ID).getDispatcherConfig().getType().equals("ExampleDispatcher")){
			//we need to to do this ourselves and to not rely on AmodeusVirtualNetworkModule as we need to filter the car network
			createAndWriteVirtualNetwork(config, scenario, scenarioOptions);
		}

		controler.run();
	}

	private static void createAndWriteVirtualNetwork(Config config, Scenario scenario, ScenarioOptions scenarioOptions) {
		AVConfigGroup avConfig = ConfigUtils.addOrGetModule(config, AVConfigGroup.class);
//			createAndStoreVirtualNetwork();

		int nrOfVehicles =  avConfig.getOperatorConfigs().get(OperatorConfig.DEFAULT_OPERATOR_ID).getGeneratorConfig().getNumberOfVehicles();

		NetworkFilterManager mng = new NetworkFilterManager(scenario.getNetwork());
		mng.addLinkFilter(l -> l.getAllowedModes().contains(TransportMode.car));

		Network filteredNetwork = mng.applyFilters();
		new NetworkCleaner().run(filteredNetwork);

		VirtualNetworkPreparer.INSTANCE.create(filteredNetwork, scenario.getPopulation(), scenarioOptions, nrOfVehicles, (int) config.qsim().getEndTime());
	}

	static File getWorkingDirectory(String dir){
		try {
			return (new File(dir)).getCanonicalFile();
		} catch (Exception var1) {
			System.err.println("Cannot load working directory, returning null: ");
			var1.printStackTrace();
			return null;
		}
	}

	public static Controler prepareControler(Scenario scenario, File workingDirectory, ScenarioOptions scenarioOptions) throws IOException, URISyntaxException {
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

		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				bind(MainModeIdentifier.class).toInstance(new BackportMainModeIdentifier());
			}
		});

		controler.addOverridingModule(new SwissRailRaptorModule());
		configureAmodeus(scenario.getConfig(), scenario, controler, workingDirectory, scenarioOptions);

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

		String[] typedArgs = Arrays.copyOfRange(args, 2, args.length);

		final Config config = ConfigUtils.loadConfig(args[0] + "/" + args[1]); // I need this to set the context

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

		config.plansCalcRoute().removeModeRoutingParams("non_network_walk");
		config.plansCalcRoute().removeModeRoutingParams(TransportMode.transit_walk);

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

		config.transit().setUsingTransitInMobsim(false);
	}

	public static void configureAmodeus(Config config, Scenario scenario, Controler controller, File workingDirectory, ScenarioOptions scenarioOptions)
			throws IOException, URISyntaxException {
		insertAVTripsIntoPopulation(scenario);
		configureAVContrib(config, controller, scenarioOptions);
		{ // Configure Amodeus

			// Open server port for clients to connect to (e.g. viewer)
			SimulationServer.INSTANCE.startAcceptingNonBlocking();
			SimulationServer.INSTANCE.setWaitForClients(false);

			MatsimAmodeusDatabase db = MatsimAmodeusDatabase.initialize(scenario.getNetwork(),
					new BerlinReferenceFrame());

			// controller.addOverridingModule(new AVModule(false));
			controller.addOverridingModule(new AmodeusModule());
			controller.addOverridingModule(new AmodeusDispatcherModule());
			controller.addOverridingModule(new AmodeusVehicleGeneratorModule());
			controller.addOverridingModule(new AmodeusVehicleToVSGeneratorModule());
			controller.addOverridingModule(new AmodeusDatabaseModule(db));
			controller.addOverridingModule(new AmodeusVirtualNetworkModule(scenarioOptions));
			controller.addOverridingModule(new DatabaseModule());
		}

		// Add custom dispatcher
		controller.addOverridingModule(new ExampleDispatcherModule());
	}

	static ScenarioOptions createScenarioOptions(Config config, File workingDirectory) throws IOException, URISyntaxException {
		ScenarioOptions scenarioOptions = new ScenarioOptions(workingDirectory, ScenarioOptionsBase.getDefault());

		if(USE_VIRTUAL_NETWORK){
			//if you want to use some sophisticated rebalancing/dispatching algorithm,
			//you may have to specify a virtualNetwork (representing 'rebalancing zones')
			scenarioOptions.setProperty("virtualNetwork", "berlin_virtual_network");
			scenarioOptions.setProperty("travelData", "berlin_travelData");

			//this is how to set the virtualNetworkCreator
			scenarioOptions.setProperty("virtualNetworkCreator", VirtualNetworkCreators.RECTANGULAR.toString());
			scenarioOptions.setProperty("LATITUDE_NODES", "5"); //this defines the amount of cuts
			scenarioOptions.setProperty("LONGITUDE_NODES", "5");

			//that is the time bin size for the linear programming process
			scenarioOptions.setProperty("dtTravelData", "3600");

			scenarioOptions.setProperty("numVirtualNodes", "10");
		}

		scenarioOptions.setProperty("LocationSpec", "BERLIN");

		Path absoluteConfigPath = Paths.get(config.getContext().toURI());
		Path workingDirectoryPath = FileSystems.getDefault().getPath(workingDirectory.getAbsolutePath());
		scenarioOptions.setProperty("simuConfig", workingDirectoryPath.relativize(absoluteConfigPath).toString());

		scenarioOptions.saveAndOverwriteAmodeusOptions();
		return scenarioOptions;
	}

	private static void insertAVTripsIntoPopulation(Scenario scenario) {
		StageActivityTypes stageActivities = new StageActivityTypesImpl("car interaction", "pt interaction",
				"ride interaction", "freight interaction");
		MainModeIdentifier mainModeIdentifier = new BackportMainModeIdentifier();
		Random random = new Random(0);

		for (Person person : scenario.getPopulation().getPersons().values()) {
			for (Plan plan : person.getPlans()) {
				for (Trip trip : TripStructureUtils.getTrips(plan, stageActivities)) {
					String mainMode = mainModeIdentifier.identifyMainMode(trip.getTripElements());

					if (mainMode.equals("car")) {
						if (random.nextDouble() < 0.01) {
							List<? extends PlanElement> newElements = Collections
									.singletonList(PopulationUtils.createLeg("av"));
							TripRouter.insertTrip(plan, trip.getOriginActivity(), newElements,
									trip.getDestinationActivity());
						}
					}
				}
			}
		}
	}

	private static void configureAVContrib(Config config, Controler controller, ScenarioOptions scenarioOptions) {
		// Configure AV

		// CONFIG

		config.planCalcScore().addModeParams(new ModeParams("av"));

		DvrpConfigGroup dvrpConfig = new DvrpConfigGroup();
		config.addModule(dvrpConfig);

		AVConfigGroup avConfig = ConfigUtils.addOrGetModule(config, AVConfigGroup.class);

		avConfig.setAllowedLinkMode("car");
		avConfig.setEnableDistanceAnalysis(true);
		avConfig.setPassengerAnalysisInterval(1);
		avConfig.setVehicleAnalysisInterval(1);

		OperatorConfig operatorConfig = prepareOperatorConfig(config, controller.getScenario(), scenarioOptions);
		avConfig.addOperator(operatorConfig);

		List<String> modes = new LinkedList<>(Arrays.asList(config.subtourModeChoice().getModes())); //
		modes.add("av");
		config.subtourModeChoice().setModes(modes.toArray(new String[modes.size()]));

		AVScoringParameterSet params;

		params = new AVScoringParameterSet();
		params.setMarginalUtilityOfWaitingTime(-0.1);
		params.setStuckUtility(-50.0);
		params.setSubpopulation("person");
		avConfig.addScoringParameters(params);

		params = new AVScoringParameterSet();
		params.setMarginalUtilityOfWaitingTime(-0.1);
		params.setStuckUtility(-50.0);
		params.setSubpopulation("freight");
		avConfig.addScoringParameters(params);

		// CONTROLLER
		controller.addOverridingModule(new DvrpModule());
		controller.addOverridingModule(new DvrpTravelTimeModule());
		controller.addOverridingModule(new AVModule(false));

		controller.configureQSimComponents(AVQSimModule::configureComponents);
	}

	private static OperatorConfig prepareOperatorConfig(Config config, Scenario scenario, ScenarioOptions scenarioOptions) {
		OperatorConfig operatorConfig = new OperatorConfig();

		// AVInteractionFinder
		// operatorConfig.getInteractionFinderConfig().setType(type);

		{ //define dispatch algorithm

			// operatorConfig.getDispatcherConfig().setType(SingleHeuristicDispatcher.TYPE);
			// operatorConfig.getDispatcherConfig().setType("DemandSupplyBalancingDispatcher");
			// operatorConfig.getDispatcherConfig().setType("NorthPoleSharedDispatcher");
//		operatorConfig.getDispatcherConfig().setType("ExampleDispatcher");

			//this is the dispatcher that Chengqi Lu presented at VSP in feb'2020. it rebalances based on spatial distribution and vehicle-to-request deficit calculation
			//using linear programming (GLPK)
			operatorConfig.getDispatcherConfig().setType(AdaptiveRealTimeRebalancingPolicy.class.getSimpleName());
		}

		operatorConfig.getGeneratorConfig().setType(PopulationDensityGenerator.TYPE);
		operatorConfig.getGeneratorConfig().setNumberOfVehicles(200);

		{ //define virtual network
//			operatorConfig.getParams().put("virtualNetworkPath", "berlin_virtual_network/berlin_virtual_network");
//			operatorConfig.getParams().put("travelDataPath", "berlin_virtual_network/berlin_travel_data");
//			operatorConfig.getParams().put("regenerateVirtualNetwork", "false");
//			operatorConfig.getParams().put("regenerateTravelData", "false");
		}
		return operatorConfig;
	}
}

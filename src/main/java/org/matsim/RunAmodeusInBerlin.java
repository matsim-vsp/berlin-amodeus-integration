package org.matsim;

import ch.ethz.idsc.amodeus.matsim.mod.*;
import ch.ethz.idsc.amodeus.net.DatabaseModule;
import ch.ethz.idsc.amodeus.net.MatsimAmodeusDatabase;
import ch.ethz.idsc.amodeus.net.SimulationServer;
import ch.ethz.idsc.amodeus.options.ScenarioOptions;
import ch.ethz.idsc.amodeus.prep.VirtualNetworkPreparer;
import ch.ethz.idsc.amodeus.virtualnetwork.core.VirtualNetwork;
import ch.ethz.matsim.av.config.AVConfigGroup;
import ch.ethz.matsim.av.config.AVScoringParameterSet;
import ch.ethz.matsim.av.config.operator.OperatorConfig;
import ch.ethz.matsim.av.framework.AVModule;
import ch.ethz.matsim.av.framework.AVQSimModule;
import ch.ethz.matsim.av.generator.PopulationDensityGenerator;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
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
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.network.filter.NetworkFilterManager;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.*;
import org.matsim.core.router.TripStructureUtils.Trip;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.dispatcher.ExampleDispatcherModule;
import org.matsim.setup.BackportMainModeIdentifier;
import org.matsim.setup.BerlinLocationSpec;
import org.matsim.setup.ScenarioSetup;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

public class RunAmodeusInBerlin {

    private static final Logger log = Logger.getLogger(RunAmodeusInBerlin.class);


    /**
     * this class basically represents a copy of the RunBerlinScenario v5.4 (@see <a href="https://github.com/matsim-scenarios/matsim-berlin">MATSim-Berlin repo</a>)
     * with is modified an backported to MATSim 11.0.
     * This is necessary as AMODeus is compatible with MATSim 11.0 only at the time of creation of this class.
     * <p>
     * <p>
     * NOTE:
     * <p>
     * arguments have to be in this order: workingDirectory, config file, additional arguemnts...
     * <p>
     * before running, you need to set the working directory in the run configuration to the scenarios folder in this repo.
     */
    public static void main(String[] args) throws IOException, URISyntaxException {

        for (String arg : args) {
            log.info(arg);
        }

        if (args.length == 0) {
            args = new String[]{"scenarios", "berlin-v5.4-1pct.config.xml"};
        }

        File workingDirectory = ScenarioSetup.getWorkingDirectory(args[0]);

        Config config = ScenarioSetup.prepareConfig(args);
        Gbl.assertNotNull(config);
        // note that the path for this is different when run from GUI (path of original
        // config) vs.
        // when run from command line/IDE (java root). :-( See comment in method. kai,
        // jul'18
        // yy Does this comment still apply? kai, jul'19
        final Scenario scenario = ScenarioUtils.loadScenario(config);
        backportScenario(config, scenario);

        ScenarioOptions scenarioOptions = ScenarioSetup.createScenarioOptions(config, workingDirectory);

        Controler controler = prepareControler(scenario, scenarioOptions);

		//if you do not use the simple dispatching algorithm, you may need a virtualNetwork
        //we need to to do this ourselves and to not rely on AmodeusVirtualNetworkModule as we need to filter the car network
        createAndWriteVirtualNetwork(config, scenario, scenarioOptions);

        controler.run();
    }

    public static Controler prepareControler(Scenario scenario, ScenarioOptions scenarioOptions) {
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
        configureAmodeus(scenario.getConfig(), scenario, controler, scenarioOptions);

        return controler;
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

    public static void configureAmodeus(Config config, Scenario scenario, Controler controller, ScenarioOptions scenarioOptions) {
        insertAVTripsIntoPopulation(scenario);
        configureAVContrib(config, controller, scenarioOptions);
        { // Configure Amodeus

            // Open server port for clients to connect to (e.g. viewer)
            SimulationServer.INSTANCE.startAcceptingNonBlocking();
            SimulationServer.INSTANCE.setWaitForClients(false);

            MatsimAmodeusDatabase db = MatsimAmodeusDatabase.initialize(scenario.getNetwork(), BerlinLocationSpec.REFERENCE_FRAME);

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
		operatorConfig.getDispatcherConfig().setType("ExampleDispatcher");

            //this is the dispatcher that Chengqi Lu presented at VSP in feb'2020. it rebalances based on spatial distribution and vehicle-to-request deficit calculation
            //using linear programming (GLPK)
//			operatorConfig.getDispatcherConfig().setType(AdaptiveRealTimeRebalancingPolicy.class.getSimpleName());
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

	private static VirtualNetwork<Link> createAndWriteVirtualNetwork(Config config, Scenario scenario, ScenarioOptions scenarioOptions) {
		AVConfigGroup avConfig = ConfigUtils.addOrGetModule(config, AVConfigGroup.class);
//			createAndStoreVirtualNetwork();

		int nrOfVehicles =  avConfig.getOperatorConfigs().get(OperatorConfig.DEFAULT_OPERATOR_ID).getGeneratorConfig().getNumberOfVehicles();

		NetworkFilterManager mng = new NetworkFilterManager(scenario.getNetwork());
		mng.addLinkFilter(l -> l.getAllowedModes().contains(TransportMode.car));

		Network filteredNetwork = mng.applyFilters();
		new NetworkCleaner().run(filteredNetwork);

		return VirtualNetworkPreparer.INSTANCE.create(filteredNetwork, scenario.getPopulation(), scenarioOptions, nrOfVehicles, (int) config.qsim().getEndTime());
	}
}

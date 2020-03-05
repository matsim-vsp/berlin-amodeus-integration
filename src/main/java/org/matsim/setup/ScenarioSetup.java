package org.matsim.setup;

import ch.ethz.idsc.amodeus.data.LocationSpecDatabase;
import ch.ethz.idsc.amodeus.options.ScenarioOptions;
import ch.ethz.idsc.amodeus.options.ScenarioOptionsBase;
import ch.ethz.idsc.amodeus.prep.VirtualNetworkCreators;
import org.gnu.glpk.GLPK;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.OutputDirectoryLogging;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.matsim.core.config.groups.ControlerConfigGroup.RoutingAlgorithmType.FastAStarLandmarks;

/**
 * Utility class containing setup routines for the scenario.
 */
public class ScenarioSetup {

    private ScenarioSetup() {}

    public static File getWorkingDirectory(String dir){
        try {
            return (new File(dir)).getCanonicalFile();
        } catch (Exception var1) {
            System.err.println("Cannot load working directory, returning null: ");
            var1.printStackTrace();
            return null;
        }
    }

    /**
     * Create the scenario options for Amodeus.
     */
    public static ScenarioOptions createScenarioOptions(Config config, File workingDirectory) throws IOException, URISyntaxException {
        ScenarioOptions scenarioOptions = new ScenarioOptions(workingDirectory, ScenarioOptionsBase.getDefault());

        {
            // if you want to use some sophisticated rebalancing/dispatching algorithm,
            // you may have to specify a virtualNetwork (representing 'rebalancing zones')
            // when a network is set it is always loaded, even if not needed
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

        LocationSpecDatabase.INSTANCE.put(new BerlinLocationSpec());

        scenarioOptions.saveAndOverwriteAmodeusOptions();
        return scenarioOptions;
    }


    /**
     * Prepare the base config for MATSim.
     */
    public static Config prepareConfig(String[] args) {
        OutputDirectoryLogging.catchLogEntries();

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

        config.qsim().setNumberOfThreads(4);
        config.global().setNumberOfThreads(4);

        return config;
    }

    public static void checkGLPKLib() {
        try {
            System.out.println("Working with GLPK version " + GLPK.glp_version());
        } catch (Exception exception) {
            System.err.println("GLPK for java is not installed which is necessary to run the preparer or server. \n "
                    + "In order to install it, follow the instructions provided at\n: " + "http://glpk-java.sourceforge.net/gettingStarted.html \n"
                    + "In order to work properly, either the location of the GLPK library must be  specified in \n" + "the environment variable, using for instance the command"
                    + "export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/usr/local/lib/jni \n" + "where /usr/local/lib/jni  is the path where the file libglpk_java.so is located \n"
                    + "in your installation. Alternatively, the path can also be supplied as a JAVA runtime \n" + "argument, e.g., -Djava.library.path=/usr/local/lib/jni");
        }
    }

}

package org.matsim;

import ch.ethz.idsc.amodeus.gfx.AmodeusComponent;
import ch.ethz.idsc.amodeus.gfx.AmodeusViewerFrame;
import ch.ethz.idsc.amodeus.gfx.ViewerConfig;
import ch.ethz.idsc.amodeus.net.MatsimAmodeusDatabase;
import ch.ethz.idsc.amodeus.options.ScenarioOptions;
import ch.ethz.idsc.amodeus.util.math.GlobalAssert;
import ch.ethz.idsc.amodeus.virtualnetwork.core.VirtualNetworkGet;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.setup.BerlinLocationSpec;
import org.matsim.setup.ScenarioSetup;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

/**
 * the viewer allows to connect to the scenario server or to view saved simulation results.
 */
public enum RunScenarioViewer {
    ;

    public static void main(String[] args) throws IOException, URISyntaxException {

        if (args.length == 0) {
            args = new String[]{"scenarios", "berlin-v5.4-1pct.config.xml"};
        }

        /** load options */
        Config config = ScenarioSetup.prepareConfig(args);
        Scenario scenario = ScenarioUtils.loadScenario(config);

        File workingDirectory = ScenarioSetup.getWorkingDirectory(args[0]);
        ScenarioOptions scenarioOptions = ScenarioSetup.createScenarioOptions(config, workingDirectory);

        System.out.println("MATSim config file: " + scenarioOptions.getSimulationConfigName());
        final File outputSubDirectory = new File(config.controler().getOutputDirectory()).getAbsoluteFile();
        if (!outputSubDirectory.isDirectory()) {
            System.err.println("output directory: " + outputSubDirectory.getAbsolutePath() + " not found.");
            GlobalAssert.that(false);
        }
        System.out.println("outputSubDirectory=" + outputSubDirectory.getAbsolutePath());
        File outputDirectory = outputSubDirectory.getParentFile();
        System.out.println("showing simulation results from outputDirectory=" + outputDirectory);


        /** initializing the viewer */
        MatsimAmodeusDatabase db = MatsimAmodeusDatabase.initialize(scenario.getNetwork(), BerlinLocationSpec.REFERENCE_FRAME);
        AmodeusComponent amodeusComponent = AmodeusComponent.createDefault(db, workingDirectory);

        /** virtual network layer, should not cause problems if layer does not exist */
        amodeusComponent.virtualNetworkLayer.setVirtualNetwork(VirtualNetworkGet.readDefault(scenario.getNetwork(), scenarioOptions));

        /** starting the viewer */
        ViewerConfig viewerConfig = ViewerConfig.from(db, workingDirectory);
        System.out.println("Used viewer config: " + viewerConfig);
        AmodeusViewerFrame amodeusViewerFrame = new AmodeusViewerFrame(amodeusComponent, outputDirectory, scenario.getNetwork(), scenarioOptions);
        amodeusViewerFrame.setDisplayPosition(viewerConfig.settings.coord, viewerConfig.settings.zoom);
        amodeusViewerFrame.jFrame.setSize(viewerConfig.settings.dimensions);
        amodeusViewerFrame.jFrame.setVisible(true);
    }
}

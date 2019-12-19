import org.apache.log4j.Logger;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.controler.Controler;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

public class RunAmodeusInBerlinTest {

    Logger log = Logger.getLogger(RunAmodeusInBerlinTest.class);

    @Test
    public void RunAmoDeusInBerlinTest() throws IOException, URISyntaxException {
        String[] args = new String[] { "scenarios" , "berlin-v5.4-1pct.config.xml" };

        File workingDirectory = RunAmodeusInBerlin.getWorkingDirectory(args[0]);
        Config config = RunAmodeusInBerlin.prepareConfig(args);

        config.qsim().setNumberOfThreads(4);
        config.global().setNumberOfThreads(4);
        config.controler().setLastIteration(1);

        Scenario scenario = RunAmodeusInBerlin.prepareScenario(config);
        sampleDownPopulation(scenario);

        Controler controler = RunAmodeusInBerlin.prepareControler(scenario, workingDirectory);
        controler.run();
    }

    private void sampleDownPopulation(Scenario scenario) {

        Iterator<? extends Map.Entry<Id<Person>, ? extends Person>> it = scenario.getPopulation().getPersons().entrySet().iterator();

        Random rnd = new Random();

        double scaleFactor = 0.1;
        log.info("sampling population once again by factor " + scaleFactor) ;

        log.info("number of persons before sampling = " + scenario.getPopulation().getPersons().size());

        while(it.hasNext()){
            Map.Entry<Id<Person>, ? extends Person> personEntry = it.next();
            if(rnd.nextDouble() > scaleFactor){
                it.remove();
            }
        }

        log.info("number of persons after sampling = " + scenario.getPopulation().getPersons().size());
        log.info("DONE with sampling");
    }
}
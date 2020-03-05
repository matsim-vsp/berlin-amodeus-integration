package org.matsim.setup;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.TripStructureUtils;

public class BackportMainModeIdentifier implements MainModeIdentifier {
	@Override
	public String identifyMainMode(List<? extends PlanElement> tripElements) {
		Set<String> modes = new HashSet<>();

		for (Leg leg : TripStructureUtils.getLegs(tripElements)) {
			modes.add(leg.getMode());
		}

		if (modes.contains("car"))
			return "car";
		if (modes.contains("pt"))
			return "pt";
		if (modes.contains("bicycle"))
			return "bicycle";
		if (modes.contains("walk"))
			return "walk";
		if (modes.contains("freight"))
			return "freight";
		if (modes.contains("ride"))
			return "ride";
		if (modes.contains("av"))
			return "av";

		return "pt";
	}
}

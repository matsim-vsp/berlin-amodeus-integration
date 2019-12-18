

import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.GeotoolsTransformation;

import ch.ethz.idsc.amodeus.data.ReferenceFrame;

public class BerlinReferenceFrame implements ReferenceFrame {
	@Override
	public CoordinateTransformation coords_toWGS84() {
		return new GeotoolsTransformation("EPSG:31468", "EPSG:4326");
	}

	@Override
	public CoordinateTransformation coords_fromWGS84() {
		return new GeotoolsTransformation("EPSG:4326", "EPSG:31468");
	}
}

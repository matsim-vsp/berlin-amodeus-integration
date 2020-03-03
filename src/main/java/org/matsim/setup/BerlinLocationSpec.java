package org.matsim.setup;

import ch.ethz.idsc.amodeus.data.LocationSpec;
import ch.ethz.idsc.amodeus.data.ReferenceFrame;
import org.matsim.api.core.v01.Coord;

public class BerlinLocationSpec implements LocationSpec {

    public static ReferenceFrame REFERENCE_FRAME = new BerlinReferenceFrame();

    @Override
    public ReferenceFrame referenceFrame() {
        return REFERENCE_FRAME;
    }

    @Override
    public Coord center() {
        return new Coord(4595438.15, 5821747.77);
    }

    @Override
    public String name() {
        return "BERLIN";
    }
}

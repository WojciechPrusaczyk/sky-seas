package pl.skyseas.shape;

import pl.skyseas.config.WorldParams;

/** Per-block values derived from the seabed height of a column. Allocation-free. */
public final class SeabedColumn {
    private SeabedColumn() {
    }

    /** Terrain density: positive (rock) below the seabed surface, negative above. */
    public static double density(WorldParams p, double height, int y) {
        return (height - y) / p.seabedDensityScale();
    }

    /** Rock between the seabed surface and the bottom of the world. */
    public static double rockThickness(WorldParams p, double height) {
        return height - p.worldMinY();
    }

    /** 0 inside the roof band and above it, rising to 1 over {@code caveMaskRamp} blocks below. */
    public static double caveMask(WorldParams p, double height, int y) {
        return ShapeMath.clamp((height - p.caveRoofThickness() - y) / p.caveMaskRamp(), 0.0, 1.0);
    }

    /**
     * True for blocks of the roof that must stay solid, given the seabed density at the
     * block ({@link #density}), from the surface block down. On steep slopes interpolated
     * caves of a neighbouring cell can reach the surface block too.
     */
    public static boolean inRoof(WorldParams p, double seabedDensity) {
        double depth = seabedDensity * p.seabedDensityScale();
        return depth > 0.0 && depth <= p.caveRoofThickness();
    }

    /** Density added to cheese caves: largest under thin rock, zero under thick rock. */
    public static double caveCheeseBias(WorldParams p, double height) {
        double t = (p.caveThickRock() - rockThickness(p, height)) / (p.caveThickRock() - p.caveThinRock());
        return p.caveThicknessScale() * p.caveMaxCheeseBias() * ShapeMath.clamp(t, 0.0, 1.0);
    }

    /**
     * Lower bound for the aquifer floodedness noise: 1 (always flooded with sea water) in
     * the roof band and above it, falling to -1 (no effect) where caves are fully open.
     * Without it, a dry aquifer cell just below the seabed turns blocks above the seabed
     * into an air pocket sealed by a barrier.
     */
    public static double aquiferFloodFloor(WorldParams p, double height, int y) {
        return 1.0 - 2.0 * caveMask(p, height, y);
    }

    /** Biome "depth" parameter: 0 in water and air, growing below the seabed surface. */
    public static double biomeDepth(WorldParams p, double height, int y) {
        return Math.max(0.0, height - y) / p.climateDepthScale();
    }
}

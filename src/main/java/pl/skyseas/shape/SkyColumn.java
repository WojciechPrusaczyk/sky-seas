package pl.skyseas.shape;

import pl.skyseas.config.WorldParams;

/**
 * 3D density of a sky island from its column values (edge distance, top, bottom and the
 * weight of the 3D detail). The 3D noise only carves: it never adds mass outside the 2D
 * column, so it cannot leave loose blocks off the edges. Everything else is plain 2D math.
 */
public final class SkyColumn {
    /** Density bounds (blocks / skyDensityScale); far from islands the value saturates. */
    public static final double MIN = -64.0;
    public static final double MAX = 64.0;
    /** Near the coast, carving starts this far (blocks) from the rim's top and underside, at full strength here. */
    private static final double FACE_MIN = 4.0;
    private static final double FACE_MAX = 10.0;

    private SkyColumn() {
    }

    public static double density(WorldParams p, long noiseSeed, double x, double y, double z,
                                 double edge, double top, double bottom, double detail) {
        // Inside the outline at least 1: cell corners grazing the coast would interpolate to loose slivers.
        double d = Math.min(Math.min(top - y, y - bottom), edge > 0.0 ? Math.max(edge, 1.0) : edge);
        if (d > 0.0 && detail > 0.0) {
            // Carving must change slower than the distance to the surface (gradient below 1),
            // otherwise it cuts loose pieces off thin rims; amplitude and scales keep it there.
            double n = 0.8 * SimplexNoise.noise3(noiseSeed, x / 40.0, y / 30.0, z / 40.0)
                    + 0.2 * SimplexNoise.noise3(noiseSeed + 1, x / 16.0, y / 12.0, z / 16.0);
            if (n < 0.0) {
                // Near the coast only the cliff face is carved (alcoves, overhangs): the rim's top
                // and underside stay whole, so interpolation never leaves loose lumps on them.
                double face = ShapeMath.smoothstep(FACE_MIN, FACE_MAX, Math.min(top - y, y - bottom) - edge);
                double band = p.continentEdgeBand();
                d += p.continentEdgeNoise() * detail * n * ShapeMath.lerp(face, 1.0, ShapeMath.smoothstep(0.5 * band, band, edge));
            }
        }
        d = Math.min(d, y - (p.continentMinBottomY() - 0.5));
        return ShapeMath.clamp(d / p.skyDensityScale(), MIN, MAX);
    }

    /** Per-block density of hanging spires (blocks, see {@code Layout#spireDensity}), scaled like islands. */
    public static double scale(WorldParams p, double blocks) {
        return ShapeMath.clamp(blocks / p.skyDensityScale(), MIN, MAX);
    }
}

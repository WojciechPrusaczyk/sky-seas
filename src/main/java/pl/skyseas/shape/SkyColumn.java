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

    /** Coast rim shaping (blocks): band from the outline, noise scale, undercut slope and depth, rounding radius. */
    private static final double RIM_BAND = 14.0;
    private static final double RIM_SCALE = 60.0;
    /** Undercut on about half of the coast (more covers narrow towers and cuts pieces off). */
    private static final double RIM_UNDERCUT = 0.5;
    private static final double RIM_UNDERCUT_DEPTH = 12.0;
    private static final double RIM_ROUND_MIN = 2.0;
    private static final double RIM_ROUND_MAX = 5.0;

    public static double density(WorldParams p, long noiseSeed, double x, double y, double z,
                                 double edge, double top, double bottom, double detail) {
        // Inside the outline at least 2: cell corners grazing the coast would interpolate to loose slivers.
        double rim = edge > 0.0 ? Math.max(edge, 2.0) : edge;
        double depth = top - y;
        if (rim > -1.0 && rim < RIM_BAND && depth > -1.0 && y > bottom) {
            // Coast rim (one 2D noise): a rounded top edge of varying radius, and an undercut on part of
            // the coast. Both only remove rock, so nothing grows past the outline.
            double n = SimplexNoise.noise2(noiseSeed + 7, x / RIM_SCALE, z / RIM_SCALE);
            // Thin rims (small islands, tapered edges) are not undercut: it would cut off pieces.
            rim -= RIM_UNDERCUT * Math.max(0.0, n) * ShapeMath.clamp(depth, 0.0, RIM_UNDERCUT_DEPTH)
                    * ShapeMath.smoothstep(8.0, 20.0, top - bottom);
            // Rounded top and bottom edges; on thin rims (small islands) together a lens-shaped edge.
            double r = Math.min(RIM_ROUND_MIN + (RIM_ROUND_MAX - RIM_ROUND_MIN) * (0.5 + 0.5 * n), 0.5 * (top - bottom));
            double up = y - bottom;
            if (rim < r && depth < r && depth > 0.0) {
                double a = r - Math.max(rim, 0.0);
                double b = r - depth;
                rim = Math.min(rim, r - Math.sqrt(a * a + b * b));
            }
            if (rim < r && up < r) {
                double a = r - Math.max(rim, 0.0);
                double b = r - up;
                rim = Math.min(rim, r - Math.sqrt(a * a + b * b));
            }
        }
        double d = Math.min(Math.min(depth, y - bottom), rim);
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

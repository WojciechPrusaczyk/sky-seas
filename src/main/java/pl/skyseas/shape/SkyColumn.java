package pl.skyseas.shape;

import pl.skyseas.config.WorldParams;

/**
 * 3D density of a sky island from its column values (edge distance, top, bottom,
 * mountain height). 3D noise only near the edge and on mountain slopes, and only within
 * the column's Y range; everything else is plain 2D math.
 */
public final class SkyColumn {
    /** Density bounds (blocks / skyDensityScale); far from islands the value saturates. */
    public static final double MIN = -64.0;
    public static final double MAX = 64.0;

    private SkyColumn() {
    }

    public static double density(WorldParams p, long noiseSeed, double x, double y, double z,
                                 double edge, double top, double bottom, double mountain) {
        double d = Math.min(Math.min(top - y, y - bottom), edge);
        double a = p.continentEdgeNoise();
        if (y >= bottom - a - 4.0 && y <= top + a + 4.0) {
            double band = 1.0 - ShapeMath.smoothstep(0.0, p.continentEdgeBand(), edge);
            double slope = 0.6 * ShapeMath.smoothstep(4.0, 24.0, mountain);
            double w = Math.max(band, slope);
            if (w > 0.0) {
                double n = 0.7 * SimplexNoise.noise3(noiseSeed, x / 40.0, y / 24.0, z / 40.0)
                        + 0.3 * SimplexNoise.noise3(noiseSeed + 1, x / 13.0, y / 9.0, z / 13.0);
                // Adding less than carving keeps loose debris off the edges.
                d += a * w * (n > 0.0 ? 0.5 * n : n);
            }
        }
        d = Math.min(d, y - (p.continentMinBottomY() - 0.5));
        return ShapeMath.clamp(d / p.skyDensityScale(), MIN, MAX);
    }
}

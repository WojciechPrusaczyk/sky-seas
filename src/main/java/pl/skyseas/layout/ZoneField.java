package pl.skyseas.layout;

import pl.skyseas.config.WorldParams;
import pl.skyseas.shape.ShapeMath;

/**
 * Distance-from-spawn factor 0..1: {@code smoothstep(0, Rmax, d + A * noise)}, with a
 * plateau beyond {@code Rmax}. The noise term makes zone borders ragged.
 */
public final class ZoneField {
    private ZoneField() {
    }

    /**
     * @param jitter noise value, roughly -1..1
     */
    public static double factor(WorldParams p, int x, int z, double jitter) {
        double d = Math.sqrt((double) x * x + (double) z * z);
        return factorAtDistance(p, d + p.zoneJitterAmplitude() * jitter);
    }

    /** Zone factor at a plain distance (no jitter). */
    public static double factorAtDistance(WorldParams p, double distance) {
        return ShapeMath.smoothstep(distance / p.zoneMaxRadius());
    }
}

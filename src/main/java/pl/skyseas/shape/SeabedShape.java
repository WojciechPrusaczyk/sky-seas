package pl.skyseas.shape;

import pl.skyseas.config.WorldParams;
import pl.skyseas.layout.ZoneField;

/**
 * Seabed height (E1 forms): base depth growing with the zone factor, gentle dunes and
 * trenches. Trenches follow the zero line of a ridge noise, exist only in cold water
 * and only beyond the Haven, and get deeper with the zone, down to {@code seabedTrenchMinY}.
 */
public final class SeabedShape {
    private SeabedShape() {
    }

    /**
     * @param zone        zone factor 0..1
     * @param temperature climate temperature (vanilla scale, -1..1)
     * @param dunes       dune noise, roughly -1..1
     * @param trench      trench ridge noise, roughly -1..1; trenches run where it is near 0
     * @return seabed surface height (blocks)
     */
    public static double height(WorldParams p, double zone, double temperature, double dunes, double trench) {
        double base = ShapeMath.lerp(p.seabedBaseYNear(), p.seabedBaseYFar(), zone);
        double h = base + p.seabedDuneAmplitude() * ShapeMath.clamp(dunes, -1.0, 1.0);

        double strength = trenchStrength(p, zone, temperature);
        if (strength > 0.0) {
            double ridge = 1.0 - Math.abs(trench) / p.seabedTrenchWidth();
            if (ridge > 0.0) {
                double profile = ShapeMath.smoothstep(ridge);
                h -= (h - p.seabedTrenchMinY()) * profile * strength;
            }
        }
        return Math.max(h, p.seabedTrenchMinY());
    }

    /** 0..1: how deep trenches may cut here (cold water and distance from spawn). */
    static double trenchStrength(WorldParams p, double zone, double temperature) {
        double cold = ShapeMath.smoothstep(p.seabedTrenchMaxTemperature(),
                p.seabedTrenchMaxTemperature() - p.seabedTrenchTemperatureFade(), temperature);
        if (cold <= 0.0) {
            return 0.0;
        }
        double start = ZoneField.factorAtDistance(p, p.zoneRadii()[0]);
        double far = ShapeMath.clamp((zone - start) / (1.0 - start), 0.0, 1.0);
        return cold * far;
    }

    /** Lowest possible seabed height for these parameters. */
    public static double minHeight(WorldParams p) {
        return p.seabedTrenchMinY();
    }

    /** Highest possible seabed height for these parameters. */
    public static double maxHeight(WorldParams p) {
        return Math.max(p.seabedBaseYNear(), p.seabedBaseYFar()) + p.seabedDuneAmplitude();
    }
}

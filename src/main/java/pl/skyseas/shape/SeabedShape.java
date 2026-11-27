package pl.skyseas.shape;

import pl.skyseas.config.WorldParams;
import pl.skyseas.layout.ZoneField;

/**
 * Seabed height (E1 forms): base depth growing with the zone factor, multi-scale relief
 * (hills and basins from a multi-octave noise, strength varying by region), dunes,
 * canyons in temperate oceans and trenches in cold ones.
 *
 * <p>Canyons and trenches follow the zero lines of ridge noises. Trenches exist only
 * beyond the Haven and get deeper with the zone, down to {@code seabedTrenchMinY}.
 */
public final class SeabedShape {
    private SeabedShape() {
    }

    /**
     * Noise inputs are roughly -1..1.
     *
     * @param zone        zone factor 0..1
     * @param temperature climate temperature (vanilla scale, -1..1)
     * @param relief      large-scale relief noise
     * @param roughness   regional noise: how strong the relief is
     * @param dunes       dune noise
     * @param canyon      canyon ridge noise; canyons run where it is near 0
     * @param trench      trench ridge noise; trenches run where it is near 0
     * @return seabed surface height (blocks)
     */
    public static double height(WorldParams p, double zone, double temperature, double relief, double roughness,
                                double dunes, double canyon, double trench) {
        double h = ShapeMath.lerp(p.seabedBaseYNear(), p.seabedBaseYFar(), zone);

        double strength = ShapeMath.lerp(p.seabedReliefMinStrength(), 1.0, ShapeMath.smoothstep(-0.5, 0.5, roughness));
        double sat = p.seabedReliefSaturation();
        h += sat * Math.tanh(relief / sat) * strength * p.seabedReliefAmplitude();
        h += p.seabedDuneAmplitude() * ShapeMath.clamp(dunes, -1.0, 1.0);

        double canyonStrength = canyonStrength(p, temperature);
        if (canyonStrength > 0.0) {
            double profile = ridgeProfile(canyon, p.seabedCanyonWidth());
            h -= ShapeMath.lerp(p.seabedCanyonDepthNear(), p.seabedCanyonDepthFar(), zone) * profile * canyonStrength;
        }

        double trenchStrength = trenchStrength(p, zone, temperature);
        if (trenchStrength > 0.0) {
            double profile = ridgeProfile(trench, p.seabedTrenchWidth());
            h -= (h - p.seabedTrenchMinY()) * profile * trenchStrength;
        }
        return Math.max(h, p.seabedTrenchMinY());
    }

    /** 1 on the zero line of a ridge noise, falling smoothly to 0 at {@code width}. */
    private static double ridgeProfile(double noise, double width) {
        double ridge = 1.0 - Math.abs(noise) / width;
        return ridge > 0.0 ? ShapeMath.smoothstep(ridge) : 0.0;
    }

    /** 0..1: canyons in temperate oceans, fading at both temperature bounds. */
    static double canyonStrength(WorldParams p, double temperature) {
        double fade = p.seabedCanyonTemperatureFade();
        return ShapeMath.smoothstep(p.seabedCanyonMinTemperature(), p.seabedCanyonMinTemperature() + fade, temperature)
                * ShapeMath.smoothstep(p.seabedCanyonMaxTemperature(), p.seabedCanyonMaxTemperature() - fade, temperature);
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
        return SeabedProvinces.lowest(p);
    }

    /** Highest possible seabed height for these parameters (provinces included). */
    public static double maxHeight(WorldParams p) {
        return p.seabedForms().maxY();
    }
}

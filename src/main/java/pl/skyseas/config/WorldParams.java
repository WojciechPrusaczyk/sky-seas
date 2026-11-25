package pl.skyseas.config;

/**
 * All tunable numbers of the generator. The single source of these values: code reads
 * them from here, never from local constants. Pure Java (no Minecraft classes), so the
 * layout and shape packages can use it in unit tests.
 *
 * <p>E1: only {@link #DEFAULTS}. E7 adds the COMMON config file and the per-world frozen
 * snapshot, which will produce instances of this record.
 *
 * @param worldMinY               bottom of the world (fixed by the design, Y -64)
 * @param worldMaxY               top of the world, exclusive (fixed by the design, Y 320)
 * @param zoneRadii               zone borders in blocks: Haven / Borderlands / Wilds; the last one is
 *                                where the zone factor reaches its plateau
 * @param zoneJitterAmplitude     how far (blocks) noise shifts zone borders, so they are not circles
 * @param seabedBaseYNear         seabed base height at spawn
 * @param seabedBaseYFar          seabed base height at the zone plateau
 * @param seabedDuneAmplitude     height of gentle seabed dunes (blocks, +/-)
 * @param seabedTrenchMinY        deepest trench floor
 * @param seabedTrenchWidth       ridge-noise band treated as trench (noise units, larger = wider)
 * @param seabedTrenchMaxTemperature warmest climate temperature with trenches (vanilla cold ocean bound)
 * @param seabedTrenchTemperatureFade temperature range over which trenches fade in
 * @param seabedDensityScale      blocks per density unit of the seabed gradient
 * @param caveRoofThickness       solid rock kept under the seabed surface (no caves)
 * @param caveMaskRamp            blocks below the roof over which caves fade in
 * @param caveThinRock            rock thickness (blocks) at and below which caves are smallest
 * @param caveThickRock           rock thickness at and above which caves get no penalty
 * @param caveMaxCheeseBias       density added to cheese caves under thin rock (fewer, smaller chambers)
 * @param caveThicknessScale      multiplier of that effect (config key caves.thicknessScale)
 * @param climateDepthScale       blocks below the seabed per unit of the biome "depth" parameter
 */
public record WorldParams(
        int worldMinY,
        int worldMaxY,
        int[] zoneRadii,
        double zoneJitterAmplitude,
        double seabedBaseYNear,
        double seabedBaseYFar,
        double seabedDuneAmplitude,
        double seabedTrenchMinY,
        double seabedTrenchWidth,
        double seabedTrenchMaxTemperature,
        double seabedTrenchTemperatureFade,
        double seabedDensityScale,
        double caveRoofThickness,
        double caveMaskRamp,
        double caveThinRock,
        double caveThickRock,
        double caveMaxCheeseBias,
        double caveThicknessScale,
        double climateDepthScale) {

    /** Bumped whenever a code change alters generated chunks (see CLAUDE.md). */
    public static final int GENERATOR_VERSION = 1;

    public static final WorldParams DEFAULTS = new WorldParams(
            -64,
            320,
            new int[]{1500, 4000, 8000},
            400.0,
            -5.0,
            -20.0,
            2.5,
            -40.0,
            0.08,
            -0.15,
            0.1,
            8.0,
            12.0,
            8.0,
            40.0,
            120.0,
            0.5,
            1.0,
            100.0);

    /** Distance at which the zone factor reaches 1 (start of the Edge). */
    public double zoneMaxRadius() {
        return zoneRadii[zoneRadii.length - 1];
    }
}

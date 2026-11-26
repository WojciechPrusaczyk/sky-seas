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
 * @param seabedReliefAmplitude   multi-scale relief: blocks per unit of relief noise (+/-), before regional strength
 * @param seabedReliefSaturation  relief noise is softly limited to +/- this value (tanh), so rare extremes
 *                                stay within {@code amplitude * saturation} blocks
 * @param seabedReliefMinStrength relief strength in the calmest regions (0..1; 1 in the roughest)
 * @param seabedCanyonDepthNear   canyon depth below the surrounding seabed at spawn
 * @param seabedCanyonDepthFar    canyon depth at the zone plateau
 * @param seabedCanyonWidth       ridge-noise band treated as canyon (noise units, larger = wider)
 * @param seabedCanyonMinTemperature coldest climate temperature with canyons (temperate oceans)
 * @param seabedCanyonMaxTemperature warmest climate temperature with canyons
 * @param seabedCanyonTemperatureFade temperature range over which canyons fade at both bounds
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
 * @param layoutRegionSize        side of a layout region (blocks)
 * @param layoutBucketSize        side of a bucket of the region's island index (blocks)
 * @param layoutCacheRegions      maximum number of cached layout regions
 * @param continentCellSize       continent grid cell (blocks); one candidate per cell
 * @param continentRadiusMin      smallest continent radius
 * @param continentRadiusMax      largest continent radius
 * @param continentOutlineNoise   outline radius varies by +/- this fraction (bays, peninsulas)
 * @param continentWarp           domain warp of the outline, fraction of the radius
 * @param continentBaseYMin       lowest flat terrain height of a continent
 * @param continentBaseYMax       highest flat terrain height of a continent
 * @param continentPeakMaxY       highest mountain peak
 * @param continentCrustMin       thinnest crust under flat terrain
 * @param continentCrustMax       thickest crust under flat terrain
 * @param continentRootsMin       fewest cone roots under a continent
 * @param continentRootsMax       most cone roots under a continent
 * @param continentRootDepthMax   longest root below the crust
 * @param continentEdgeBand       width of the band along the edge with 3D noise (overhangs, arches)
 * @param continentEdgeNoise      strength of that 3D noise (blocks)
 * @param continentMountainThreshold terrain region noise level below which the land is flat plains
 * @param continentMinBottomY     nothing of a sky island below this Y (50 blocks over the sea)
 * @param starterContinentRadius  radius of the starter continent at 0,0
 * @param starterPlateauRadius    radius of the flat spawn plateau on the starter continent
 * @param islandReachMargin       island reach = outline + this margin (land climate above it)
 * @param skyDensityScale         blocks per density unit of sky islands
 * @param clearSkyMargin          no sky island within this many blocks (horizontally) of land at sea level
 * @param oceanWarmMinTemperature warmest ocean class (vanilla warm ocean bound): atolls
 * @param oceanLukewarmMinTemperature lukewarm ocean bound: sea spires
 * @param oceanColdMaxTemperature cold ocean upper bound (vanilla): volcanoes between this and the frozen bound
 * @param oceanFrozenMaxTemperature frozen ocean upper bound (vanilla)
 * @param oceanIslandCellSize     ocean island grid cell (blocks); one candidate per cell
 * @param oceanIslandChance       chance that a cell has an island (before the clear-sky rule)
 * @param oceanIslandRadius       class radius bounds: small {0, 1}, medium {1, 2}, large {2, 3} (blocks)
 * @param oceanIslandMediumShare  share of medium islands
 * @param oceanIslandLargeShareMax share of large islands at the zone plateau (none in the Haven, rising beyond it)
 * @param oceanIslandTopMin       lowest summit above sea level per class (small, medium, large)
 * @param oceanIslandTopMax       highest summit above sea level per class
 * @param oceanShelfYMin          lowest shelf (shallows) around an island
 * @param oceanShelfYMax          highest shelf
 * @param oceanShelfWidthMin      narrowest shelf per class
 * @param oceanShelfWidthMax      widest shelf per class
 * @param oceanShelfSlopeMin      narrowest slope from the shelf down to the seabed
 * @param oceanShelfSlopeMax      widest slope
 * @param atollChance             chance that a medium or large island in warm water is an atoll
 * @param atollLagoonYMin         deepest lagoon floor
 * @param atollLagoonYMax         shallowest lagoon floor
 * @param seamountCellSize        seamount grid cell (temperate oceans)
 * @param seamountChance          chance per cell
 * @param seamountRadiusMin       smallest seamount base radius
 * @param seamountRadiusMax       largest seamount base radius
 * @param seamountPeakYMin        lowest seamount summit
 * @param seamountPeakYMax        highest seamount summit (just under the surface)
 * @param volcanoCellSize         volcano grid cell (cold oceans)
 * @param volcanoChance           chance per cell
 * @param volcanoRadiusMin        smallest volcano base radius
 * @param volcanoRadiusMax        largest volcano base radius
 * @param volcanoRimYMin          lowest crater rim
 * @param volcanoRimYMax          highest crater rim
 * @param volcanoCraterDepthMin   shallowest crater (below the rim)
 * @param volcanoCraterDepthMax   deepest crater
 * @param seaSpireCellSize        sea spire group grid cell (lukewarm oceans)
 * @param seaSpireChance          chance per cell
 * @param seaSpireCountMax        most spires in a group
 * @param seaSpireSpread          spires of a group stand within this radius
 * @param seaSpireRadiusMin       thinnest spire
 * @param seaSpireRadiusMax       thickest spire
 * @param seaSpireTopMin          lowest spire top above sea level
 * @param seaSpireTopMax          highest spire top above sea level
 */
public record WorldParams(
        int worldMinY,
        int worldMaxY,
        int[] zoneRadii,
        double zoneJitterAmplitude,
        double seabedBaseYNear,
        double seabedBaseYFar,
        double seabedDuneAmplitude,
        double seabedReliefAmplitude,
        double seabedReliefSaturation,
        double seabedReliefMinStrength,
        double seabedCanyonDepthNear,
        double seabedCanyonDepthFar,
        double seabedCanyonWidth,
        double seabedCanyonMinTemperature,
        double seabedCanyonMaxTemperature,
        double seabedCanyonTemperatureFade,
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
        double climateDepthScale,
        int layoutRegionSize,
        int layoutBucketSize,
        int layoutCacheRegions,
        int continentCellSize,
        double continentRadiusMin,
        double continentRadiusMax,
        double continentOutlineNoise,
        double continentWarp,
        double continentBaseYMin,
        double continentBaseYMax,
        double continentPeakMaxY,
        double continentCrustMin,
        double continentCrustMax,
        int continentRootsMin,
        int continentRootsMax,
        double continentRootDepthMax,
        double continentEdgeBand,
        double continentEdgeNoise,
        double continentMountainThreshold,
        double continentMinBottomY,
        double starterContinentRadius,
        double starterPlateauRadius,
        double islandReachMargin,
        double skyDensityScale,
        double clearSkyMargin,
        double oceanWarmMinTemperature,
        double oceanLukewarmMinTemperature,
        double oceanColdMaxTemperature,
        double oceanFrozenMaxTemperature,
        int oceanIslandCellSize,
        double oceanIslandChance,
        double[] oceanIslandRadius,
        double oceanIslandMediumShare,
        double oceanIslandLargeShareMax,
        double[] oceanIslandTopMin,
        double[] oceanIslandTopMax,
        double oceanShelfYMin,
        double oceanShelfYMax,
        double[] oceanShelfWidthMin,
        double[] oceanShelfWidthMax,
        double oceanShelfSlopeMin,
        double oceanShelfSlopeMax,
        double atollChance,
        double atollLagoonYMin,
        double atollLagoonYMax,
        int seamountCellSize,
        double seamountChance,
        double seamountRadiusMin,
        double seamountRadiusMax,
        double seamountPeakYMin,
        double seamountPeakYMax,
        int volcanoCellSize,
        double volcanoChance,
        double volcanoRadiusMin,
        double volcanoRadiusMax,
        double volcanoRimYMin,
        double volcanoRimYMax,
        double volcanoCraterDepthMin,
        double volcanoCraterDepthMax,
        int seaSpireCellSize,
        double seaSpireChance,
        int seaSpireCountMax,
        double seaSpireSpread,
        double seaSpireRadiusMin,
        double seaSpireRadiusMax,
        double seaSpireTopMin,
        double seaSpireTopMax) {

    /** Bumped whenever a code change alters generated chunks (see CLAUDE.md). */
    public static final int GENERATOR_VERSION = 9;

    public static final WorldParams DEFAULTS = new WorldParams(
            -64,
            320,
            new int[]{1500, 4000, 8000},
            400.0,
            -5.0,
            -20.0,
            2.0,
            30.0,
            0.4,
            0.6,
            8.0,
            14.0,
            0.1,
            -0.15,
            0.2,
            0.05,
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
            100.0,
            1024,
            64,
            512,
            1800,
            230.0,
            690.0,
            0.12,
            0.06,
            135.0,
            175.0,
            250.0,
            10.0,
            25.0,
            3,
            8,
            40.0,
            24.0,
            12.0,
            -0.3,
            113.0,
            350.0,
            150.0,
            16.0,
            8.0,
            16.0,
            0.55,
            0.2,
            -0.15,
            -0.45,
            300,
            0.8,
            new double[]{2.5, 15.0, 75.0, 200.0},
            0.25,
            0.08,
            new double[]{1.0, 4.0, 10.0},
            new double[]{8.0, 18.0, 40.0},
            35.0,
            60.0,
            new double[]{6.0, 20.0, 45.0},
            new double[]{20.0, 60.0, 130.0},
            45.0,
            90.0,
            0.5,
            52.0,
            59.0,
            900,
            0.5,
            60.0,
            150.0,
            40.0,
            58.0,
            800,
            0.7,
            70.0,
            140.0,
            15.0,
            50.0,
            8.0,
            18.0,
            450,
            0.6,
            4,
            30.0,
            3.0,
            8.0,
            8.0,
            35.0);

    /** Distance at which the zone factor reaches 1 (start of the Edge). */
    public double zoneMaxRadius() {
        return zoneRadii[zoneRadii.length - 1];
    }
}

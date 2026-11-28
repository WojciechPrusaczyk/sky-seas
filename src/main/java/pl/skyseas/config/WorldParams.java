package pl.skyseas.config;

/**
 * All tunable numbers of the generator. The single source of these values: code reads
 * them from here, never from local constants. Pure Java (no Minecraft classes), so the
 * layout and shape packages can use it in unit tests.
 *
 * <p>{@link #DEFAULTS} are the config defaults; the world uses {@link #current()}, its frozen
 * snapshot (E7, {@code pl.skyseas.params}).
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
 * @param continentBoxGapMin      narrowest gap between the outline boxes of neighbouring continents (blocks);
 *                                half of it stays free on each side inside a grid cell. Outlines do not fill
 *                                their boxes, so coast-to-coast gaps come out about 170 blocks wider: 400-800
 * @param continentBoxGapMax      widest such gap
 * @param continentOutlineNoise   outline radius varies by +/- this fraction (bays, peninsulas)
 * @param continentWarp           domain warp of the outline, fraction of the radius
 * @param continentBaseYMin       lowest flat terrain height of a continent (tilt and members included)
 * @param continentBaseYMax       highest flat terrain height of a continent (exceeded only when members and tilt
 *                                together span more than the range)
 * @param continentPeakMaxY       highest mountain peak outside massifs
 * @param continentMassifPeakYMin lowest massif summit
 * @param continentMassifPeakYMax highest massif summit
 * @param continentMassifShareMin share of continents with a massif at spawn
 * @param continentMassifShareMax share of continents with a massif at the zone plateau
 * @param continentTiltMin        least tilt of a continent (height difference across it, blocks)
 * @param continentTiltMax        most tilt
 * @param continentCompositeShare share of composite continents (2-4 members joined by isthmuses or rock bridges)
 * @param continentMemberStepMin  least height difference between joined members
 * @param continentMemberStepMax  largest height difference between joined members
 * @param continentElevatedShare  share of elevated continents beyond the Haven
 * @param continentElevatedBaseYMin lowest flat terrain of an elevated continent
 * @param continentElevatedBaseYMax highest flat terrain of an elevated continent
 * @param continentLowShare       share of continents (not the starter) lying low, their coasts and undersides in the sea
 * @param continentLowBaseYMin    lowest flat terrain of a low continent
 * @param continentLowBaseYMax    highest flat terrain of a low continent
 * @param starterBaseYMin         lowest flat terrain of the starter continent
 * @param starterBaseYMax         highest flat terrain of the starter continent
 * @param continentCrustMin       thinnest crust under flat terrain; at least the rim thickness (16), since cells
 *                                of the interpolated density are 8 blocks tall and thinner slabs break up
 * @param continentCrustMax       thickest crust under flat terrain
 * @param continentSpiresMin      fewest rock spires hanging under a continent
 * @param continentSpiresMax      most hanging spires
 * @param continentSpireLengthMin shortest hanging spire (blocks)
 * @param continentSpireLengthMax longest hanging spire (blocks); shorter where the underside is low
 * @param continentEdgeBand       width of the band along the edge with 3D noise (overhangs, arches)
 * @param continentEdgeNoise      strength of that 3D noise (blocks)
 * @param continentMountainThreshold terrain region noise level below which the land is flat plains
 * @param continentMinBottomY     nothing of a sky form below this Y (low continents and islands sink into the sea, never to the seabed)
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
 * @param skyForms                towers, sky islands and clusters between continents (round 2 of E3)
 * @param seabedForms             seabed provinces and their forms (round 3 of E3)
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
        double continentBoxGapMin,
        double continentBoxGapMax,
        double continentOutlineNoise,
        double continentWarp,
        double continentBaseYMin,
        double continentBaseYMax,
        double continentPeakMaxY,
        double continentMassifPeakYMin,
        double continentMassifPeakYMax,
        double continentMassifShareMin,
        double continentMassifShareMax,
        double continentTiltMin,
        double continentTiltMax,
        double continentCompositeShare,
        double continentMemberStepMin,
        double continentMemberStepMax,
        double continentElevatedShare,
        double continentElevatedBaseYMin,
        double continentElevatedBaseYMax,
        double continentLowShare,
        double continentLowBaseYMin,
        double continentLowBaseYMax,
        double starterBaseYMin,
        double starterBaseYMax,
        double continentCrustMin,
        double continentCrustMax,
        int continentSpiresMin,
        int continentSpiresMax,
        double continentSpireLengthMin,
        double continentSpireLengthMax,
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
        double seaSpireTopMax,
        SkyForms skyForms,
        SeabedForms seabedForms) {

    private static WorldParams current;

    /**
     * Parameters of the running world: the frozen snapshot, set before the levels load
     * (single server per JVM); {@link #DEFAULTS} until then and in tests.
     */
    public static WorldParams current() {
        return current == null ? DEFAULTS : current;
    }

    public static void setCurrent(WorldParams params) {
        current = params;
    }

    /** Bumped whenever a code change alters generated chunks (see CLAUDE.md). */
    public static final int GENERATOR_VERSION = 31;

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
            12.0,
            30.0,
            75.0,
            0.2,
            1.0,
            100.0,
            1024,
            64,
            512,
            1700,
            240.0,
            600.0,
            0.12,
            0.06,
            100.0,
            200.0,
            250.0,
            270.0,
            300.0,
            0.1,
            0.3,
            20.0,
            40.0,
            0.3,
            20.0,
            60.0,
            0.15,
            200.0,
            240.0,
            0.17,
            70.0,
            90.0,
            140.0,
            150.0,
            16.0,
            28.0,
            3,
            8,
            10.0,
            40.0,
            32.0,
            8.0,
            -0.3,
            30.0,
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
            35.0,
            new SkyForms(
                305.0,
                70.0,
                70.0,
                80,
                0.8,
                0.1,
                0.5,
                5.0,
                15.0,
                15.0,
                40.0,
                7.5,
                20.0,
                50.0,
                150.0,
                0.45,
                0.3,
                300.0,
                480.0,
                650.0,
                230.0,
                20,
                60,
                7.5,
                75.0,
                4,
                20,
                50,
                10.0,
                25.0,
                40.0,
                120.0,
                32.0,
                16.0,
                0.04,
                1100.0,
                900.0,
                0.1,
                64.0,
                75.0,
                80.0,
                160.0,
                40.0,
                0.45,
                0.3,
                0.3,
                200.0,
                260.0),
            new SeabedForms(
                800.0,
                0.10,
                1.0, 1.0, 1.0, 0.8, 0.8,
                90.0,
                7.0,
                18.0,
                42.0,
                600.0, 0.2, -45.0,
                260.0, 0.14, -34.0,
                48, 0.75, 7.0, 26.0, 5.0, 38.0,
                14.0, 3,
                34, 0.5, 4.5, 9.0, 18.0, 55.0,
                24.0));

    /** Distance at which the zone factor reaches 1 (start of the Edge). */
    public double zoneMaxRadius() {
        return zoneRadii[zoneRadii.length - 1];
    }

    /**
     * Seabed provinces (round 3 of E3): soft regions of the seabed, each with its own set of
     * forms, so the floor is rarely a flat plain. Weights are relative.
     *
     * @param provinceScale           size of a province cell (warped Voronoi)
     * @param plainsShare             share of the seabed left as plains with dunes
     * @param hillsWeight             hills and underwater mountain ranges
     * @param canyonsWeight           canyon networks down to near the bedrock
     * @param volcanicWeight          fields of small cones
     * @param plateausWeight          scarps, terraces and table plateaus
     * @param rockyWeight             pillars and columns
     * @param provinceBlend           blocks over which neighbouring provinces blend
     * @param midAmplitude            medium-scale bumps (every 50-150 blocks) outside plains
     * @param hillAmplitude           rolling hills in the hills province
     * @param mountainAmplitude       ridged mountain ranges in the hills province
     * @param canyonScale             wavelength of the main canyon network
     * @param canyonWidth             ridge-noise band of a main canyon (larger = wider)
     * @param canyonFloorY            floor of the main canyons; open water much below about -48 meets aquifer
     *                                cells under the hard-coded lava level -54, which turn it into dry pockets
     * @param branchScale             wavelength of the side canyons
     * @param branchWidth
     * @param branchFloorY            floor of the side canyons
     * @param coneCell                grid of cones in volcanic fields
     * @param coneChance              chance of a cone per cell at full province weight
     * @param coneRadiusMin           cone base radius
     * @param coneRadiusMax
     * @param coneHeightMin           cone height above the seabed
     * @param coneHeightMax
     * @param terraceStep             height of one scarp in the plateau province
     * @param terraceSteps            scarps between the lowest and the highest terrace
     * @param pillarCell              grid of pillars in the rocky province
     * @param pillarChance            chance of a pillar per cell at full province weight
     * @param pillarRadiusMin
     * @param pillarRadiusMax
     * @param pillarHeightMin         pillar height above the seabed
     * @param pillarHeightMax
     * @param maxY                    nothing of the seabed rises above this Y (sky forms stay above 30)
     */
    public record SeabedForms(
            double provinceScale,
            double plainsShare,
            double hillsWeight,
            double canyonsWeight,
            double volcanicWeight,
            double plateausWeight,
            double rockyWeight,
            double provinceBlend,
            double midAmplitude,
            double hillAmplitude,
            double mountainAmplitude,
            double canyonScale,
            double canyonWidth,
            double canyonFloorY,
            double branchScale,
            double branchWidth,
            double branchFloorY,
            int coneCell,
            double coneChance,
            double coneRadiusMin,
            double coneRadiusMax,
            double coneHeightMin,
            double coneHeightMax,
            double terraceStep,
            int terraceSteps,
            int pillarCell,
            double pillarChance,
            double pillarRadiusMin,
            double pillarRadiusMax,
            double pillarHeightMin,
            double pillarHeightMax,
            double maxY) {
    }

    /**
     * Sky forms between continents.
     *
     * @param skyMaxY                 highest top of a sky form (continents excepted)
     * @param skyFormMinY             lowest underside of towers and Hallelujah bodies (sunk islands go lower)
     * @param skySpireMinY            lowest tip of a spire under any sky form (continents included)
     * @param skyCellSize             grid cell of single sky forms (towers, small and medium islands); one per cell
     * @param skyCellChance           chance of a candidate per cell where the sky is densest (before rejections)
     * @param towerShare              share of single forms that are towers
     * @param smallIslandShare        share of the remaining single forms that are small islands
     * @param smallIslandRadiusMin    small sky island radius (10-30 blocks across)
     * @param smallIslandRadiusMax
     * @param mediumIslandRadiusMin   medium sky island radius (30-80 blocks across)
     * @param mediumIslandRadiusMax
     * @param towerRadiusMin          tower radius (15-40 blocks across)
     * @param towerRadiusMax
     * @param towerHeightMin          tower height from flat top to underside
     * @param towerHeightMax
     * @param archipelagoChance       chance of an archipelago at a corner of the continent grid
     * @param hallelujahChance        share of corners with a Hallelujah cluster (corners hold at most one cluster)
     * @param clusterRadiusMin        radius of the disc holding a cluster's island centres
     * @param clusterRadiusMax
     * @param guaranteeDistance       distance from spawn of the guaranteed archipelago and Hallelujah cluster
     * @param guaranteeClusterRadius  disc radius of the guaranteed clusters (they fit between the starter and its neighbours)
     * @param archipelagoCountMin     islands of an archipelago (before rejections)
     * @param archipelagoCountMax
     * @param archipelagoRadiusMin    archipelago island radius (15-150 blocks across)
     * @param archipelagoRadiusMax
     * @param archipelagoBridgesMax   most rock bridges or arches in an archipelago
     * @param hallelujahCountMin      bodies of a Hallelujah cluster (before rejections)
     * @param hallelujahCountMax
     * @param hallelujahRadiusMin     body radius (20-50 blocks across)
     * @param hallelujahRadiusMax
     * @param hallelujahHeightMin     body height
     * @param hallelujahHeightMax
     * @param clusterCorridor         width of the straight flight corridor kept free through every cluster
     * @param skyFormGap              least horizontal gap between the footprints of two large sky forms
     * @param skyDensityMin           chance per cell where the sky is sparsest (density noise lerps to skyCellChance)
     * @param densityScale            wavelength of the density noise (dense swarms, empty stretches)
     * @param heightScale             wavelength of the height noise: islands of an area share a height band
     * @param sunkShare               share of islands sinking into the sea (top just above the water)
     * @param sunkTopMin              top of a sunk island
     * @param sunkTopMax
     * @param islandTopMin            lowest top of an island that is not sunk
     * @param towerTopMin             lowest top of a tower
     * @param detailRadiusMax         islands smaller than this are built per block and may overlap or merge
     * @param detailOverlap           such islands keep their centres at least this share of the summed footprints apart
     * @param continentOverlap        such islands may reach this share of their footprint radius over a continent's coast
     * @param edgeClusterChance       chance of a cluster at the middle of a side of a continent grid cell
     * @param edgeClusterRadiusMin    disc radius of such clusters
     * @param edgeClusterRadiusMax
     */
    public record SkyForms(
            double skyMaxY,
            double skyFormMinY,
            double skySpireMinY,
            int skyCellSize,
            double skyCellChance,
            double towerShare,
            double smallIslandShare,
            double smallIslandRadiusMin,
            double smallIslandRadiusMax,
            double mediumIslandRadiusMin,
            double mediumIslandRadiusMax,
            double towerRadiusMin,
            double towerRadiusMax,
            double towerHeightMin,
            double towerHeightMax,
            double archipelagoChance,
            double hallelujahChance,
            double clusterRadiusMin,
            double clusterRadiusMax,
            double guaranteeDistance,
            double guaranteeClusterRadius,
            int archipelagoCountMin,
            int archipelagoCountMax,
            double archipelagoRadiusMin,
            double archipelagoRadiusMax,
            int archipelagoBridgesMax,
            int hallelujahCountMin,
            int hallelujahCountMax,
            double hallelujahRadiusMin,
            double hallelujahRadiusMax,
            double hallelujahHeightMin,
            double hallelujahHeightMax,
            double clusterCorridor,
            double skyFormGap,
            double skyDensityMin,
            double densityScale,
            double heightScale,
            double sunkShare,
            double sunkTopMin,
            double sunkTopMax,
            double islandTopMin,
            double towerTopMin,
            double detailRadiusMax,
            double detailOverlap,
            double continentOverlap,
            double edgeClusterChance,
            double edgeClusterRadiusMin,
            double edgeClusterRadiusMax) {
    }
}

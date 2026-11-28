package pl.skyseas.layout;

import pl.skyseas.shape.ShapeMath;

/**
 * Biome climate parameters in three dimensions (spec "Biomy i klimat"). Vanilla
 * {@code multi_noise} picks the biome; these values steer it: land climate in the reach
 * of sky islands, ocean climate in water and air over the open sea, cave climate under
 * rock. Thresholds are the 1.21.1 {@code OverworldBiomeBuilder} ones.
 *
 * <p>Inputs per sample: {@code island} = type of the sky island in reach or
 * {@link Layout#NO_CLIMATE}, its {@code bottom} and {@code top} (surface, {@link Layout#NO_TOP}
 * at the rim), {@code height} = seabed (or ocean land) surface Y.
 */
public final class Climate {
    private static final int SEA_LEVEL = 63;
    /** Rock this deep under a surface gets cave climate (caves start at depth 0.2 anyway). */
    private static final double ROCK = 8.0;
    /** Continentalness: far inland (continents), mid inland (other sky forms), near inland (chains, ocean land). */
    private static final double CONTINENT = 0.5;
    private static final double SKY_FORM = 0.15;
    private static final double CHAIN = 0.0;
    /** Floor under rock: mid inland, so caves get cave biomes and never ocean ones. */
    private static final double UNDERGROUND_MIN = 0.1;
    private static final double OCEAN_LAND = -0.05;
    /** Coast (beaches) on ocean land lower than this many blocks above the sea. */
    private static final double BEACH_HEIGHT = 4.0;
    private static final double COAST = -0.15;
    private static final double OCEAN = -0.3;
    private static final double DEEP_OCEAN = -0.7;
    private static final double DEEP_WATER = 30.0;
    private static final double MUSHROOM_BELOW = -1.05;
    private static final double MUSHROOM = -1.1;
    /** Thematic clusters: share of the cluster climate point. */
    private static final double PULL = 0.8;
    private static final double PEAK_EROSION = -0.85;
    /** Deep dark needs erosion below -0.375 (bottom biome at depth 1.1); kept out between these depths. */
    private static final double SHALLOW_ROCK_DEPTH = 0.3;
    private static final double DEEP_DARK_DEPTH = 0.9;
    private static final double NO_DEEP_DARK_EROSION = -0.3;
    /** Under ocean land deeper than {@link #DEEP_DARK_DEPTH}: always deep dark (ancient cities, decision 2026-11-28). */
    private static final double DEEP_DARK_EROSION = -0.6;
    private static final double COOL_FROM_Y = 200.0;
    private static final double COOL_PER_BLOCK = 0.1 / 25.0;
    private static final double WARM = 0.7;
    /** The warm bias stays in the sea and on ocean land. */
    private static final double WARM_MAX_Y = 100.0;

    private Climate() {
    }

    public static boolean inSky(double island, double bottom, int y) {
        return island >= 0.0 && y >= bottom;
    }

    public static double continents(double island, double bottom, double top, double height, int y, double vanilla) {
        if (inSky(island, bottom, y)) {
            if (y < top - ROCK) {
                return Math.max(vanilla, UNDERGROUND_MIN);
            }
            int t = (int) island;
            return t == RegionLayout.CONTINENT ? CONTINENT : (t == RegionLayout.CHAIN ? CHAIN : SKY_FORM);
        }
        if (y < height - ROCK) {
            return Math.max(vanilla, UNDERGROUND_MIN);
        }
        if (height >= SEA_LEVEL) {
            if (vanilla < MUSHROOM_BELOW) {
                return MUSHROOM;
            }
            return height - SEA_LEVEL < BEACH_HEIGHT ? COAST : OCEAN_LAND;
        }
        return SEA_LEVEL - height > DEEP_WATER ? DEEP_OCEAN : OCEAN;
    }

    /**
     * {@code point}: cluster erosion or {@link Layout#NO_CLIMATE}; {@code mountain}: continent mountain height (blocks).
     * Under rock thinner than deep dark needs, erosion stays above the deep dark range: with
     * inland continentalness under rock, vanilla would pick deep dark already at depth ~0.5.
     * Deep under ocean land it is always deep dark, so ancient cities (Y -27) find it there.
     */
    public static double erosion(double island, double bottom, double top, double height, int y, double point,
                                 double mountain, double vanilla, double scale) {
        double e = vanilla;
        if (inSky(island, bottom, y)) {
            e = point > Layout.NO_CLIMATE ? ShapeMath.lerp(vanilla, point, PULL)
                    : ShapeMath.lerp(vanilla, PEAK_EROSION, ShapeMath.smoothstep(10.0, 45.0, mountain));
        }
        double d = depth(island, bottom, top, height, y, scale);
        if (d >= DEEP_DARK_DEPTH && height >= SEA_LEVEL && !inSky(island, bottom, y)) {
            return DEEP_DARK_EROSION;
        }
        return d >= SHALLOW_ROCK_DEPTH && d < DEEP_DARK_DEPTH ? Math.max(e, NO_DEEP_DARK_EROSION) : e;
    }

    /** Cherry patch noise above this: the sky surface gets the cherry grove climate. */
    private static final double CHERRY_FROM = 0.55;
    /** Continent mountains (blocks) keep their own climate. */
    private static final double CHERRY_MAX_MOUNTAIN = 20.0;
    /** A cherry grove cell of the vanilla table: plateau (far inland, erosion class 1, mid slice, variant), temperate, dry. */
    public static final double CHERRY_CONTINENTS = 0.5;
    public static final double CHERRY_EROSION = -0.55;
    public static final double CHERRY_TEMPERATURE = -0.1;
    public static final double CHERRY_VEGETATION = -0.5;
    public static final double CHERRY_RIDGES = 0.33;

    /**
     * Cherry groves: vanilla puts them where four noises meet a narrow cell, so they come out
     * as small patches. On the sky surface (not under the rock, not on mountains, not in
     * thematic clusters), a patch of the cherry noise above {@link #CHERRY_FROM} sets every
     * climate parameter to the cherry grove cell, which gives groves of a few hundred blocks.
     */
    public static boolean inCherry(double island, double bottom, double top, int y, double point, double mountain,
            double cherry) {
        return cherry > CHERRY_FROM && inSky(island, bottom, y) && y >= top - ROCK && point <= Layout.NO_CLIMATE
                && mountain < CHERRY_MAX_MOUNTAIN;
    }

    /** Vanilla frozen ocean below this temperature, cold ocean from it. */
    private static final double FROZEN = -0.45;

    /**
     * {@code patch}: a noise in -1..1. Below the sky, frozen water only where the patch noise
     * is positive, cold elsewhere: vanilla temperature bands are thousands of blocks wide,
     * and an all deep sea turns them into huge frozen seas (Aquamirae's Ice Maze, decision 2026-11-28).
     */
    public static double temperature(double island, double bottom, int y, double point, double warm, double base,
            double patch) {
        if (inSky(island, bottom, y)) {
            double t = point > Layout.NO_CLIMATE ? ShapeMath.lerp(base, point, PULL) : base;
            return t - Math.max(0.0, y - COOL_FROM_Y) * COOL_PER_BLOCK;
        }
        if (warm > 0.0 && y < WARM_MAX_Y) {
            return ShapeMath.lerp(base, Math.max(base, WARM), warm);
        }
        return base < FROZEN && patch < 0.0 ? FROZEN + 0.01 : base; // just inside cold: -0.45 is on the border
    }

    public static double vegetation(double island, double bottom, int y, double point, double base) {
        return inSky(island, bottom, y) && point > Layout.NO_CLIMATE ? ShapeMath.lerp(base, point, PULL) : base;
    }

    /** Ridges magnitude on sky land: peaks-and-valleys "mid", no river valleys. */
    private static final double RIDGES_MID = 0.35;
    /** Ridges magnitude of the vanilla peaks line (peaks-and-valleys 1). */
    private static final double RIDGES_PEAK = 2.0 / 3.0;

    /**
     * Ridges (weirdness) on sky land keep the vanilla sign (biome variants) but a fixed
     * magnitude: vanilla's ridge lines would give river valleys and stripes of
     * peaks-and-valleys classes across islands. Continent mountains move toward the peak line.
     */
    public static double ridges(double island, double bottom, int y, double mountain, double vanilla) {
        if (!inSky(island, bottom, y)) {
            return vanilla;
        }
        double m = ShapeMath.lerp(RIDGES_MID, RIDGES_PEAK, ShapeMath.smoothstep(10.0, 45.0, mountain));
        return vanilla < 0.0 ? -m : m;
    }

    /** Biome depth: blocks under the surface above (sky island top or seabed) over the scale. */
    public static double depth(double island, double bottom, double top, double height, int y, double scale) {
        if (inSky(island, bottom, y)) {
            return y < top ? (top - y) / scale : 0.0;
        }
        return Math.max(0.0, height - y) / scale;
    }
}

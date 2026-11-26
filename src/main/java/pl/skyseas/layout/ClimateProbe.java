package pl.skyseas.layout;

/**
 * Climate temperature at a column, for layout decisions that depend on the water
 * (atolls in warm seas, volcanoes in cold ones). Sampled once per candidate at its
 * centre, so a form is either whole or absent. Must not depend on the layout itself.
 */
@FunctionalInterface
public interface ClimateProbe {
    /** Temperate everywhere: no climate-bound forms except seamounts. */
    ClimateProbe TEMPERATE = (x, z) -> 0.0;

    double temperature(int x, int z);
}

package pl.skyseas.command;

import net.minecraft.world.level.levelgen.DensityFunction;

/** Finds a density function of a given type inside a bound noise router (commands only, allocates). */
public final class RouterLookup {
    private RouterLookup() {
    }

    public static <T extends DensityFunction> T find(DensityFunction root, Class<T> type) {
        Object[] found = new Object[1];
        root.mapAll(f -> {
            if (found[0] == null && type.isInstance(f)) {
                found[0] = f;
            }
            return f;
        });
        return type.cast(found[0]);
    }
}

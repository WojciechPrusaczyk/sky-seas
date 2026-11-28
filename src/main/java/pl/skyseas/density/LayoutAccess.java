package pl.skyseas.density;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.level.levelgen.RandomState;
import pl.skyseas.command.RouterLookup;
import pl.skyseas.layout.Layout;

/**
 * Layout of a RandomState for code outside the density functions (commands; later
 * features, surface rules and structures). Found once in the router, then remembered.
 */
public final class LayoutAccess {
    private static final Map<RandomState, Layout> CACHE = new WeakHashMap<>();
    private static final Map<RandomState, SeabedHeightFunction> SEABED = new WeakHashMap<>();
    private static final Map<RandomState, ZoneFactorFunction> ZONE = new WeakHashMap<>();

    private LayoutAccess() {
    }

    /** The layout of this RandomState, or null if its router has no Sky Seas layout. */
    public static synchronized Layout get(RandomState random) {
        Layout layout = CACHE.get(random);
        if (layout == null) {
            LayoutRootFunction root = RouterLookup.find(random.router().finalDensity(), LayoutRootFunction.class);
            layout = root == null ? null : root.layout();
            if (layout != null) {
                CACHE.put(random, layout);
            }
        }
        return layout;
    }

    /** The zone factor function of this RandomState (unwrapped, thread-safe), or null outside Sky Seas. */
    public static synchronized ZoneFactorFunction zone(RandomState random) {
        ZoneFactorFunction f = ZONE.get(random);
        if (f == null) {
            f = RouterLookup.find(random.router().finalDensity(), ZoneFactorFunction.class);
            if (f != null) {
                ZONE.put(random, f);
            }
        }
        return f;
    }

    /** The seabed height function of this RandomState (unwrapped, thread-safe), or null outside Sky Seas. */
    public static synchronized SeabedHeightFunction seabed(RandomState random) {
        SeabedHeightFunction f = SEABED.get(random);
        if (f == null) {
            f = RouterLookup.find(random.router().finalDensity(), SeabedHeightFunction.class);
            if (f != null) {
                SEABED.put(random, f);
            }
        }
        return f;
    }
}

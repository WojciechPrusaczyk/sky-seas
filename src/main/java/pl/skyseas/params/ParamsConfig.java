package pl.skyseas.params;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;
import pl.skyseas.config.WorldParams;

/**
 * The COMMON config ({@code config/skyseas-common.toml}): every {@link WorldParams} number
 * under its record component name (nested records as sections), defaults from
 * {@link WorldParams#DEFAULTS}. Built by reflection, so a new parameter needs no code here.
 * The world height stays fixed (CLAUDE.md) and is not in the file.
 */
public final class ParamsConfig {
    private static final List<String> FIXED = List.of("worldMinY", "worldMaxY");

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ALLOW_PARAM_CHANGE;
    private static final Map<String, ModConfigSpec.ConfigValue<?>> VALUES = new LinkedHashMap<>();

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        ALLOW_PARAM_CHANGE = b.comment("Overwrite the world's frozen parameter snapshot with this file on the next start.",
                        "Chunks generated before keep the old parameters: expect seams at their border.")
                .define("allowParamChange", false);
        b.push("params");
        define(b, "", WorldParams.DEFAULTS);
        b.pop();
        SPEC = b.build();
    }

    private ParamsConfig() {
    }

    private static void define(ModConfigSpec.Builder b, String prefix, Record defaults) {
        for (RecordComponent c : defaults.getClass().getRecordComponents()) {
            if (FIXED.contains(c.getName())) {
                continue;
            }
            Object v = get(c, defaults);
            String key = prefix + c.getName();
            if (v instanceof Record r) {
                b.push(c.getName());
                define(b, key + ".", r);
                b.pop();
            } else if (v instanceof int[] a) {
                List<Integer> list = new ArrayList<>();
                for (int x : a) {
                    list.add(x);
                }
                VALUES.put(key, b.defineList(c.getName(), list, () -> 0, o -> o instanceof Integer));
            } else if (v instanceof double[] a) {
                List<Double> list = new ArrayList<>();
                for (double x : a) {
                    list.add(x);
                }
                VALUES.put(key, b.defineList(c.getName(), list, () -> 0.0, o -> o instanceof Number));
            } else {
                VALUES.put(key, b.define(c.getName(), v));
            }
        }
    }

    /** Parameters from the config file (call after the config has loaded). */
    public static WorldParams params() {
        return (WorldParams) build("", WorldParams.DEFAULTS);
    }

    private static Record build(String prefix, Record defaults) {
        RecordComponent[] cs = defaults.getClass().getRecordComponents();
        Object[] args = new Object[cs.length];
        Class<?>[] types = new Class<?>[cs.length];
        for (int i = 0; i < cs.length; i++) {
            RecordComponent c = cs[i];
            types[i] = c.getType();
            Object v = get(c, defaults);
            String key = prefix + c.getName();
            if (FIXED.contains(c.getName())) {
                args[i] = v;
            } else if (v instanceof Record r) {
                args[i] = build(key + ".", r);
            } else if (v instanceof int[]) {
                args[i] = ((List<?>) VALUES.get(key).get()).stream().mapToInt(o -> ((Number) o).intValue()).toArray();
            } else if (v instanceof double[]) {
                args[i] = ((List<?>) VALUES.get(key).get()).stream().mapToDouble(o -> ((Number) o).doubleValue()).toArray();
            } else if (v instanceof Integer) {
                args[i] = ((Number) VALUES.get(key).get()).intValue();
            } else {
                args[i] = ((Number) VALUES.get(key).get()).doubleValue();
            }
        }
        try {
            return (Record) defaults.getClass().getDeclaredConstructor(types).newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot build " + defaults.getClass().getSimpleName(), e);
        }
    }

    private static Object get(RecordComponent c, Record r) {
        try {
            return c.getAccessor().invoke(r);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}

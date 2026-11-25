package pl.skyseas.shape;

/** Small allocation-free helpers shared by shapes and layout. */
public final class ShapeMath {
    private ShapeMath() {
    }

    public static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Hermite smoothstep of t, clamped to 0..1. */
    public static double smoothstep(double t) {
        double c = clamp(t, 0.0, 1.0);
        return c * c * (3.0 - 2.0 * c);
    }

    /** 0 at edge0, 1 at edge1 (edges may be in either order). */
    public static double smoothstep(double edge0, double edge1, double v) {
        return smoothstep((v - edge0) / (edge1 - edge0));
    }
}

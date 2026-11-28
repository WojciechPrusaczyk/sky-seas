package pl.skyseas.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.worldgen.lithostitched.api.worldgen.placementcondition.PlacementCondition;
import net.minecraft.core.BlockPos;
import pl.skyseas.density.LayoutAccess;
import pl.skyseas.layout.Layout;

/**
 * Structure start on a sky island at least {@code min_edge} blocks inside its outline
 * (spec "Struktury"). Outside a Sky Seas world it always passes.
 */
public record IslandInterior(int minEdge) implements PlacementCondition {
    public static final MapCodec<IslandInterior> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(0, 256).fieldOf("min_edge").forGetter(IslandInterior::minEdge)
    ).apply(i, IslandInterior::new));

    @Override
    public boolean test(Context context, BlockPos pos) {
        Layout layout = LayoutAccess.get(context.randomState());
        if (layout == null) {
            return true;
        }
        return layout.column(pos.getX(), pos.getZ(), Layout.EDGE) >= minEdge
                && pos.getY() >= layout.column(pos.getX(), pos.getZ(), Layout.BOTTOM);
    }

    @Override
    public MapCodec<? extends PlacementCondition> codec() {
        return CODEC;
    }
}

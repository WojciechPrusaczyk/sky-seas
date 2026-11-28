package pl.skyseas.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.worldgen.lithostitched.api.worldgen.placementcondition.PlacementCondition;
import net.minecraft.core.BlockPos;
import pl.skyseas.density.LayoutAccess;
import pl.skyseas.density.SeabedHeightFunction;
import pl.skyseas.world.WorldAccess;

/**
 * Structure start at least {@code min} blocks under the seabed (ocean land included),
 * measured from the seabed height (spec "Struktury"). Outside a Sky Seas world it always passes.
 */
public record RockAbove(int min) implements PlacementCondition {
    public static final MapCodec<RockAbove> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(0, 384).fieldOf("min").forGetter(RockAbove::min)
    ).apply(i, RockAbove::new));

    @Override
    public boolean test(Context context, BlockPos pos) {
        SeabedHeightFunction seabed = LayoutAccess.seabed(context.randomState());
        return seabed == null || WorldAccess.seabedHeight(seabed, pos.getX(), pos.getZ()) - pos.getY() >= min;
    }

    @Override
    public MapCodec<? extends PlacementCondition> codec() {
        return CODEC;
    }
}

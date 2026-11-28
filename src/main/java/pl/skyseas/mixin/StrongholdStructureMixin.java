package pl.skyseas.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pl.skyseas.structure.BelowSeabed;

/**
 * Why a mixin: the stronghold height is hard-coded (top 10+ blocks under the sea level), no
 * data or Lithostitched hook can move it, and a spawn condition could only drop strongholds,
 * which eyes of ender still point to. Approved by the user for E6 (2026-11-28).
 */
@Mixin(StrongholdStructure.class)
abstract class StrongholdStructureMixin {
    @WrapOperation(method = "generatePieces", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/pieces/StructurePiecesBuilder;moveBelowSeaLevel(IILnet/minecraft/util/RandomSource;I)I"))
    private static int skyseas$belowSeabed(StructurePiecesBuilder builder, int seaLevel, int minY, RandomSource random, int amount,
                                           Operation<Integer> original, StructurePiecesBuilder b, Structure.GenerationContext context) {
        int maxTop = BelowSeabed.maxTop(context.randomState(), builder.getBoundingBox(), builder.build().pieces());
        // Too tall for the room under the seabed: it stays as low as vanilla allows (it must exist).
        return original.call(builder, BelowSeabed.seaLevel(maxTop, seaLevel, amount), minY, random, amount);
    }
}

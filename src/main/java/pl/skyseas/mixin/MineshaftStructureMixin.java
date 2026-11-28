package pl.skyseas.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pl.skyseas.density.LayoutAccess;
import pl.skyseas.structure.BelowSeabed;

/**
 * Why a mixin: mineshaft heights are hard-coded (normal: top 10+ blocks under the sea level;
 * badlands: between the sea level and the surface, which here is mid-air over the ocean).
 * Both go under the seabed with a roof; a mineshaft with no room there is dropped. Approved by the user for E6 (2026-11-28).
 */
@Mixin(MineshaftStructure.class)
abstract class MineshaftStructureMixin {
    /** In Sky Seas the badlands branch is never taken (the type check compares with null). */
    @ModifyExpressionValue(method = "generatePiecesAndAdjust", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/level/levelgen/structure/structures/MineshaftStructure$Type;MESA:Lnet/minecraft/world/level/levelgen/structure/structures/MineshaftStructure$Type;"))
    private MineshaftStructure.Type skyseas$noSurfaceMineshafts(MineshaftStructure.Type mesa, StructurePiecesBuilder builder,
                                                                Structure.GenerationContext context) {
        return LayoutAccess.seabed(context.randomState()) == null ? mesa : null;
    }

    @WrapOperation(method = "generatePiecesAndAdjust", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/pieces/StructurePiecesBuilder;moveBelowSeaLevel(IILnet/minecraft/util/RandomSource;I)I"))
    private int skyseas$belowSeabed(StructurePiecesBuilder builder, int seaLevel, int minY, RandomSource random, int amount,
                                    Operation<Integer> original, StructurePiecesBuilder b, Structure.GenerationContext context) {
        int maxTop = BelowSeabed.maxTop(context.randomState(), builder.getBoundingBox(), builder.build().pieces());
        int offset = original.call(builder, BelowSeabed.seaLevel(maxTop, seaLevel, amount), minY, random, amount);
        if (builder.getBoundingBox().maxY() > maxTop) {
            // No room under the seabed (trenches, low seabed): no mineshaft here.
            builder.clear();
        }
        return offset;
    }
}

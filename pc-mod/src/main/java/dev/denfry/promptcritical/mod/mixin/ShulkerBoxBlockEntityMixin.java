package dev.denfry.promptcritical.mod.mixin;

import dev.denfry.promptcritical.mod.environment.NestingRule;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Воронки и трубы тоже не загружают источник в шалкер (04d §4d.6.2). */
@Mixin(ShulkerBoxBlockEntity.class)
abstract class ShulkerBoxBlockEntityMixin {
    @Inject(method = "canPlaceItemThroughFace", at = @At("HEAD"), cancellable = true)
    private void promptcritical$noSources(int index, ItemStack stack, Direction side, CallbackInfoReturnable<Boolean> cir) {
        if (!NestingRule.mayNest(stack)) cir.setReturnValue(false);
    }
}

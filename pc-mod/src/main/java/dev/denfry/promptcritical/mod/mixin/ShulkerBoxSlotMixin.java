package dev.denfry.promptcritical.mod.mixin;

import dev.denfry.promptcritical.mod.environment.NestingRule;
import net.minecraft.world.inventory.ShulkerBoxSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Источник выше уровня освобождения не кладётся в слот шалкера (04d §4d.6.2). */
@Mixin(ShulkerBoxSlot.class)
abstract class ShulkerBoxSlotMixin {
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void promptcritical$noSources(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!NestingRule.mayNest(stack)) cir.setReturnValue(false);
    }
}

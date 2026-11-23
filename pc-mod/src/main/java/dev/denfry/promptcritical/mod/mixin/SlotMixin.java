package dev.denfry.promptcritical.mod.mixin;

import dev.denfry.promptcritical.mod.environment.NestingRule;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Сундук Края — общий для всех сундуков игрока карман вне мира: источник в нём пропал бы из расчёта излучения.
 * Поэтому в слоты {@link PlayerEnderChestContainer} источник выше уровня освобождения не кладётся (04d §4d.6.2).
 */
@Mixin(Slot.class)
abstract class SlotMixin {
    @Shadow
    @Final
    public Container container;

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void promptcritical$noSourcesInEnderChest(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (container instanceof PlayerEnderChestContainer && !NestingRule.mayNest(stack)) cir.setReturnValue(false);
    }
}

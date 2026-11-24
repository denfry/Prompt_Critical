package dev.denfry.promptcritical.mod.mixin;

import dev.denfry.promptcritical.mod.api.ApiDevices;
import java.util.Collection;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Устройства для API аддонов (05 §5.8.1): все блок-сущности, входящие в уровень при загрузке чанка и при установке
 * блока, проходят через этот метод NeoForge.
 */
@Mixin(Level.class)
abstract class LevelFreshBlockEntitiesMixin {
    @Inject(method = "addFreshBlockEntities", at = @At("HEAD"))
    private void promptcritical$trackDevices(Collection<BlockEntity> beList, CallbackInfo ci) {
        ApiDevices.added((Level) (Object) this, beList);
    }
}

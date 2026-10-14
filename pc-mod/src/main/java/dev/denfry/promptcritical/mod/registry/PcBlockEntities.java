package dev.denfry.promptcritical.mod.registry;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Типы BE. {@code device} — общий BE устройств до появления специализированных (этапы 6–8). */
public final class PcBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DeviceBlockEntity>> DEVICE =
            BLOCK_ENTITIES.register("device", () -> BlockEntityType.Builder.of(DeviceBlockEntity::new,
                    PcBlocks.devices().stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null));

    private PcBlockEntities() {
    }
}

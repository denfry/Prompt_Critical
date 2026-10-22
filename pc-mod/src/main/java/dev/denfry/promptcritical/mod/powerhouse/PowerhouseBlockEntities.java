package dev.denfry.promptcritical.mod.powerhouse;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Типы BE котла I1 и деаэратора (07 §7.9.5, 08 §8.12). */
public final class PowerhouseBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WaterTubeBoilerBlockEntity>> WATER_TUBE_BOILER =
            type("water_tube_boiler", WaterTubeBoilerBlockEntity::new, PcBlocks.WATER_TUBE_BOILER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DraftFanBlockEntity>> FORCED_DRAFT_FAN =
            type("forced_draft_fan", DraftFanBlockEntity::forced, PcBlocks.FORCED_DRAFT_FAN);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DraftFanBlockEntity>> INDUCED_DRAFT_FAN =
            type("induced_draft_fan", DraftFanBlockEntity::induced, PcBlocks.INDUCED_DRAFT_FAN);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DeaeratorBlockEntity>> DEAERATOR =
            type("deaerator", DeaeratorBlockEntity::new, PcBlocks.DEAERATOR);

    private PowerhouseBlockEntities() {
    }

    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(String name,
            BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> block) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }
}

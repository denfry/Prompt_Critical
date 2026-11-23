package dev.denfry.promptcritical.mod.boiler;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Типы BE котельной I0 (07 §7.9). */
public final class BoilerBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FireTubeBoilerBlockEntity>> FIRE_TUBE_BOILER =
            BLOCK_ENTITIES.register("fire_tube_boiler", () -> BlockEntityType.Builder.of(FireTubeBoilerBlockEntity::new,
                    PcBlocks.FIRE_TUBE_BOILER.get(), PcBlocks.LANCASHIRE_BOILER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteamEngineBlockEntity>> STEAM_ENGINE =
            BLOCK_ENTITIES.register("steam_engine_cylinder", () -> BlockEntityType.Builder.of(SteamEngineBlockEntity::new,
                    PcBlocks.STEAM_ENGINE_CYLINDER.get(), PcBlocks.MILL_ENGINE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GrateStokerBlockEntity>> GRATE_STOKER =
            type("grate_stoker", GrateStokerBlockEntity::new, PcBlocks.GRATE_STOKER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LancashireStokerBlockEntity>> LANCASHIRE_STOKER =
            type("lancashire_stoker", LancashireStokerBlockEntity::new, PcBlocks.LANCASHIRE_STOKER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<KineticPortBlockEntity>> KINETIC_PORT =
            type("kinetic_port", KineticPortBlockEntity::new, PcBlocks.KINETIC_PORT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<KineticInputBlockEntity>> KINETIC_INPUT =
            type("kinetic_input", KineticInputBlockEntity::new, PcBlocks.KINETIC_PORT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PressureGaugeBlockEntity>> PRESSURE_GAUGE =
            type("instrument_pressure", PressureGaugeBlockEntity::new, PcBlocks.INSTRUMENT_PRESSURE);

    private BoilerBlockEntities() {
    }

    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type(String name,
            BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> block) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }
}

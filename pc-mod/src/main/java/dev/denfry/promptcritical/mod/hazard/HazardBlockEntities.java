package dev.denfry.promptcritical.mod.hazard;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Типы BE и подключение аварий нефтегаза, разливов, трубопроводов и резервуарного парка (09 §9.8, §9.11, 14 §14.5). */
public final class HazardBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GasStorageBulletBlockEntity>> GAS_STORAGE_BULLET =
            BLOCK_ENTITIES.register("gas_storage_bullet",
                    () -> BlockEntityType.Builder.of(GasStorageBulletBlockEntity::new, PcBlocks.GAS_STORAGE_BULLET.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OilSkimmerBlockEntity>> OIL_SKIMMER =
            BLOCK_ENTITIES.register("oil_skimmer",
                    () -> BlockEntityType.Builder.of(OilSkimmerBlockEntity::new, PcBlocks.OIL_SKIMMER.get()).build(null));

    // трубопроводы и резервуарный парк 09 §9.8
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TankGaugeBlockEntity>> TANK_GAUGE =
            BLOCK_ENTITIES.register("tank_gauge",
                    () -> BlockEntityType.Builder.of(TankGaugeBlockEntity::new, PcBlocks.TANK_GAUGE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PigTrapBlockEntity>> PIG_TRAP =
            BLOCK_ENTITIES.register("pig_trap",
                    () -> BlockEntityType.Builder.of(PigTrapBlockEntity::new, PcBlocks.PIG_LAUNCHER.get(), PcBlocks.PIG_RECEIVER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CpRectifierBlockEntity>> CP_RECTIFIER =
            BLOCK_ENTITIES.register("cp_rectifier",
                    () -> BlockEntityType.Builder.of(CpRectifierBlockEntity::new, PcBlocks.CP_RECTIFIER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CpTestPostBlockEntity>> CP_TEST_POST =
            BLOCK_ENTITIES.register("cp_test_post",
                    () -> BlockEntityType.Builder.of(CpTestPostBlockEntity::new, PcBlocks.CP_TEST_POST.get()).build(null));

    private HazardBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(HazardBlockEntities::capabilities);
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(() ->
                // пролитая нефть горит и выгорает, как горючий блок (пожар пролива 09 §9.11.4)
                ((FireBlock) Blocks.FIRE).setFlammable(PcBlocks.CRUDE_OIL.get(), 60, 20)));
        HazardService.register();
        OilSpills.register();
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, GAS_STORAGE_BULLET.get(), (be, side) -> be.fluids(side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, OIL_SKIMMER.get(), (be, side) -> be.fluids(side));
    }
}

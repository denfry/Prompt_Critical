package dev.denfry.promptcritical.mod.hydrogen;

import dev.denfry.promptcritical.mod.registry.PcBlocks;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Привязка водородной станции: реестр BE и баки за жидкостными портами (вода подпитки и охлаждения). Трубы Create
 * цепляются только к клетке и грани порта ({@link dev.denfry.promptcritical.mod.multiblock.WholePorts}); газовые порты
 * баков не дают — газ ходит по линиям ({@link GasLines}).
 */
public final class HydrogenBinding {
    private HydrogenBinding() {
    }

    public static void register(IEventBus modBus) {
        HydrogenBlockEntities.BLOCK_ENTITIES.register(modBus);
        modBus.addListener(HydrogenBinding::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        Block[] blocks = {PcBlocks.H2_ELECTROLYSER_REAL.get(), PcBlocks.H2_COMPRESSOR_REAL.get(), PcBlocks.H2_TUBE_BANK_REAL.get(),
                PcBlocks.H2_GASHOLDER_REAL.get(), PcBlocks.H2_FUEL_CELL_REAL.get(), PcBlocks.MULTIBLOCK_PART.get()};
        event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, be, side) -> {
            if (side == null) return null;
            H2Machine m = GasLines.machineAt(level, pos);
            if (m == null) return null;
            int port = m.portAt(pos, side);
            return port < 0 ? null : m.portFluids(port);
        }, blocks);
    }
}

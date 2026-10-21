package dev.denfry.promptcritical.mod.automation;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.boiler.BoilerGeometry;
import dev.denfry.promptcritical.mod.boiler.FireTubeBoilerBlockEntity;
import dev.denfry.promptcritical.mod.boiler.PressureSource;
import dev.denfry.promptcritical.mod.mine.MineDrainagePumpBlockEntity;
import dev.denfry.promptcritical.sim.automation.ProcessSwitchKind;
import dev.denfry.promptcritical.sim.mining.MineGas;
import dev.denfry.promptcritical.sim.mining.MineNetwork;
import dev.denfry.promptcritical.sim.plant.BoilerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Клемма процесса реле (03d §3d.3.6) и чувствительный элемент газоанализатора (03d §3d.11): величина объекта, на
 * котором висит прибор. Давление — паровое пространство котла или золотник машины; уровень — водомерное стекло котла
 * или бак Create (доля × высота); температура — вода котла, иначе воздух по биому; расход — водоотливной насос или
 * механический насос Create (≈ 0,01 кг/с на об/мин [моё]).
 */
public final class ProcessSources {
    /** НКПР метана, объёмная доля. */
    public static final double CH4_LEL = 0.05;
    /** Ниже этой доли кислорода термокаталитический датчик занижает показание (нет окисления). */
    public static final double CATALYTIC_O2_MIN = 0.10;

    private ProcessSources() {
    }

    /** Величина процесса в единицах {@link ProcessSwitchKind}: Па, м, K, кг/с. */
    public static double read(ServerLevel level, BlockPos mount, ProcessSwitchKind kind) {
        if (!level.isLoaded(mount)) return 0;
        BlockPos boiler = BoilerGeometry.controllerOf(level, mount);
        BlockEntity be = boiler != null ? level.getBlockEntity(boiler) : level.getBlockEntity(mount);
        return switch (kind) {
            case PRESSURE -> be instanceof PressureSource p ? Math.max(0, p.gaugePressurePa()) : 0;
            case LEVEL -> {
                if (be instanceof FireTubeBoilerBlockEntity b && b.unit() != null) yield b.unit().gaugeGlassLevelM();
                if (be instanceof FluidTankBlockEntity t && t.getControllerBE() != null) {
                    FluidTankBlockEntity c = t.getControllerBE();
                    double cap = c.getTankInventory().getCapacity();
                    yield cap <= 0 ? 0 : c.getTankInventory().getFluidAmount() / cap * c.getHeight();
                }
                yield 0;
            }
            case TEMPERATURE -> {
                if (be instanceof FireTubeBoilerBlockEntity b) {
                    BoilerUnit u = b.unit();
                    if (u != null) yield u.waterTempK();
                }
                yield 273.15 + 25 * level.getBiome(mount).value().getBaseTemperature();
            }
            case FLOW -> {
                if (be instanceof MineDrainagePumpBlockEntity p) {
                    SimLevelBinding b = SimBindings.get(level);
                    yield b == null ? 0 : 1000 * p.capacityM3s(b.world().clock().tick());
                }
                if (be instanceof KineticBlockEntity k && AllBlocks.MECHANICAL_PUMP.has(level.getBlockState(mount)))
                    yield 0.01 * Math.abs(k.getSpeed());
                yield 0;
            }
        };
    }

    /** Метан у потолка выработки, % НКПР; без кислорода термокаталитический датчик врёт в меньшую сторону. */
    public static double methaneLel(ServerLevel level, BlockPos pos) {
        SimLevelBinding b = SimBindings.get(level);
        MineNetwork n = b == null ? null : b.mines().mineAt(pos);
        int node = n == null ? -1 : n.nodeOf(pos.asLong());
        if (node <= 0) return 0;
        double ch4 = n.field().roofMethane(node), o2 = n.field().concentration(node, MineGas.O2);
        return 100 * ch4 / CH4_LEL * Math.min(1, o2 / CATALYTIC_O2_MIN);
    }
}

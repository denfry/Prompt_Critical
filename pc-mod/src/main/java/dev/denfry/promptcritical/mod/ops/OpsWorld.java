package dev.denfry.promptcritical.mod.ops;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.boiler.BoilerGeometry;
import dev.denfry.promptcritical.mod.grid.GridMountedBlock;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.ops.MaintenanceFaultKind;
import dev.denfry.promptcritical.sim.ops.OpsSubsystem;
import dev.denfry.promptcritical.sim.ops.UnitComponent;
import dev.denfry.promptcritical.sim.ops.UnitKind;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Эксплуатация со стороны мира: подсистема измерения, машина по клетке, установка по машине. */
public final class OpsWorld {
    private OpsWorld() {
    }

    @Nullable
    public static OpsSubsystem ops(@Nullable Level level) {
        SimLevelBinding b = level instanceof ServerLevel s ? SimBindings.get(s) : null;
        return b == null ? null : b.world().subsystem(OpsSubsystem.class);
    }

    /**
     * Установка машины: при первом обращении — сборка (износ новой машины, дефекты монтажа). Сервер; до привязки к
     * домену — null.
     */
    @Nullable
    public static OpsSubsystem.Unit ensure(Level level, SimId id, UnitKind kind, double workingPressurePa,
            boolean dashpotFitted) {
        OpsSubsystem ops = ops(level);
        if (ops == null || id.isNone()) return null;
        return ops.ensure(id, kind, workingPressurePa, false, Set.<UnitComponent>of(), dashpotFitted);
    }

    @Nullable
    public static OpsSubsystem.Unit unit(@Nullable Level level, SimId id) {
        OpsSubsystem ops = ops(level);
        return ops == null || id.isNone() ? null : ops.unit(id);
    }

    /**
     * Машина по клетке: сама машина, её клетка, щит обслуживания или самописец на ней; иначе null. Работает и на
     * клиенте (BE машины там есть).
     */
    @Nullable
    public static OpsMachine machine(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.is(PcBlocks.MAINTENANCE_PANEL.get()) || s.is(PcBlocks.RECORDING_GAUGE.get())
                || s.is(PcBlocks.INSTRUMENT_PRESSURE.get())) {
            BlockPos mount = s.is(PcBlocks.INSTRUMENT_PRESSURE.get())
                    ? pos.relative(s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING).getOpposite())
                    : GridMountedBlock.mountPos(s, pos);
            return direct(level, mount);
        }
        return direct(level, pos);
    }

    @Nullable
    private static OpsMachine direct(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof OpsMachine m) return m;
        BlockPos c = BoilerGeometry.controllerOf(level, pos);
        return c != null && level.getBlockEntity(c) instanceof OpsMachine m ? m : null;
    }

    /** Строка панели по виду неисправности; −1 — нет. */
    public static int index(OpsSubsystem.Unit u, MaintenanceFaultKind kind) {
        for (int i = 0; i < u.panel().size(); i++) if (u.panel().source(i).kind() == kind) return i;
        return -1;
    }
}

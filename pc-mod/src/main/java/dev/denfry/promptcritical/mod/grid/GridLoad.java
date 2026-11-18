package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.grid.ConsumerUnit;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Сетевой ввод машины, у которой BE не {@link GridDeviceBlockEntity} (участок цеха, выпарной аппарат, ванна): BE
 * реализует {@link GridDevice}, регистрируется здесь при загрузке, а мощность берёт двойником-потребителем
 * {@link ConsumerUnit} 6,3 кВ с мощностью, которую задаёт сама машина. Стендовое питание (GameTest) — флаг машины.
 */
public final class GridLoad {
    private GridLoad() {
    }

    public static void register(@Nullable Level level, BlockPos pos) {
        if (level instanceof ServerLevel s) {
            SimLevelBinding b = SimBindings.get(s);
            if (b != null) b.grids().register(pos);
        }
    }

    public static void unregister(@Nullable Level level, BlockPos pos) {
        if (level instanceof ServerLevel s) {
            SimLevelBinding b = SimBindings.get(s);
            if (b != null) b.grids().unregister(pos);
        }
    }

    @Nullable
    public static ConsumerUnit unit(@Nullable Level level, SimId id) {
        SimLevelBinding b = level instanceof ServerLevel s ? SimBindings.get(s) : null;
        if (b == null || id.isNone() || !b.world().topology().exists(id)) return null;
        GridSubsystem g = b.world().subsystem(GridSubsystem.class);
        return g == null ? null : g.consumer(id);
    }

    /**
     * Машине нужна мощность {@code demandW}: задать её вводу и узнать, есть ли напряжение.
     *
     * @return ввод под напряжением
     */
    public static boolean draw(@Nullable Level level, SimId id, double demandW) {
        ConsumerUnit u = unit(level, id);
        if (u == null) return false;
        u.setDemandW(demandW);
        return u.energized();
    }
}

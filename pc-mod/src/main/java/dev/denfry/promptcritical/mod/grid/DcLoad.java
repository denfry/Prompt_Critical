package dev.denfry.promptcritical.mod.grid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Потребитель постоянного тока от выпрямительного агрегата: серия электролизёров (хлорных, фторных). Выпрямитель
 * спрашивает мощность при заданном токе и каждый тик сообщает ток, который смог дать.
 */
public interface DcLoad {
    /** Заданный ток серии, А. */
    double dcSetpointA();

    /** Мощность постоянного тока при заданном токе, Вт; 0 — серия отключена. */
    double dcDemandW();

    /** Ток от выпрямителя на этот тик, А. */
    void supply(double amps);

    /** Ближайшая серия в кубе с ребром 2·reach+1 вокруг {@code pos}. */
    @Nullable
    static DcLoad near(Level level, BlockPos pos, int reach) {
        DcLoad best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-reach, -reach, -reach), pos.offset(reach, reach, reach)))
            if (level.getBlockEntity(p) instanceof DcLoad d && p.distSqr(pos) < bd) {
                best = d;
                bd = p.distSqr(pos);
            }
        return best;
    }
}

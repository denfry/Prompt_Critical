package dev.denfry.promptcritical.mod.reactor;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.reactor.PileUnit;
import dev.denfry.promptcritical.sim.reactor.ReactorSubsystem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Общие помощники реакторов в мире. */
public final class Reactor {
    /** Кнопка АЗ в зале находит пульт не дальше этого, блоков. */
    public static final int SCRAM_REACH = 24;

    private Reactor() {
    }

    @Nullable
    public static PileUnit unit(@Nullable SimLevelBinding b, SimId id) {
        if (b == null || id.isNone()) return null;
        return b.world().subsystem(ReactorSubsystem.class).unit(b.world().topology(), id);
    }

    /** Ближайший пульт котла в радиусе {@code reach} (по блок-сущностям загруженных чанков) или {@code null}. */
    @Nullable
    public static PileControllerBlockEntity nearestController(Level level, BlockPos from, int reach) {
        PileControllerBlockEntity best = null;
        double bestD = (double) reach * reach;
        int c0x = (from.getX() - reach) >> 4, c1x = (from.getX() + reach) >> 4;
        int c0z = (from.getZ() - reach) >> 4, c1z = (from.getZ() + reach) >> 4;
        for (int cx = c0x; cx <= c1x; cx++) {
            for (int cz = c0z; cz <= c1z; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                for (var be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (!(be instanceof PileControllerBlockEntity c) || be.isRemoved()) continue;
                    double d = be.getBlockPos().distSqr(from);
                    if (d <= bestD) {
                        bestD = d;
                        best = c;
                    }
                }
            }
        }
        return best;
    }
}

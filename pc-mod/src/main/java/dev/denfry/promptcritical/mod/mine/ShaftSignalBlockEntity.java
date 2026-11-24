package dev.denfry.promptcritical.mod.mine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/**
 * Стволовой сигнальный аппарат посадки (08 §8.2.8): ставится у клети на посадке и связывает её с подъёмной машиной
 * без проводов к машинному залу. Выход — сильный сигнал, пока клеть стоит у этой посадки; компаратор — 0 без клети,
 * 1 у пустой клети и до 15 при полной загрузке. Вход — фронт редстоуна вызывает клеть к этой посадке через
 * {@link MineHoistBlockEntity#call}, со всеми блокировками машины.
 *
 * <p>Машину аппарат ищет сам: подъём, у которого ось клети не дальше {@value #AXIS_REACH} клеток по горизонтали и
 * посадка на уровне аппарата (пол клети на высоте аппарата или до двух клеток ниже — аппарат висит на стенке
 * клети). Поиск идёт по загруженным чанкам в радиусе {@value #SEARCH_CHUNKS} чанков, только пока связи нет.
 */
public class ShaftSignalBlockEntity extends BlockEntity {
    /** Звонки и табло стволовой сигнализации, Вт. Малая нагрузка 0,4 кВ: розетка, щит или ИБП рядом. */
    public static final double POWER_W = 100;
    static final int AXIS_REACH = 3;
    static final int SEARCH_CHUNKS = 4;
    private static final int PERIOD = 4, RESCAN = 100;

    @Nullable private BlockPos hoistPos;
    private int landing = -1, comparator, nextScan;
    private boolean input;

    public ShaftSignalBlockEntity(BlockPos pos, BlockState state) {
        super(MineBlockEntities.SHAFT_SIGNAL.get(), pos, state);
    }

    public int landing() {
        return landing;
    }

    public int comparator() {
        return comparator;
    }

    @Nullable
    public MineHoistBlockEntity hoist() {
        if (hoistPos == null || level == null) return null;
        if (level.getBlockEntity(hoistPos) instanceof MineHoistBlockEntity h && h.landingDepths().length > 0) return h;
        hoistPos = null;
        landing = -1;
        return null;
    }

    static void tick(ShaftSignalBlockEntity be) {
        if (!(be.level instanceof ServerLevel sl) || sl.getGameTime() % PERIOD != 0 || !dev.denfry.promptcritical.mod.grid.MachinePower.powered(sl, be.worldPosition, POWER_W)) return;
        MineHoistBlockEntity h = be.hoist();
        if (h == null && --be.nextScan <= 0) {
            be.nextScan = RESCAN / PERIOD;
            be.link(sl);
            h = be.hoist();
        }
        boolean here = h != null && be.landing >= 0 && !h.isMoving() && h.landingAtCage() == be.landing;
        int cmp = here ? 1 + (int) Math.round(Math.max(0, Math.min(1, h.cageLoadFraction())) * 14) : 0;
        BlockState state = be.getBlockState();
        boolean changed = cmp != be.comparator;
        be.comparator = cmp;
        if (state.getValue(BlockStateProperties.POWERED) != here) {
            sl.setBlock(be.worldPosition, state.setValue(BlockStateProperties.POWERED, here), Block.UPDATE_ALL);
            changed = true;
        }
        if (changed) {
            be.setChanged();
            sl.updateNeighbourForOutputSignal(be.worldPosition, state.getBlock());
            sl.updateNeighborsAt(be.worldPosition, state.getBlock());
        }
    }

    /** Вход: передний фронт сигнала вызывает клеть к этой посадке. */
    void onInput(boolean powered) {
        boolean rising = powered && !input;
        input = powered;
        if (!rising) return;
        MineHoistBlockEntity h = hoist();
        if (h != null && landing >= 0) h.call(landing, null);
    }

    private void link(ServerLevel sl) {
        int cx = worldPosition.getX() >> 4, cz = worldPosition.getZ() >> 4;
        for (int dx = -SEARCH_CHUNKS; dx <= SEARCH_CHUNKS; dx++) {
            for (int dz = -SEARCH_CHUNKS; dz <= SEARCH_CHUNKS; dz++) {
                LevelChunk chunk = sl.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null) continue;
                for (BlockEntity e : chunk.getBlockEntities().values()) {
                    if (!(e instanceof MineHoistBlockEntity h)) continue;
                    int l = landingFor(h);
                    if (l >= 0) {
                        hoistPos = h.getBlockPos();
                        landing = l;
                        return;
                    }
                }
            }
        }
    }

    private int landingFor(MineHoistBlockEntity h) {
        HoistGeometry.Shaft s = h.shaft();
        if (s == null) return -1;
        if (Math.abs(s.cageX() - worldPosition.getX()) > AXIS_REACH || Math.abs(s.cageZ() - worldPosition.getZ()) > AXIS_REACH) return -1;
        for (int dy = 0; dy <= 2; dy++) {
            int l = h.landingAt(worldPosition.getY() - dy);
            if (l >= 0) return l;
        }
        return -1;
    }
}

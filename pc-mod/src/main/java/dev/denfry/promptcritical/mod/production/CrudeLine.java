package dev.denfry.promptcritical.mod.production;

import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.environment.EnvironmentService;
import dev.denfry.promptcritical.mod.hazard.PigTrapBlockEntity;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Нефтяная линия на трубах Create между машинами транспорта нефти (09 §9.8): трубы Create не несут температуру, поэтому
 * машина, которой она нужна (насос — для вязкости, резервуар — для смешения), идёт от своего патрубка по трубам,
 * насосам и арматуре Create назад до первой машины, отдающей нефть ({@link Source}), и берёт её температуру. Без
 * источника нефть приходит с температурой воздуха (выкидные линии скважин лежат на земле).
 */
public final class CrudeLine {
    /** Наибольшее число клеток трубы в обходе, как у камеры СОД. */
    public static final int TRACE_LIMIT = 256;

    /** Машина, отдающая нефть в трубу: температура нефти у её выходного патрубка, °C. */
    public interface Source {
        double crudeC();
    }

    private CrudeLine() {
    }

    /** Температура воздуха у клетки, °C. */
    public static double ambientC(ServerLevel level, BlockPos pos) {
        return EnvironmentService.climateK(level, pos) - 273.15;
    }

    /**
     * Клетка порта в мире: клетка {@code cell} модели (фасад на север, от контроллера {@code controller}) при фасаде
     * {@code facing}.
     */
    public static BlockPos portCell(BlockPos controller, Direction facing, BlockPos cell) {
        return controller.offset(cell.rotate(Footprint.rotation(facing)));
    }

    /** Мировая грань для грани {@code modelFace} модели при фасаде {@code facing}. */
    public static Direction worldFace(Direction modelFace, Direction facing) {
        return modelFace.getAxis().isVertical() ? modelFace : Footprint.rotation(facing).rotate(modelFace);
    }

    /**
     * Источник нефти выше по линии от патрубка {@code port} с гранью {@code face} (мировые), исключая машину
     * {@code self}; {@code null} — линии нет или на её конце не машина транспорта нефти.
     */
    @Nullable
    public static Source upstream(ServerLevel level, BlockPos port, Direction face, Object self) {
        BlockPos first = port.relative(face);
        Source direct = source(level, first, self);
        if (direct != null) return direct;
        if (!level.isLoaded(first) || !PigTrapBlockEntity.pipe(level.getBlockState(first))) return null;
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        q.add(first);
        seen.add(first);
        seen.add(port);
        while (!q.isEmpty() && seen.size() < TRACE_LIMIT) {
            BlockPos p = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!seen.add(n) || !level.isLoaded(n)) continue;
                Source s = source(level, n, self);
                if (s != null) return s;
                if (PigTrapBlockEntity.pipe(level.getBlockState(n))) q.add(n);
            }
        }
        return null;
    }

    /** Машина транспорта нефти, которой принадлежит клетка {@code pos} (контроллер или клетка объёма). */
    @Nullable
    private static Source source(ServerLevel level, BlockPos pos, Object self) {
        BlockState s = level.getBlockState(pos);
        BlockPos c = pos;
        if (s.getBlock() instanceof MultiblockPartBlock) c = PcBlock.structureController(level, MultiblockPartBlock.anchor(s, pos));
        else if (s.getBlock() instanceof PcBlock) {
            BlockPos k = PcBlock.structureController(level, pos);
            if (k != null) c = k;
        }
        if (c == null) return null;
        return level.getBlockEntity(c) instanceof Source src && src != self ? src : null;
    }
}

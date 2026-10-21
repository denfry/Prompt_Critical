package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.GridUnit;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * BE устройства сети (07 §7.1.2): выводы — грани клеток, которыми устройство касается кабелей, шин и выводов соседних
 * устройств. {@link GridWorld} собирает из них узлы и передаёт двойнику ({@link GridUnit#setNodes}).
 */
public interface GridDevice {
    SimId simId();

    /** Выводы в мировых координатах: у каждого — грани клеток наружу. */
    List<List<Face>> terminals();

    /** Двойник устройства (создаётся при первом обращении); {@code null} — устройства ещё нет в топологии. */
    @Nullable
    GridUnit gridUnit(GridSubsystem grid);

    /** Грань клетки {@code cell} в сторону {@code dir}. */
    record Face(BlockPos cell, Direction dir) {
        /** Ключ грани, общий для двух клеток по обе её стороны. */
        public long key() {
            BlockPos base = dir.getAxisDirection() == Direction.AxisDirection.POSITIVE ? cell : cell.relative(dir);
            return GridWorld.mix(base.asLong() * 3 + dir.getAxis().ordinal());
        }
    }

    /**
     * Грани выводов по модели (фасад на север, «лево» — −x): клетка {@code (ox, oy, oz)} от контроллера, направления
     * модели. Поворачиваются к фасаду {@code facing}.
     */
    static void faces(List<Face> out, BlockPos controller, Direction facing, int ox, int oy, int oz, Direction... modelDirs) {
        BlockPos cell = controller.offset(new BlockPos(ox, oy, oz).rotate(Footprint.rotation(facing)));
        for (Direction d : modelDirs) {
            Direction w = d.getAxis().isVertical() ? d : Footprint.rotation(facing).rotate(d);
            out.add(new Face(cell, w));
        }
    }

    /** Все шесть граней одной клетки. */
    static List<Face> allFaces(BlockPos cell) {
        List<Face> out = new ArrayList<>(6);
        for (Direction d : Direction.values()) out.add(new Face(cell, d));
        return out;
    }
}

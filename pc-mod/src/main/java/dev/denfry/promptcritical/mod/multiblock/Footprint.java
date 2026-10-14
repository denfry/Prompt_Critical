package dev.denfry.promptcritical.mod.multiblock;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;

/**
 * Занимаемый блоком объём (05 §5.3.3, арт 00 §0.12.2): один или несколько сегментов до 3×3×3, каждый со своим
 * блоком-якорем, на котором рисуется модель сегмента. Координаты — в пространстве модели (фасад на север); по
 * оси размера 3 якорь — средний блок (−1…1), размера 2 — край (0…1 или −1…0, как в модели). Сегмент 0 —
 * контроллер: на нём {@code BlockEntity} и с него выпадает предмет.
 */
public record Footprint(List<Segment> segments) {
    public static final Footprint SINGLE = new Footprint(List.of(Segment.box(0, 0, 0, 0, 0, 0)));

    /**
     * Сегмент: смещение якоря от якоря контроллера и диапазоны клеток вокруг своего якоря.
     */
    public record Segment(int ax, int ay, int az, int x0, int x1, int y0, int y1, int z0, int z1) {
        public Segment {
            if (x0 < -1 || x1 > 1 || y0 < -1 || y1 > 1 || z0 < -1 || z1 > 1 || x0 > x1 || y0 > y1 || z0 > z1)
                throw new IllegalArgumentException("segment cells must lie in −1…1 around the anchor");
        }

        public static Segment box(int x0, int x1, int y0, int y1, int z0, int z1) {
            return new Segment(0, 0, 0, x0, x1, y0, y1, z0, z1);
        }

        public Segment at(int ax, int ay, int az) {
            return new Segment(ax, ay, az, x0, x1, y0, y1, z0, z1);
        }
    }

    public static Footprint of(Segment... segments) {
        return new Footprint(List.of(segments));
    }

    public boolean isSingle() {
        return segments.size() == 1 && segments.getFirst().equals(SINGLE.segments.getFirst());
    }

    /** Наименьшее смещение по y среди клеток: на столько поднимается якорь при установке с земли. */
    public int minY() {
        int m = 0;
        for (Segment s : segments) m = Math.min(m, s.ay + s.y0);
        return m;
    }

    public static Rotation rotation(Direction facing) {
        return switch (facing) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /** Мировая позиция якоря сегмента {@code index} при контроллере в {@code controller}. */
    public BlockPos anchor(BlockPos controller, Direction facing, int index) {
        Segment s = segments.get(index);
        return controller.offset(new BlockPos(s.ax, s.ay, s.az).rotate(rotation(facing)));
    }

    /** Позиция контроллера по якорю сегмента {@code index}. */
    public BlockPos controllerFrom(BlockPos anchor, Direction facing, int index) {
        Segment s = segments.get(index);
        return anchor.subtract(new BlockPos(s.ax, s.ay, s.az).rotate(rotation(facing)));
    }

    /** Клетка сегмента: мировая позиция и смещение от якоря сегмента (уже повёрнутое). */
    public record Cell(int segment, BlockPos pos, BlockPos offsetFromAnchor) {
        public boolean isAnchor() {
            return offsetFromAnchor.equals(BlockPos.ZERO);
        }
    }

    public List<Cell> cells(BlockPos controller, Direction facing) {
        Rotation rot = rotation(facing);
        List<Cell> out = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            Segment s = segments.get(i);
            BlockPos anchor = anchor(controller, facing, i);
            for (int y = s.y0; y <= s.y1; y++)
                for (int z = s.z0; z <= s.z1; z++)
                    for (int x = s.x0; x <= s.x1; x++) {
                        BlockPos off = new BlockPos(x, y, z).rotate(rot);
                        out.add(new Cell(i, anchor.offset(off), off));
                    }
        }
        return out;
    }
}

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

    /**
     * Прямоугольный объём машины в реальном масштабе (спецификация real-scale machines, §1), клетки от контроллера
     * {@code x0…x1, y0…y1, z0…z1} (фасад на север), разложенный на сегменты 3×3×3 по сетке с якорями в кратных 3:
     * сегмент 0 — с якорем в контроллере. Объём должен содержать клетку контроллера (0, 0, 0).
     */
    public static Footprint tiled(int x0, int x1, int y0, int y1, int z0, int z1) {
        if (x0 > 0 || x1 < 0 || y0 > 0 || y1 < 0 || z0 > 0 || z1 < 0) throw new IllegalArgumentException("volume must contain the controller");
        List<Segment> out = new ArrayList<>();
        for (int ky = Math.floorDiv(y0 + 1, 3); ky <= Math.floorDiv(y1 + 1, 3); ky++)
            for (int kz = Math.floorDiv(z0 + 1, 3); kz <= Math.floorDiv(z1 + 1, 3); kz++)
                for (int kx = Math.floorDiv(x0 + 1, 3); kx <= Math.floorDiv(x1 + 1, 3); kx++) {
                    // ячейка сетки 3×3×3 вокруг 3k, обрезанная объёмом; якорь — внутри обрезка, иначе на краю объёма
                    // клетки ссылались бы на якорь, которого нет (форма, поломка и поиск контроллера терялись)
                    int lx = Math.max(x0, 3 * kx - 1), hx = Math.min(x1, 3 * kx + 1), ly = Math.max(y0, 3 * ky - 1),
                            hy = Math.min(y1, 3 * ky + 1), lz = Math.max(z0, 3 * kz - 1), hz = Math.min(z1, 3 * kz + 1);
                    int ax = Math.clamp(3 * kx, lx, hx), ay = Math.clamp(3 * ky, ly, hy), az = Math.clamp(3 * kz, lz, hz);
                    Segment s = new Segment(ax, ay, az, lx - ax, hx - ax, ly - ay, hy - ay, lz - az, hz - az);
                    if (kx == 0 && ky == 0 && kz == 0) out.addFirst(s);
                    else out.add(s);
                }
        return new Footprint(List.copyOf(out));
    }

    /**
     * Как {@link #tiled}, но колонна сетки 3×3 над контроллером (x, z = −1…1) пуста по всей высоте, кроме самого
     * контроллера: копёр стоит вокруг ствола, а клеть ходит в колонне.
     */
    public static Footprint tiledAroundShaft(int x0, int x1, int y0, int y1, int z0, int z1) {
        List<Segment> out = new ArrayList<>();
        out.add(new Segment(0, 0, 0, 0, 0, 0, 0, 0, 0));
        for (Segment s : tiled(x0, x1, y0, y1, z0, z1).segments)
            if (s.ax + s.x0 < -1 || s.ax + s.x1 > 1 || s.az + s.z0 < -1 || s.az + s.z1 > 1) out.add(s);
            else if (s.ax + s.x0 >= -1 && s.ax + s.x1 <= 1 && s.az + s.z0 >= -1 && s.az + s.z1 <= 1) continue;
            else throw new IllegalArgumentException("tile crosses the shaft column: " + s);
        return new Footprint(List.copyOf(out));
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

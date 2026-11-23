package dev.denfry.promptcritical.mod.multiblock;

import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;

/**
 * Точки подключения машин реального масштаба (спецификация real-scale machines, §3b «Connections»): трубы, воронки и
 * лотки цепляются только к клетке и грани, где у цельной модели нарисован фланец патрубка, а не к любой клетке
 * объёма. Клетки и грани — в пространстве модели (фасад на север, клетки от контроллера), как в шапках
 * art/blockbench/core/&lt;машина&gt;.author.js.
 */
public final class WholePorts {
    /** Порт: клетка модели и грань, через которую идёт подключение. */
    public record Port(int x, int y, int z, Direction face) {}

    private static Port p(int x, int y, int z, Direction face) {
        return new Port(x, y, z, face);
    }

    private static final Direction E = Direction.EAST, W = Direction.WEST, N = Direction.NORTH, S = Direction.SOUTH,
            U = Direction.UP, D = Direction.DOWN;

    private static final Map<String, List<Port>> PORTS = Map.of(
            // холодные (y 7) и горячие (y 9) нитки по обе стороны корпуса
            "vver_reactor", List.of(p(2, 7, -1, E), p(2, 7, 1, E), p(-2, 7, -1, W), p(-2, 7, 1, W),
                    p(2, 9, -1, E), p(2, 9, 1, E), p(-2, 9, -1, W), p(-2, 9, 1, W)),
            // первый контур снизу, пар — с торца коллектора, питательная вода — сбоку
            "vver_steam_generator", List.of(p(-1, 0, 0, D), p(1, 0, 0, D), p(7, 7, 0, E), p(2, 4, 2, S)),
            "vver_coolant_pump", List.of(p(-2, 1, 0, W), p(2, 1, 0, E)),
            "vver_pressurizer", List.of(p(2, 0, 0, E), p(0, 17, 0, U)),
            "bwr240_reactor", List.of(p(2, 6, 0, E), p(-2, 6, 0, W), p(2, 9, 0, E), p(0, 10, 2, S)),
            "candu6_reactor", List.of(p(4, 10, -5, E), p(4, 10, 5, E)),
            "bn600_reactor", List.of(p(7, 9, 0, E), p(7, 11, 0, E), p(0, 9, 7, S), p(0, 11, 7, S), p(0, 9, -7, N), p(0, 11, -7, N)),
            "am1_reactor", List.of(p(3, 7, 0, E), p(-3, 7, 0, W)));

    private WholePorts() {}

    /** Порты машины; пустой список — машина портов не объявила. */
    public static List<Port> of(String id) {
        return PORTS.getOrDefault(id, List.of());
    }

    /**
     * Можно ли подключиться к клетке в (dx, dy, dz) от контроллера машины {@code id}, повёрнутой на {@code facing},
     * с грани {@code side} (мировой). Машина без объявленных портов — как раньше, любая клетка.
     */
    public static boolean allows(String id, Direction facing, int dx, int dy, int dz, Direction side) {
        List<Port> ports = PORTS.get(id);
        if (ports == null) return true;
        double[] m = WholeShapes.unrotate(dx + 0.5, dz + 0.5, facing);
        int mx = (int) Math.floor(m[0]), mz = (int) Math.floor(m[1]);
        Direction face = side.getAxis().isVertical() ? side : modelFace(side, facing);
        for (Port p : ports) if (p.x() == mx && p.y() == dy && p.z() == mz && p.face() == face) return true;
        return false;
    }

    /** Мировая горизонтальная грань → грань модели (обратный поворот, как у отрисовки). */
    static Direction modelFace(Direction world, Direction facing) {
        return switch (facing) {
            case EAST -> world.getCounterClockWise();
            case SOUTH -> world.getOpposite();
            case WEST -> world.getClockWise();
            default -> world;
        };
    }
}

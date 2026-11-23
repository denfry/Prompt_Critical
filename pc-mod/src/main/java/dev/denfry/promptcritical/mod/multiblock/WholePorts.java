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

    private static final Map<String, List<Port>> PORTS = Map.ofEntries(
            // холодные (y 7) и горячие (y 9) нитки по обе стороны корпуса
            Map.entry("vver_reactor", List.of(p(2, 7, -1, E), p(2, 7, 1, E), p(-2, 7, -1, W), p(-2, 7, 1, W),
                    p(2, 9, -1, E), p(2, 9, 1, E), p(-2, 9, -1, W), p(-2, 9, 1, W))),
            // первый контур снизу, пар — с торца коллектора, питательная вода — сбоку
            Map.entry("vver_steam_generator", List.of(p(-1, 0, 0, D), p(1, 0, 0, D), p(7, 7, 0, E), p(2, 4, 2, S))),
            Map.entry("vver_coolant_pump", List.of(p(-2, 1, 0, W), p(2, 1, 0, E))),
            Map.entry("vver_pressurizer", List.of(p(2, 0, 0, E), p(0, 17, 0, U))),
            Map.entry("bwr240_reactor", List.of(p(2, 6, 0, E), p(-2, 6, 0, W), p(2, 9, 0, E), p(0, 10, 2, S))),
            Map.entry("candu6_reactor", List.of(p(4, 10, -5, E), p(4, 10, 5, E))),
            Map.entry("bn600_reactor", List.of(p(7, 9, 0, E), p(7, 11, 0, E), p(0, 9, 7, S), p(0, 11, 7, S), p(0, 9, -7, N), p(0, 11, -7, N))),
            Map.entry("am1_reactor", List.of(p(3, 7, 0, E), p(-3, 7, 0, W))),
            // колоша — с края площадки на юге (клетка (0, 3, 3)): чугун и кокс подают воронкой или лотком
            Map.entry("cupola_furnace_real", List.of(p(0, 3, 3, S))),
            // всас вниз через пол у контроллера, напорный став вверх над ним; вал Create — в порт на восточной грани
            Map.entry("mine_drainage_pump_geared", List.of(p(0, 0, 0, D), p(0, 1, 0, U))),
            // лобовая стенка внизу: питательный клапан (x −1) и дверца зольника (x 1); пар — с вентиля на обечайке;
            // с забрасывателями перед фронтом — питательная вода и зольник с боковых стенок
            Map.entry("lancashire_boiler", List.of(p(-1, 0, 0, N), p(1, 0, 0, N), p(0, 3, 2, U), p(-2, 0, 0, W), p(2, 0, 0, E))),
            // пар сверху на клапанную коробку, выхлоп с западной грани у цилиндра
            Map.entry("mill_engine", List.of(p(0, 2, 0, U), p(0, 0, 0, W))),
            // уголь в бункера забрасывателей сверху
            Map.entry("lancashire_stoker", List.of(p(-1, 2, 0, U), p(1, 2, 0, U))),
            // тигель и кокс — через крышку ямы; клетки кладки закрыты
            Map.entry("crucible_furnace_real", List.of(p(0, 0, 0, U))),
            // химочищенная вода — с севера на колонку, конденсат — с юга, питательная вода — вниз из бака у контроллера;
            // греющий пар деаэратор берёт из барабана ближнего котла (07 §7.9.6), паропровод и выпар нарисованы
            Map.entry("deaerator_real", List.of(p(2, 4, -1, N), p(2, 4, 1, S), p(0, 0, 0, D))),
            // вода — в верхнее днище, умягчённая — из северной грани нижней клетки
            Map.entry("ion_exchange_column_real", List.of(p(0, 3, 0, U), p(0, 0, 0, N))),
            // электрические машины: вал Create — через клетку-порт, кабели — к клеммам, труб и лотков нет
            Map.entry("shaft_generator_real", List.of()),
            Map.entry("electric_motor_mv_real", List.of()));

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

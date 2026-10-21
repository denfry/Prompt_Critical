package dev.denfry.promptcritical.mod.automation;

import dev.denfry.promptcritical.sim.automation.AutomationKind;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Клеммы на корпусе блока (03c §3c.2, 03d §3d.2.1): точка в модельных координатах (пиксели, фасад на север, контроллер
 * в начале) и нормаль колодки. Клеммы стоят рядами на лицевой панели модели; у клемм механизмов — колодка на верхней
 * клетке ячейки, на крышке двигателя, на торце ТП. Порядок — {@link AutomationKind#terminals()}.
 */
public final class TerminalLayout {
    /** Клемма модели: точка (пиксели) и нормаль колодки (в модели). */
    public record Point(double x, double y, double z, Direction normal) {
    }

    private static final Map<AutomationKind, List<Point>> LAYOUTS = new EnumMap<>(AutomationKind.class);

    static {
        front(AutomationKind.RELAY, 5.8, 2, 3, 14, 13, 4);
        front(AutomationKind.RELAY_LATCHING, 5.8, 1.5, 3, 14.5, 13, 5);
        front(AutomationKind.TIME_RELAY, 7.9, 2, 2, 14, 6, 4);
        front(AutomationKind.STEPPING_SWITCH, 0.9, 2, 2, 14, 13, 5);
        front(AutomationKind.CAM_CONTROLLER, 0.9, 1.5, 1.5, 14.5, 14, 6);
        front(AutomationKind.PROCESS_SWITCH, 9.3, 5, 1.5, 11, 1.5, 3);
        front(AutomationKind.POWER_SUPPLY, 8.3, 4, 3, 12, 3, 2);
        front(AutomationKind.PUSHBUTTON, 10.87, 2.5, 2.5, 13.5, 13.5, 2);
        front(AutomationKind.SELECTOR_SWITCH, 12.4, 2.5, 2.5, 13.5, 13.5, 2);
        front(AutomationKind.KEY_SWITCH, 12.4, 2.5, 2, 13.5, 2, 3);
        front(AutomationKind.INDICATOR_LAMP, 10.4, 3, 2, 13, 2, 2);
        front(AutomationKind.REDSTONE_INTERFACE, 5.8, 2, 3, 14, 3, 4);
        front(AutomationKind.GAS_DETECTOR, 7.9, 5, 3.5, 11.5, 13.5, 3);
        front(AutomationKind.ANALOG_SUMMER, -0.6, 2, 2, 14, 2, 5);
        front(AutomationKind.ANALOG_INTEGRATOR, -1.6, 2, 2, 14, 4.5, 4);
        front(AutomationKind.ANALOG_DIFFERENTIATOR, -0.6, 4, 2, 12, 2, 2);
        front(AutomationKind.ANALOG_MULTIPLIER, -1.6, 3, 2, 13, 2, 3);
        front(AutomationKind.ANALOG_FUNCTION, -0.6, 4, 2, 12, 2, 2);
        front(AutomationKind.ANALOG_COMPARATOR, -0.6, 2, 2, 14, 2, 5);
        front(AutomationKind.MANUAL_STATION, 4.4, 1.5, 1.5, 14.5, 4, 5);
        front(AutomationKind.SIGNAL_CONVERTER, 8.9, 4, 3, 12, 3, 2);
        front(AutomationKind.BREAKER_IO, -0.6, 2.5, 21, 13.5, 28, 4);
        LAYOUTS.put(AutomationKind.MOTOR_IO, List.of(new Point(5.5, 15.6, 7, Direction.UP), new Point(10.5, 15.6, 7, Direction.UP),
                new Point(5.5, 15.6, 10, Direction.UP), new Point(10.5, 15.6, 10, Direction.UP)));
        LAYOUTS.put(AutomationKind.CONSUMER_IO, List.of(new Point(5, -10, -16.1, Direction.NORTH),
                new Point(11, -10, -16.1, Direction.NORTH)));
    }

    private TerminalLayout() {
    }

    /** Ряды клемм на плоскости {@code z} лицевой стороны: от левого верхнего угла, {@code cols} в ряду. */
    private static void front(AutomationKind kind, double z, double x0, double y0, double x1, double y1, int cols) {
        int n = kind.terminalCount();
        int rows = (n + cols - 1) / cols;
        List<Point> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int r = i / cols, c = i % cols;
            double x = cols == 1 ? (x0 + x1) / 2 : x0 + (x1 - x0) * c / (cols - 1);
            double y = rows == 1 ? y1 : y1 - (y1 - y0) * r / (rows - 1);
            // смотрящий на фасад видит +x модели справа налево: первая клемма — слева для него
            out.add(new Point(16 - x, y, z, Direction.NORTH));
        }
        LAYOUTS.put(kind, List.copyOf(out));
    }

    public static List<Point> of(AutomationKind kind) {
        return LAYOUTS.get(kind);
    }

    /** Точка клеммы в мире: контроллер {@code pos}, фасад {@code facing}. */
    public static Vec3 world(BlockPos pos, Direction facing, Point p) {
        double[] r = rotate(facing, p.x - 8, p.z - 8);
        return new Vec3(pos.getX() + 0.5 + r[0] / 16, pos.getY() + p.y / 16, pos.getZ() + 0.5 + r[1] / 16);
    }

    /** Нормаль колодки в мире. */
    public static Direction normal(Direction facing, Point p) {
        if (p.normal.getAxis().isVertical()) return p.normal;
        return switch (facing) {
            case EAST -> p.normal.getClockWise();
            case SOUTH -> p.normal.getOpposite();
            case WEST -> p.normal.getCounterClockWise();
            default -> p.normal;
        };
    }

    /** Поворот модели (фасад на север) к {@code facing} вокруг центра клетки — как у формы блока. */
    static double[] rotate(Direction facing, double x, double z) {
        return switch (facing) {
            case EAST -> new double[] {-z, x};
            case SOUTH -> new double[] {-x, -z};
            case WEST -> new double[] {z, -x};
            default -> new double[] {x, z};
        };
    }
}

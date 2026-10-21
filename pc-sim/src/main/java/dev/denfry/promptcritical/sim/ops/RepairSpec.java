package dev.denfry.promptcritical.sim.ops;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Строка «устранить» панели (13 §13.6.5): инструмент в руке, запчасть и количество, условия, время работ.
 *
 * @param tool      ID предмета-инструмента без пространства имён; null — пустая рука или запчасть в руке
 * @param part      ID запчасти; null — без расхода
 * @param partial   ремонт снимает часть износа (подтяжка сальника)
 */
public record RepairSpec(String tool, String part, int quantity, Set<RepairCondition> needs, double durationS,
        boolean partial) {
    public RepairSpec {
        if (quantity < 0 || !(durationS >= 0)) throw new IllegalArgumentException("repair");
        needs = needs.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(needs));
    }

    /** Способы ремонта среза I0–I1 (13 §13.6.7); пусто — вид вне среза. */
    public static List<RepairSpec> slice(MaintenanceFaultKind kind) {
        Set<RepairCondition> stop = Set.of(RepairCondition.STOP);
        return switch (kind) {
            case BEARING_WEAR -> List.of(new RepairSpec(null, "bronze_bearing_shell", 1, stop, 60, false));
            case LUBRICATION -> List.of(new RepairSpec("oil_can", "tallow", 1, Set.of(), 10, false));
            case HX_FOULING -> List.of(new RepairSpec("boiler_scraper", null, 0,
                    Set.of(RepairCondition.STOP, RepairCondition.COLD, RepairCondition.DRAINED), 60, false));
            case PACKING_LEAK -> List.of(new RepairSpec("torque_wrench", null, 0, Set.of(), 10, true),
                    new RepairSpec(null, "gland_packing", 1, stop, 30, false));
            case IMPELLER_EROSION -> List.of(new RepairSpec(null, "pump_impeller", 1, stop, 120, false));
            case ROPE_WEAR -> List.of(new RepairSpec(null, "wire_rope", 1, stop, 120, false));
            default -> List.of();
        };
    }
}

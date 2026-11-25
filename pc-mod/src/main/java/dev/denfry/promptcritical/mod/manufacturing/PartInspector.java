package dev.denfry.promptcritical.mod.manufacturing;

import dev.denfry.promptcritical.sim.manufacturing.PartInspection;
import dev.denfry.promptcritical.sim.manufacturing.PartSpec;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Замер детали инструментом (18 §18.6.10): пишет отметку замера в {@link ManufacturingComponents#INSPECTION_CHECKS},
 * а когда сделаны все нужные детали замеры — ставит в паспорте флаг «проверено» и снимает маску.
 */
public final class PartInspector {
    /** Итог замера. */
    public enum Outcome { NO_PART, NOT_APPLICABLE, ALREADY, MEASURED, INSPECTED }

    /** Итог и строка для игрока ({@code null} — нечего сказать). */
    public record Result(Outcome outcome, @Nullable Component message) {}

    private PartInspector() {
    }

    public static Result measure(ItemStack part, PartInspection.Tool tool) {
        PartSpec spec = part.get(ManufacturingComponents.PART_SPEC.get());
        if (spec == null) return new Result(Outcome.NO_PART, ManufacturingText.msg("inspect.no_part"));
        if (spec.inspected()) return new Result(Outcome.ALREADY, ManufacturingText.msg("inspect.already"));
        PartInspection.Check c = PartInspection.measure(tool, spec);
        if (c == null) return new Result(Outcome.NOT_APPLICABLE, ManufacturingText.msg("inspect.not_applicable." + key(tool)));
        int mask = PartInspection.record(part.getOrDefault(ManufacturingComponents.INSPECTION_CHECKS.get(), 0), c);
        Component reading = ManufacturingText.reading(spec, c);
        if (PartInspection.complete(spec, mask)) {
            part.remove(ManufacturingComponents.INSPECTION_CHECKS.get());
            part.set(ManufacturingComponents.PART_SPEC.get(), spec.inspect());
            return new Result(Outcome.INSPECTED, ManufacturingText.msg("inspect.done", reading));
        }
        part.set(ManufacturingComponents.INSPECTION_CHECKS.get(), mask);
        return new Result(Outcome.MEASURED, reading);
    }

    static String key(PartInspection.Tool t) {
        return t.name().toLowerCase(java.util.Locale.ROOT);
    }
}

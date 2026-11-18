package dev.denfry.promptcritical.sim.heavywater;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Объём воды с непрерывной долей дейтерия (замедлитель, теплоноситель, куб колонны). Смешение — по атомам водорода:
 * n_H = 2m/M(x), доля D после смешения ΣD/ΣH. Тритий (атомы) идёт вместе с водой: отбор уносит его пропорционально
 * массе — так тритий реактора попадает с дренажом в ремонтные баки и на установку повышения концентрации.
 */
public final class HeavyWaterInventory {
    private double kg, x = HeavyWater.REACTOR_GRADE_X, tritiumAtoms;

    public double kg() {
        return kg;
    }

    /** Доля D среди атомов водорода; у пустого — последняя бывшая. */
    public double purity() {
        return x;
    }

    public double tritiumAtoms() {
        return tritiumAtoms;
    }

    /** Атомы дейтерия. */
    public double deuteriumAtoms() {
        return 2 * HeavyWater.moles(kg, x) * x * HeavyWater.AVOGADRO;
    }

    /** Долить {@code addKg} воды с долей D {@code addX}. */
    public void add(double addKg, double addX) {
        if (!(addKg > 0)) return;
        double cx = HeavyWater.clamp(addX);
        double h0 = 2 * HeavyWater.moles(kg, x), h1 = 2 * HeavyWater.moles(addKg, cx);
        double mixed = (h0 * x + h1 * cx) / (h0 + h1);
        kg += addKg;
        x = mixed;
    }

    /** Отобрать до {@code takeKg}; возвращает взятое. Доля D не меняется, тритий уходит пропорционально. */
    public double remove(double takeKg) {
        double t = Math.max(0, Math.min(takeKg, kg));
        if (t <= 0) return 0;
        tritiumAtoms *= kg - t <= 0 ? 0 : (kg - t) / kg;
        kg -= t;
        if (kg < 1e-9) kg = 0;
        return t;
    }

    /** Наработка трития (атомов); распад ведёт владелец. */
    public void setTritiumAtoms(double atoms) {
        tritiumAtoms = Math.max(0, atoms);
    }

    /** Сразу заполнить {@code fillKg} воды чистоты {@code fillX} (новая станция, тест). */
    public void set(double fillKg, double fillX) {
        kg = Math.max(0, fillKg);
        x = HeavyWater.clamp(fillX);
        tritiumAtoms = 0;
    }

    public void save(StateWriter out, String prefix) {
        out.putDouble(prefix + "_kg", kg);
        out.putDouble(prefix + "_x", x);
        out.putDouble(prefix + "_t", tritiumAtoms);
    }

    /** Нет ключей (старое сохранение) — оставляет текущее содержимое. */
    public void load(StateReader in, String prefix) {
        if (!in.has(prefix + "_kg")) return;
        kg = Math.max(0, in.optDouble(prefix + "_kg", 0));
        x = HeavyWater.clamp(in.optDouble(prefix + "_x", HeavyWater.REACTOR_GRADE_X));
        tritiumAtoms = Math.max(0, in.optDouble(prefix + "_t", 0));
    }
}

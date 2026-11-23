package dev.denfry.promptcritical.mod.manufacturing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.sim.manufacturing.PartDefect;
import dev.denfry.promptcritical.sim.manufacturing.Workpiece;
import dev.denfry.promptcritical.sim.metallurgy.AirCooling;
import dev.denfry.promptcritical.sim.metallurgy.HeatTreatState;
import dev.denfry.promptcritical.sim.metallurgy.SteelFamily;
import dev.denfry.promptcritical.sim.metallurgy.SteelGrade;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Компонент {@code workpiece} поковки (18 §18.4, §18.7): марка, сечение, размеры, твёрдость, дефекты и история
 * процесса заготовки {@link Workpiece}. Температура не хранится числом на каждый тик: предмет помнит температуру и тик
 * выдачи, а текущая считается остыванием на воздухе ({@link AirCooling}) — как у проката {@code steel_stock}.
 * Перечисления — по имени, неизвестные дефекты пропускаются, чтобы предмет пережил другую версию мода.
 */
public record WorkpieceData(String family, Map<String, Float> chemPct, String state, String section, float aMm, float bMm,
        float lengthMm, float tempK, long tick, float hardnessHrc, Map<String, Float> defects, float oxideSq, float quenchHrc,
        float temperP) {
    /** Воздух цеха, К [моё: 20 °C]. */
    public static final double AMBIENT_K = 293.15;
    /** Секунд игры на тик сервера. */
    public static final double SECONDS_PER_TICK = 0.05;

    public static final Codec<WorkpieceData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("family").forGetter(WorkpieceData::family),
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).fieldOf("chem").forGetter(WorkpieceData::chemPct),
            Codec.STRING.fieldOf("state").forGetter(WorkpieceData::state),
            Codec.STRING.fieldOf("section").forGetter(WorkpieceData::section),
            Codec.FLOAT.fieldOf("a_mm").forGetter(WorkpieceData::aMm),
            Codec.FLOAT.fieldOf("b_mm").forGetter(WorkpieceData::bMm),
            Codec.FLOAT.fieldOf("length_mm").forGetter(WorkpieceData::lengthMm),
            Codec.FLOAT.fieldOf("temp_k").forGetter(WorkpieceData::tempK),
            Codec.LONG.fieldOf("tick").forGetter(WorkpieceData::tick),
            Codec.FLOAT.fieldOf("hrc").forGetter(WorkpieceData::hardnessHrc),
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("defects", Map.of()).forGetter(WorkpieceData::defects),
            Codec.FLOAT.optionalFieldOf("oxide", 0f).forGetter(WorkpieceData::oxideSq),
            Codec.FLOAT.optionalFieldOf("quench_hrc", 0f).forGetter(WorkpieceData::quenchHrc),
            Codec.FLOAT.optionalFieldOf("temper_p", 0f).forGetter(WorkpieceData::temperP)).apply(i, WorkpieceData::new));

    public WorkpieceData {
        chemPct = Map.copyOf(chemPct);
        defects = Map.copyOf(defects);
    }

    /** Снимок заготовки на тике {@code now}. */
    public static WorkpieceData of(Workpiece w, long now) {
        Map<String, Float> d = new LinkedHashMap<>();
        w.defects().forEach((k, v) -> d.put(k.name(), v.floatValue()));
        SteelGrade g = w.grade();
        return new WorkpieceData(g.family().name(), new LinkedHashMap<>(g.chemPct()), g.state().name(), w.section().name(),
                (float) (w.aM() * 1000), (float) (w.bM() * 1000), (float) (w.lengthM() * 1000), (float) w.tempK(), now,
                (float) w.hardnessHrc(), d, (float) w.oxideSq(), (float) w.quenchHrc(), (float) w.temperP());
    }

    /** Заготовка на тике {@code now}: температура — после остывания на воздухе с момента снимка. */
    public Workpiece workpiece(long now) {
        SteelFamily f = enumOr(SteelFamily.class, family, SteelFamily.CARBON);
        SteelGrade g = SteelGrade.of(f, f.designation, chemPct, enumOr(HeatTreatState.class, state, f.referenceState));
        Map<PartDefect, Double> d = new EnumMap<>(PartDefect.class);
        defects.forEach((k, v) -> {
            PartDefect p = enumOr(PartDefect.class, k, null);
            if (p != null && v != null && v > 0) d.put(p, v.doubleValue());
        });
        Workpiece w = new Workpiece(g, enumOr(Workpiece.Section.class, section, Workpiece.Section.SQUARE),
                Math.max(1e-3, aMm / 1000.0), Math.max(1e-3, bMm / 1000.0), Math.max(1e-3, lengthMm / 1000.0),
                Math.max(1, tempK), hardnessHrc, d, Math.max(0, oxideSq), Math.max(0, quenchHrc), Math.max(0, temperP));
        double seconds = Math.max(0, now - tick) * SECONDS_PER_TICK;
        return w.tempK() > AMBIENT_K && seconds > 0
                ? w.withTemp(AirCooling.tempAfter(w.tempK(), AMBIENT_K, w.equivalentThicknessM(), seconds)) : w;
    }

    /** Температура на тике {@code now}, К. */
    public double tempK(long now) {
        return workpiece(now).tempK();
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return fallback;
        }
    }
}

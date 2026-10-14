package dev.denfry.promptcritical.sim.metallurgy;

import java.util.Objects;

/**
 * Заливка формы (08 §8.9): время затвердевания по правилу Чворинова t_s = B·(V/A)² и дефект отливки.
 * B для стали в песчаной форме — {@code casting.chvorinov_b_sand_min_cm2} (2,5 мин/см²); для других сплавов —
 * множитель {@link CastAlloy#chvorinovFactor}, для кокиля — {@link MoldKind#chvorinovScale}.
 */
public final class Casting {
    /** Прибыль должна иметь модуль ≥ 1,2 модуля отливки. */
    public static final double RISER_MODULUS_RATIO = 1.2;
    /** Влажность формовочной смеси, выше которой — газовая пористость. */
    public static final double MAX_SAND_MOISTURE = 0.06;
    /** Минимальный перегрев над ликвидусом, К; ниже — холодный спай. */
    public static final double MIN_SUPERHEAT_K = 50;
    /** Отношение толщин стенок, выше которого у склонных сплавов горячие трещины [допущение, в GDD нет]. */
    public static final double HOT_TEAR_THICKNESS_RATIO = 3;
    /** Отливка серого чугуна, затвердевшая быстрее, отбеливается [допущение: тонкие стенки &lt; 5 мм]. */
    public static final double CHILL_TIME_S = 60;

    /**
     * Условия заливки.
     *
     * @param kind           модель
     * @param alloy          сплав
     * @param mold           форма
     * @param moduleCm       модуль отливки V/A, см
     * @param riserModuleCm  модуль прибыли, см; NaN — прибыли нет
     * @param sandMoisture   влажность смеси (у кокиля не учитывается)
     * @param superheatK     перегрев металла над ликвидусом при заливке, К
     * @param deoxidized     сталь раскислена
     * @param thicknessRatio отношение наибольшей толщины стенки к наименьшей
     * @param dn             условный проход корпуса, мм
     */
    public record Pour(CastingKind kind, CastAlloy alloy, MoldKind mold, double moduleCm, double riserModuleCm,
            double sandMoisture, double superheatK, boolean deoxidized, double thicknessRatio, int dn) {
        public Pour {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(alloy, "alloy");
            Objects.requireNonNull(mold, "mold");
            if (!(moduleCm > 0 && thicknessRatio >= 1 && sandMoisture >= 0)) throw new IllegalArgumentException("pour");
        }
    }

    /** Итог: время затвердевания, с, и дефект. */
    public record Result(double solidificationS, CastingDefect defect) {
        public CastingSpec spec(Pour p, long serial) {
            return new CastingSpec(p.kind(), p.alloy().id, p.dn(), defect, 0, serial);
        }
    }

    private final double bSandSteelMinCm2;

    /** @param bSandSteelMinCm2 B стали в песчаной форме, мин/см² */
    public Casting(double bSandSteelMinCm2) {
        if (!(bSandSteelMinCm2 > 0)) throw new IllegalArgumentException("B: " + bSandSteelMinCm2);
        this.bSandSteelMinCm2 = bSandSteelMinCm2;
    }

    public Casting(MetallurgyConfig cfg) {
        this(cfg.chvorinovBSandMinCm2());
    }

    /** Постоянная Чворинова B, мин/см². */
    public double chvorinovB(CastAlloy alloy, MoldKind mold, double superheatK) {
        return bSandSteelMinCm2 * alloy.chvorinovFactor(superheatK) * mold.chvorinovScale;
    }

    /** t_s = B·M², с. */
    public double solidificationS(CastAlloy alloy, MoldKind mold, double moduleCm, double superheatK) {
        return chvorinovB(alloy, mold, superheatK) * moduleCm * moduleCm * 60;
    }

    public Result pour(Pour p) {
        double ts = solidificationS(p.alloy(), p.mold(), p.moduleCm(), p.superheatK());
        return new Result(ts, defect(p, ts));
    }

    private static CastingDefect defect(Pour p, double ts) {
        if (Double.isNaN(p.riserModuleCm()) || p.riserModuleCm() < RISER_MODULUS_RATIO * p.moduleCm())
            return CastingDefect.SHRINKAGE_CAVITY;
        if (p.alloy().hotShort && p.thicknessRatio() > HOT_TEAR_THICKNESS_RATIO) return CastingDefect.HOT_TEAR;
        if (p.superheatK() < MIN_SUPERHEAT_K) return CastingDefect.COLD_SHUT;
        if (p.mold() == MoldKind.SAND && p.sandMoisture() > MAX_SAND_MOISTURE) return CastingDefect.GAS_POROSITY;
        if (p.alloy() == CastAlloy.CARBON_STEEL && !p.deoxidized()) return CastingDefect.GAS_POROSITY;
        if (p.alloy() == CastAlloy.GRAY_IRON && (p.mold() == MoldKind.PERMANENT || ts < CHILL_TIME_S))
            return CastingDefect.CHILL;
        return CastingDefect.NONE;
    }
}

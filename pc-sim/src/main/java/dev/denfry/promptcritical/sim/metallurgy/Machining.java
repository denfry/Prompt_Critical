package dev.denfry.promptcritical.sim.metallurgy;

import java.util.Objects;

/**
 * Резание на станках I0 — {@code machine_lathe}, {@code drill_press} (08 §8.10). Мощность резания
 * P_c = k_c·a_p·f·v_c → стресс Create; стойкость резца {@code cutting_tool} по Тейлору v_c·T^n = C_T.
 */
public final class Machining {
    /** Обрабатываемый материал: удельная сила резания k_c и множитель C_T к стали. */
    public enum WorkMaterial {
        /** Сталь 45: k_c ≈ 2000 Н/мм². */
        STEEL_45(2000, 1.0),
        /** Чугун: 1100 Н/мм²; C_T быстрорежущего по серому чугуну ≈ 0,9 стального [сверить]. */
        CAST_IRON(1100, 0.9),
        /** Алюминий: 700 Н/мм²; режется в разы быстрее, C_T ×4 [сверить]. */
        ALUMINIUM(700, 4.0);

        /** k_c, Н/мм². */
        public final double kc;
        public final double taylorScale;

        WorkMaterial(double kc, double taylorScale) {
            this.kc = kc;
            this.taylorScale = taylorScale;
        }

        /** Материал заготовки по сплаву отливки; медь и бронза режутся как чугун (k_c ≈ 1100) [допущение]. */
        public static WorkMaterial of(CastAlloy alloy) {
            return switch (alloy) {
                case CARBON_STEEL -> STEEL_45;
                case ALUMINIUM -> ALUMINIUM;
                case GRAY_IRON, BRONZE, COPPER -> CAST_IRON;
            };
        }
    }

    /** Материал резца. Твёрдый сплав — (B): n = 0,25, C_T = 300 м/мин [сверить]. */
    public enum ToolMaterial { HSS, CARBIDE }

    private final MetallurgyConfig cfg;

    public Machining(MetallurgyConfig cfg) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
    }

    /** Скорость резания v_c = πDn/1000, м/мин (D в мм, n в об/мин). */
    public static double cuttingSpeed(double diameterMm, double rpm) {
        return Math.PI * diameterMm * Math.abs(rpm) / 1000;
    }

    /** Мощность резания P_c = k_c·a_p·f·v_c, Вт (a_p, f — мм, мм/об; v_c — м/мин). */
    public static double cuttingPowerW(WorkMaterial m, double depthMm, double feedMmRev, double vcMMin) {
        return m.kc * depthMm * feedMmRev * vcMMin / 60;
    }

    /** Съём металла a_p·f·v_c, мм³/с. */
    public static double removalRateMm3S(double depthMm, double feedMmRev, double vcMMin) {
        return depthMm * feedMmRev * vcMMin * 1000 / 60;
    }

    public double taylorN(ToolMaterial t) {
        return t == ToolMaterial.HSS ? cfg.taylorN() : 0.25;
    }

    public double taylorC(ToolMaterial t, WorkMaterial m) {
        return (t == ToolMaterial.HSS ? cfg.taylorCMMin() : 300) * m.taylorScale;
    }

    /** Стойкость T = (C_T/v_c)^{1/n}, мин. */
    public double toolLifeMin(ToolMaterial t, WorkMaterial m, double vcMMin) {
        if (vcMMin <= 0) return Double.POSITIVE_INFINITY;
        return Math.pow(taylorC(t, m) / vcMMin, 1 / taylorN(t));
    }

    /** Резец: износ по правилу Майнера dW = dt/T(v_c); 1 — затупился. */
    public static final class CuttingTool {
        private final ToolMaterial material;
        private double wear;

        public CuttingTool(ToolMaterial material) {
            this(material, 0);
        }

        /** Резец с износом {@code wear} (0 — новый, 1 — тупой). */
        public CuttingTool(ToolMaterial material, double wear) {
            this.material = Objects.requireNonNull(material, "material");
            this.wear = Math.max(0, Math.min(1, wear));
        }

        public ToolMaterial material() {
            return material;
        }

        public double wear() {
            return wear;
        }

        public boolean dull() {
            return wear >= 1;
        }

        /** Переточка. */
        public void regrind() {
            wear = 0;
        }
    }

    /**
     * Шаг резания (FAST): износ резца и мощность.
     *
     * @param dt         шаг, с
     * @param tool       резец
     * @param m          материал заготовки
     * @param diameterMm диаметр обработки (заготовки на токарном, сверла на сверлильном), мм
     * @param rpm        частота шпинделя
     * @param depthMm    глубина резания a_p
     * @param feedMmRev  подача f
     * @return мощность резания, Вт (0 — резец тупой, резания нет)
     */
    public double cut(double dt, CuttingTool tool, WorkMaterial m, double diameterMm, double rpm, double depthMm,
            double feedMmRev) {
        if (tool.dull()) return 0;
        double vc = cuttingSpeed(diameterMm, rpm);
        if (vc <= 0) return 0;
        tool.wear = Math.min(1, tool.wear + dt / 60 / toolLifeMin(tool.material, m, vc));
        return cuttingPowerW(m, depthMm, feedMmRev, vc);
    }
}

package dev.denfry.promptcritical.sim.metallurgy;

import java.util.Objects;

/**
 * Резание на станках I0 — {@code machine_lathe}, {@code drill_press} (08 §8.10). Мощность резания
 * P_c = k_c·a_p·f·v_c → стресс Create; стойкость резца {@code cutting_tool} по Тейлору v_c·T^n = C_T.
 */
public final class Machining {
    /**
     * Обрабатываемый материал: удельная сила резания k_c токарного станка (при толщине среза ≈ 0,25 мм), множитель
     * C_T к стали и константы Кинцле k_c1.1 (сила на 1 мм² сечения при h = b = 1 мм) и m_c для любой толщины среза:
     * k_c = k_c1.1 · h^{−m_c} (O. Kienzle, «Die Bestimmung von Kräften und Leistungen an spanenden Werkzeugen und
     * Werkzeugmaschinen», VDI-Z 94 (1952); таблица König W., Klocke F. «Fertigungsverfahren 1», для C45 и GG-20).
     */
    public enum WorkMaterial {
        /** Сталь 45 (C45): k_c ≈ 2000 Н/мм²; k_c1.1 = 1450 Н/мм², m_c = 0,27 [сверить по König–Klocke]. */
        STEEL_45(2000, 1.0, 1450, 0.27),
        /** Чугун (СЧ20 ≈ GG-20): 1100 Н/мм²; k_c1.1 = 1020, m_c = 0,25; C_T быстрорежущего ≈ 0,9 стального [сверить]. */
        CAST_IRON(1100, 0.9, 1020, 0.25),
        /** Алюминий: 700 Н/мм²; k_c1.1 ≈ 500, m_c = 0,25 [сверить]; режется в разы быстрее, C_T ×4 [сверить]. */
        ALUMINIUM(700, 4.0, 500, 0.25);

        /** k_c, Н/мм². */
        public final double kc;
        public final double taylorScale;
        /** k_c1.1, Н/мм², и показатель m_c Кинцле. */
        public final double kc11, mc;

        WorkMaterial(double kc, double taylorScale, double kc11, double mc) {
            this.kc = kc;
            this.taylorScale = taylorScale;
            this.kc11 = kc11;
            this.mc = mc;
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

    /**
     * Удельная сила резания по Кинцле k_c = k_c1.1 · h^{−m_c}, Н/мм²: тонкая стружка режется с большей удельной силой
     * (на кромке растёт доля трения и пластического сдвига).
     *
     * @param hMm толщина среза h, мм (у фрезы — средняя h_m)
     */
    public static double kienzleKc(WorkMaterial m, double hMm) {
        if (!(hMm > 0)) throw new IllegalArgumentException("h: " + hMm);
        return m.kc11 * Math.pow(hMm, -m.mc);
    }

    /**
     * Рост силы резания с износом: изношенная задняя грань трётся о поверхность, к концу стойкости (VB ≈ 0,3–0,5 мм)
     * сила выше на ~30 % (König, Klocke «Fertigungsverfahren 1», поправка K_ver на износ 1,3–1,5) [сверить].
     */
    public static double wearForceFactor(double wear) {
        return 1 + 0.3 * Math.max(0, Math.min(1, wear));
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

        /** Износ за {@code dt} с, если кромка в резании долю {@code share} времени при стойкости {@code lifeMin}. */
        void wear(double dt, double share, double lifeMin) {
            if (lifeMin > 0) wear = Math.min(1, wear + dt * share / 60 / lifeMin);
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

package dev.denfry.promptcritical.sim.waste;

/**
 * Остекловывание высокоактивных отходов и тепловой режим стеклоблока.
 *
 * <p>Два стекла промышленной практики:
 * <ul>
 *   <li><b>боросиликатное</b> (Ла-Аг, Селлафилд; стекло R7T7): варка 1100–1150 °C, плотность 2,75 г/см³,
 *   теплопроводность ≈ 1,1 Вт/(м·К), включение оксидов отходов до 18,5 % по массе (предел — растворимость MoO₃ и
 *   тепловыделение), температура стеклования T_g ≈ 510 °C;</li>
 *   <li><b>алюмофосфатное</b> (ЭП-500, ПО «Маяк»): варка 900–1000 °C — меньше летучесть Cs и Ru, — плотность 2,6 г/см³,
 *   ≈ 0,9 Вт/(м·К), включение ≈ 10 %; T_g ≈ 450 °C, но кристаллизуется уже выше ≈ 400 °C, после чего выщелачивается
 *   на порядки быстрее. Поэтому для него предел хранения — 400 °C, для боросиликатного — T_g.</li>
 * </ul>
 *
 * <p>Температура: стеклоблок — длинный цилиндр с равномерным источником q''' = Q/V; перепад центр — поверхность
 * ΔT = q'''R²/(4k) (Карслоу, Егер, «Теплопроводность твёрдых тел», гл. 7). Поверхность отдаёт тепло воздуху
 * хранилища с коэффициентом h (естественная конвекция плюс излучение ≈ 10 Вт/(м²·К)); геометрия H/D = 3, как CSD-V.
 */
public final class Vitrification {
    public enum Glass {
        BOROSILICATE(WasteForm.BOROSILICATE_GLASS, 1.1, 0.185, 783, 1398),
        PHOSPHATE(WasteForm.PHOSPHATE_GLASS, 0.9, 0.10, 673, 1223);

        public final WasteForm form;
        /** Теплопроводность, Вт/(м·К); предельное включение оксидов; допустимая температура центра, К; варка, К. */
        public final double conductivity, maxLoading, limitK, meltK;

        Glass(WasteForm form, double conductivity, double maxLoading, double limitK, double meltK) {
            this.form = form;
            this.conductivity = conductivity;
            this.maxLoading = maxLoading;
            this.limitK = limitK;
            this.meltK = meltK;
        }
    }

    public static final double H_AIR_W_M2K = 10;

    private Vitrification() {
    }

    /** Стекла на {@code oxideKg} оксидов отходов при включении {@code loading} (не выше предельного), кг. */
    public static double glassKg(Glass g, double oxideKg, double loading) {
        return oxideKg / Math.min(loading, g.maxLoading);
    }

    /** Перепад температуры центр — поверхность, К. */
    public static double centreRiseK(Glass g, double heatW, double glassKg) {
        if (glassKg <= 0) return 0;
        double v = glassKg / (g.form.densityKgL * 1000);
        double r = g.form.radiusM(glassKg);
        return heatW / v * r * r / (4 * g.conductivity);
    }

    /** Температура поверхности стеклоблока в воздухе {@code airK}, К. */
    public static double surfaceK(Glass g, double heatW, double glassKg, double airK, double hWm2K) {
        double a = g.form.surfaceM2(glassKg);
        return a > 0 ? airK + heatW / (hWm2K * a) : airK;
    }

    /** Температура в центре стеклоблока, К. */
    public static double centreK(Glass g, double heatW, double glassKg, double airK) {
        return surfaceK(g, heatW, glassKg, airK, H_AIR_W_M2K) + centreRiseK(g, heatW, glassKg);
    }

    /** Предельное тепловыделение блока по температуре центра, Вт. */
    public static double maxHeatW(Glass g, double glassKg, double airK) {
        double perW = centreK(g, 1, glassKg, airK) - airK;
        return perW > 0 ? (g.limitK - airK) / perW : Double.POSITIVE_INFINITY;
    }
}

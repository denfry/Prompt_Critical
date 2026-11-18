package dev.denfry.promptcritical.sim.ops;

/**
 * Фильтрующий противогаз с одним сменным фильтром (13 §13.7): кратность {@value Ppe#GAS_MASK_FACTOR} по примеси
 * своего фильтра, пока сорбент не насыщен; ресурс расходуется пропорционально концентрации × времени при лёгочной
 * вентиляции {@value Ppe#BREATHING_LPM} л/мин. Кислорода маска не добавляет: при O₂ ниже
 * {@value Ppe#MASK_MIN_O2} и от удушающих газов защиты нет.
 */
public final class GasMask {
    private final FilterType filter;
    private double loaded;

    /** Маска с фильтром {@code filter} ({@code null} — без фильтра), в котором уже поглощено {@code loaded}. */
    public GasMask(FilterType filter, double loaded) {
        this.filter = filter;
        this.loaded = Math.max(0, loaded);
    }

    public FilterType filter() {
        return filter;
    }

    /** Поглощено: моли газа или мг частиц (фильтр P). */
    public double loaded() {
        return loaded;
    }

    /** Доля израсходованного ресурса фильтра, 0…1. */
    public double usedFraction() {
        return filter == null ? 1 : Math.min(1, loaded / filter.capacity());
    }

    public boolean spent() {
        return filter == null || loaded >= filter.capacity();
    }

    /** Маска задержит эту примесь при такой доле кислорода. */
    public boolean protects(Contaminant c, double o2) {
        return filter != null && !spent() && filter.stops(c) && Ppe.maskWorks(c, o2);
    }

    /**
     * {@code dtS} секунд вдоха воздуха с примесью {@code c}: газ — в ppm, частицы — в мкг/м³.
     *
     * @return концентрация под маской в тех же единицах
     */
    public double inhale(double dtS, Contaminant c, double concentration, double o2) {
        if (concentration <= 0 || !protects(c, o2)) return concentration;
        double litres = Ppe.BREATHING_LPM * dtS / 60;
        loaded += c == Contaminant.PARTICLES ? concentration * 1e-3 * litres * 1e-3 // мкг/м³ × м³ → мг
                : concentration * 1e-6 * litres / FilterType.MOLAR_VOLUME_L;
        return concentration / Ppe.GAS_MASK_FACTOR;
    }
}

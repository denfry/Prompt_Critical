package dev.denfry.promptcritical.sim.neutronics;

/** Компоненты баланса реактивности (01 §1.7.1). */
public enum ReactivityComponent {
    /** ρ_nodal при опорных условиях последнего SLOW-шага (выгорание, Xe, Sm, бор, стержни, геометрия). */
    STATIC(true, false),
    RODS(true, true),
    DOPPLER(true, true),
    MODERATOR_TEMP(true, true),
    COOLANT_DENSITY(true, true),
    BORON(true, true),
    /** Только для отображения: вклад в STATIC, раскрытый отдельно (§1.8.5). */
    XENON(false, false),
    SAMARIUM(false, false),
    /** Эксперименты и тесты 04c; не сбрасывается на SLOW-шаге. */
    EXTERNAL(true, false);

    private final boolean summed, fast;

    ReactivityComponent(boolean summed, boolean fast) {
        this.summed = summed;
        this.fast = fast;
    }

    /** Входит в ρ_total. */
    public boolean summed() {
        return summed;
    }

    /** Квазистатическая поправка FAST-тика, обнуляемая при новом STATIC (§1.6.7). */
    public boolean fast() {
        return fast;
    }
}

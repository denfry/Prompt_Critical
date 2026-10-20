package dev.denfry.promptcritical.sim.metallurgy;

/**
 * Добавка на {@code ladle_station} (08 §8.6.2): основной элемент, его доля, углерод, усвоение. Усвоение Mn 85–95 %,
 * Si 70–85 %, Al 30–60 % — 08 §8.6.2 [сверить]; остальные — справочные порядки [сверить]. Основа — железо.
 */
public enum Ferroalloy {
    FERROMANGANESE("ferromanganese", AlloyElement.MN, 0.75, 0.07, 0.90),
    FERROSILICON("ferrosilicon", AlloyElement.SI, 0.75, 0, 0.78),
    ALUMINIUM("aluminium", AlloyElement.AL, 1.0, 0, 0.45),
    /** Низкоуглеродистый (для нержавеющих). */
    FERROCHROME("ferrochrome", AlloyElement.CR, 0.65, 0.001, 0.95),
    FERROMOLYBDENUM("ferromolybdenum", AlloyElement.MO, 0.60, 0, 0.97),
    FERROVANADIUM("ferrovanadium", AlloyElement.V, 0.50, 0, 0.90),
    FERRONIOBIUM("ferroniobium", AlloyElement.NB, 0.65, 0, 0.90),
    FERROTITANIUM("ferrotitanium", AlloyElement.TI, 0.70, 0, 0.50),
    NICKEL("nickel", AlloyElement.NI, 1.0, 0, 1.0),
    COBALT("cobalt", AlloyElement.CO, 1.0, 0, 1.0);

    public final String id;
    public final AlloyElement element;
    /** Доля элемента и углерода в добавке. */
    public final double content, carbon;
    /** Доля элемента, перешедшая в металл (остальное — в шлак). */
    public final double recovery;

    Ferroalloy(String id, AlloyElement element, double content, double carbon, double recovery) {
        this.id = id;
        this.element = element;
        this.content = content;
        this.carbon = carbon;
        this.recovery = recovery;
    }
}

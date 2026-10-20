package dev.denfry.promptcritical.sim.metallurgy;

/** Элемент химии стали (08 §8.6.4); {@link #symbol} — ключ карты {@code SteelGrade.chemPct}. */
public enum AlloyElement {
    C("C"), SI("Si"), MN("Mn"), P("P"), S("S"), O("O"), AL("Al"), CR("Cr"), NI("Ni"), MO("Mo"), V("V"), NB("Nb"),
    CU("Cu"), CO("Co"), TI("Ti");

    public final String symbol;

    AlloyElement(String symbol) {
        this.symbol = symbol;
    }

    /** Элемент по символу; {@code null}, если такого нет. */
    public static AlloyElement bySymbol(String symbol) {
        for (AlloyElement e : values()) if (e.symbol.equals(symbol)) return e;
        return null;
    }

    /** Легирующий (квантуется шагом 0,1 %, не больше 4 в марке): всё, кроме C, S, P, O. */
    public boolean alloying() {
        return this != C && this != S && this != P && this != O;
    }
}

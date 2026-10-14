package dev.denfry.promptcritical.sim.grid;

/** Устройство переключения отпаек (07 §7.4.3): отпайка k = 1 + step·stepPu на стороне ВН. */
public record TapChanger(int minStep, int maxStep, double stepPu, double stepTimeS, boolean onLoad) {
    public static final TapChanger NONE = new TapChanger(0, 0, 0, 0, false);

    public TapChanger {
        if (minStep > 0 || maxStep < 0) throw new IllegalArgumentException("steps must include 0");
        if (!(stepPu >= 0 && stepPu < 0.1)) throw new IllegalArgumentException("stepPu");
        if (!(stepTimeS >= 0)) throw new IllegalArgumentException("stepTimeS");
    }

    public double ratio(int step) {
        return 1 + step * stepPu;
    }
}

package dev.denfry.promptcritical.sim.battery;

import java.util.Objects;

/**
 * Головной шахтный светильник: щелочная батарея на поясе ({@link Battery}), фара с лампой накаливания на каске.
 *
 * <p>Лампа накаливания при отклонении напряжения: световой поток Φ = Φ₀·(U/U₀)^3,4, ток I = I₀·(U/U₀)^0,55 —
 * показатели General Electric, «Incandescent Lamps», TP-110 (поток ∝ U^3,38, ток ∝ U^0,54): нить холоднее —
 * сопротивление меньше, света много меньше. Ток и напряжение батареи согласуются итерацией U = V_бат(I),
 * I = I(U). К концу смены напряжение уходит с полки, и фара тускнеет: при 0,9 U₀ — 70 % света, при 0,8 U₀ — 47 %.
 *
 * <p>Исполнение рудничное взрывобезопасное: колба под толстым стеклом фары, батарея закрыта. Разбитое стекло
 * (падение, удар) обнажает колбу; горящая нить лопнувшей колбы в рудничном газе — источник воспламенения
 * ({@code IgnitionSource.LAMP}), после чего нить перегорает на воздухе и фара гаснет до ремонта в ламповой.
 *
 * @see BatterySpec#nickelCadmiumPocketPlate
 */
public final class CapLamp {
    /**
     * Фара: лампа 3,75 В 0,9 А под три элемента NiCd, 30 лм на выходе фары (БСЭ, «Светильник шахтный»: 30 лм,
     * не меньше 10 ч горения) [лампа восстановлена: ток выбран так, чтобы батарея 10 А·ч держала полку 10 ч].
     */
    public static final double BULB_VOLTS = 3.75, BULB_AMPS = 0.9, FLUX_LM = 30;
    /** Показатели лампы накаливания (GE TP-110). */
    public static final double FLUX_EXPONENT = 3.4, CURRENT_EXPONENT = 0.55;

    private final Battery battery;
    private boolean glassBroken, bulbBurnt;
    private double volts, amps, flux;

    public CapLamp(BatterySpec spec) {
        this.battery = new Battery(Objects.requireNonNull(spec, "spec"));
    }

    /** Светильник СГГ: три элемента НК по 10 А·ч, лампа 3,75 В 0,9 А. */
    public static CapLamp standard() {
        return new CapLamp(BatterySpec.nickelCadmiumPocketPlate(3, 10));
    }

    public Battery battery() {
        return battery;
    }

    /**
     * Горение (или хранение, если {@code lit} ложно) в течение {@code dtS} секунд при температуре воздуха.
     *
     * @return световой поток фары, лм
     */
    public double operate(double dtS, boolean lit, double ambientC) {
        double heat = 0;
        if (lit && !bulbBurnt) {
            solve();
            heat = battery.discharge(dtS, amps);
        } else {
            volts = battery.terminalVolts(0);
            amps = 0;
            flux = 0;
        }
        battery.selfDischarge(dtS);
        battery.heat(dtS, heat, ambientC);
        return flux;
    }

    /** Рабочая точка лампы на батарее: U = V(I), I = I₀·(U/U₀)^0,55. */
    private void solve() {
        double i = BULB_AMPS;
        double u = 0;
        for (int it = 0; it < 30; it++) {
            u = battery.terminalVolts(i);
            double next = BULB_AMPS * Math.pow(Math.max(u, 1e-3) / BULB_VOLTS, CURRENT_EXPONENT);
            if (Math.abs(next - i) < 1e-6) {
                i = next;
                break;
            }
            i = 0.5 * (i + next);
        }
        volts = battery.terminalVolts(i);
        amps = i;
        flux = FLUX_LM * Math.pow(volts / BULB_VOLTS, FLUX_EXPONENT);
    }

    /** Световой поток последнего шага, лм. */
    public double fluxLm() {
        return flux;
    }

    /** Поток относительно номинального (свежая лампа на номинальном напряжении). */
    public double relativeFlux() {
        return flux / FLUX_LM;
    }

    public double volts() {
        return volts;
    }

    public double amps() {
        return amps;
    }

    /** Защита цела: стекло фары и колба целы. */
    public boolean explosionProof() {
        return !glassBroken;
    }

    public boolean glassBroken() {
        return glassBroken;
    }

    public boolean bulbBurnt() {
        return bulbBurnt;
    }

    /**
     * Стекло фары разбито. Если лампа горела, нить её лопнувшей колбы на миг оказывается в рудничной атмосфере —
     * это источник воспламенения; затем нить перегорает.
     *
     * @return {@code true} — раскалённая нить вышла в атмосферу
     */
    public boolean breakGlass(boolean lit) {
        if (glassBroken) return false;
        glassBroken = true;
        boolean exposed = lit && !bulbBurnt && battery.terminalVolts(0) > 0.5 * BULB_VOLTS;
        bulbBurnt = true;
        flux = 0;
        amps = 0;
        return exposed;
    }

    /** Ремонт в ламповой: новое стекло и лампа. */
    public void repair() {
        glassBroken = false;
        bulbBurnt = false;
    }

    /** Восстановить из предмета. */
    public void restore(double removedAh, double cycles, double tempC, boolean glassBroken, boolean bulbBurnt) {
        battery.restore(removedAh, cycles, tempC);
        this.glassBroken = glassBroken;
        this.bulbBurnt = bulbBurnt;
    }
}

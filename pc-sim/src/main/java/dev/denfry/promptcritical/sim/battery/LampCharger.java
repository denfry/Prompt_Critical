package dev.denfry.promptcritical.sim.battery;

/**
 * Зарядное место ламповой: трансформатор и выпрямитель на одну батарею, заряд постоянным током C/4 до 1,55 В на
 * элемент, затем постоянным напряжением со спадающим током; заряд окончен, когда ток упал ниже C/50. Для
 * открытых щелочных батарей допустимы оба режима — ток C/4…C/5 6–7 ч (140 % отданного) или постоянное напряжение
 * 1,55–1,65 В/элемент (Linden, Reddy, «Handbook of Batteries», 2002, гл. 25).
 *
 * <p>Выпрямитель 1930–50-х (селеновый или купроксный) с трансформатором — КПД около 0,75 [оценка]. При просадке
 * сети ниже 0,85 о.е. ток постоянного тока снижается пропорционально напряжению, ниже 0,5 о.е. заряда нет.
 */
public final class LampCharger {
    public static final double CV_VOLTS_PER_CELL = 1.55, CC_C_RATE = 0.25, END_C_RATE = 0.02, EFFICIENCY = 0.75;
    static final double DERATE_FROM_PU = 0.85, DROPOUT_PU = 0.5;

    private LampCharger() {
    }

    /** Итог шага заряда одной батареи. */
    public record Step(double currentA, double dcPowerW, double acPowerW, double heatW, boolean full) {
        public static final Step IDLE = new Step(0, 0, 0, 0, false);
    }

    /**
     * Заряжать батарею {@code dtS} секунд от сети с напряжением {@code supplyPu}.
     *
     * @param ambientC температура в ламповой
     */
    public static Step charge(Battery b, double dtS, double supplyPu, double ambientC) {
        BatterySpec s = b.spec();
        if (supplyPu < DROPOUT_PU) {
            b.selfDischarge(dtS);
            b.heat(dtS, 0, ambientC);
            return Step.IDLE;
        }
        double derate = Math.min(1, supplyPu / DERATE_FROM_PU);
        double cc = CC_C_RATE * s.ratedAh() * derate;
        double cvVolts = CV_VOLTS_PER_CELL * s.cells();
        double i = Math.min(cc, b.chargeCurrentAt(cvVolts));
        boolean full = i < END_C_RATE * s.ratedAh();
        if (full) {
            b.selfDischarge(dtS);
            b.heat(dtS, 0, ambientC);
            return new Step(0, 0, 0, 0, true);
        }
        double volts = b.terminalVolts(-i);
        double heatJ = b.charge(dtS, i);
        b.heat(dtS, heatJ, ambientC);
        double dc = volts * i, ac = dc / EFFICIENCY;
        return new Step(i, dc, ac, ac - dc + heatJ / dtS, false);
    }
}

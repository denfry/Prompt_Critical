package dev.denfry.promptcritical.sim.gear;

import dev.denfry.promptcritical.sim.battery.Battery;
import dev.denfry.promptcritical.sim.battery.BatterySpec;
import java.util.Objects;

/**
 * Аккумулятор носимого снаряжения ({@link Battery}) под нагрузкой постоянной мощности: двигатель вентилятора или
 * нагреватель через регулятор берут мощность P, ток I = P/U согласуется с напряжением батареи U = V(I). Ниже
 * {@link #CUTOFF_PER_CELL} на элемент регулятор отключает нагрузку (защита от глубокого разряда и переполюсовки
 * элементов). Отдельной «энергии снаряжения» нет (§15.8): запас — отданный заряд модели Tremblay.
 */
public final class PowerPack {
    /** Конечное напряжение разряда NiCd, В на элемент (Linden &amp; Reddy, гл. 25). */
    public static final double CUTOFF_PER_CELL = 1.0;

    private final Battery battery;
    private double volts, amps, powerW;

    public PowerPack(BatterySpec spec) {
        this.battery = new Battery(Objects.requireNonNull(spec, "spec"));
    }

    public Battery battery() {
        return battery;
    }

    /** Энергия батареи при номинальном напряжении с учётом износа, Вт·ч (для подписи). */
    public double ratedWh() {
        return battery.spec().nominalVolts() * battery.spec().ratedAh() * battery.health();
    }

    /**
     * Питать нагрузку {@code watts} в течение {@code dtS} секунд при температуре воздуха {@code ambientC}.
     *
     * @return отданная мощность, Вт: {@code watts} или 0, если батарея села
     */
    public double supply(double dtS, double watts, double ambientC) {
        double heat = 0;
        powerW = 0;
        amps = 0;
        if (watts > 0) {
            double cutoff = CUTOFF_PER_CELL * battery.spec().cells();
            double i = watts / battery.spec().nominalVolts();
            double u = battery.terminalVolts(i);
            for (int it = 0; it < 20 && u > cutoff; it++) {
                double next = watts / u;
                if (Math.abs(next - i) < 1e-6) break;
                i = next;
                u = battery.terminalVolts(i);
            }
            if (u > cutoff) {
                heat = battery.discharge(dtS, i);
                amps = i;
                powerW = watts;
            }
        }
        volts = battery.terminalVolts(amps);
        battery.selfDischarge(dtS);
        battery.heat(dtS, heat, ambientC);
        return powerW;
    }

    public double volts() {
        return volts;
    }

    public double amps() {
        return amps;
    }

    /** Мощность последнего шага, Вт. */
    public double powerW() {
        return powerW;
    }
}

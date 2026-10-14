package dev.denfry.promptcritical.sim.grid;

/** Классы напряжения ГОСТ 721-77 — лестница прогрессии (07 §7.0.3, §7.4.8). Базисное напряжение класса — номинал. */
public enum VoltageClass {
    LV_0_4(400), MV_6_3(6300), MV_10_5(10500), MV_35(35000), HV_110(110000), HV_220(220000);

    public final double volts;

    VoltageClass(double v) {
        volts = v;
    }
}

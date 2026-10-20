package dev.denfry.promptcritical.sim.automation;

/**
 * Исполнение решающего усилителя A2 (03d §3d.4.2): допуск δ_g, начальное смещение e_os, σ шума AR(1),
 * погрешность множителя δ_m, тепловыделение блока.
 */
public enum AnalogTech {
    TUBE(0.005, 0.002, 0.010, 0.005, 25),
    TRANSISTOR(0.001, 0.0005, 0.002, 0.0025, 3);

    public final double gainTolerance;
    public final double offsetV;
    public final double noiseSigmaV;
    public final double multiplierError;
    public final double heatW;

    AnalogTech(double gainTolerance, double offsetV, double noiseSigmaV, double multiplierError, double heatW) {
        this.gainTolerance = gainTolerance;
        this.offsetV = offsetV;
        this.noiseSigmaV = noiseSigmaV;
        this.multiplierError = multiplierError;
        this.heatW = heatW;
    }
}

package dev.denfry.promptcritical.sim.metallurgy;

/**
 * Компонент {@code electrode_state} (08 §8.4.5, §8.17): размеры заготовки, трещина и стадия передела.
 *
 * @param stage {@link #GREEN} → {@link #BAKED} → {@link #GRAPHITIZED} (Ачесон — M4)
 */
public record ElectrodeState(double lengthM, double diameterM, boolean cracked, int stage) {
    public static final int GREEN = 0, BAKED = 1, GRAPHITIZED = 2;

    public ElectrodeState {
        if (!(lengthM > 0 && diameterM > 0)) throw new IllegalArgumentException("electrode size");
        if (stage < GREEN || stage > GRAPHITIZED) throw new IllegalArgumentException("stage: " + stage);
    }

    public ElectrodeState withCracked() {
        return new ElectrodeState(lengthM, diameterM, true, stage);
    }

    public ElectrodeState withStage(int s) {
        return new ElectrodeState(lengthM, diameterM, cracked, s);
    }
}

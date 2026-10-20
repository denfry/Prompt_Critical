package dev.denfry.promptcritical.sim.metallurgy;

/**
 * Формование электродов {@code electrode_extruder} (08 §8.4.5): наполнитель ({@code pitch_coke}, позже
 * {@code needle_coke}/{@code petroleum_coke} 09) 70–75 % + пек 25–30 % при 150–170 °C → {@code green_electrode}.
 * Холоднее 150 °C масса не течёт; связующее вне 25–30 % — заготовка с трещиной (тощая крошится, жирная
 * вспучивается при обжиге) [допущение: дефект виден сразу].
 */
public final class ElectrodeExtruder {
    public static final double PITCH_MIN = 0.25, PITCH_MAX = 0.30;
    public static final double MIN_PASTE_K = 423.15, MAX_PASTE_K = 443.15;
    /** Плотность сырой заготовки, кг/м³ [сверить]. */
    public static final double GREEN_DENSITY = 1_650;

    private ElectrodeExtruder() {}

    /**
     * @param pasteK температура массы, К; выше 170 °C пек течёт из мундштука — тоже трещина
     * @return заготовка; {@code null} — масса холодная или пустая
     */
    public static ElectrodeState extrude(double fillerKg, double pitchKg, double pasteK, double diameterM) {
        double m = fillerKg + pitchKg;
        if (!(fillerKg > 0 && pitchKg > 0 && diameterM > 0) || pasteK < MIN_PASTE_K) return null;
        double share = pitchKg / m;
        boolean bad = share < PITCH_MIN || share > PITCH_MAX || pasteK > MAX_PASTE_K;
        double length = m / GREEN_DENSITY / (Math.PI * diameterM * diameterM / 4);
        return new ElectrodeState(length, diameterM, bad, ElectrodeState.GREEN);
    }
}

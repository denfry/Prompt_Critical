package dev.denfry.promptcritical.sim.metallurgy;

/**
 * Геометрия домны от валидатора мультиблока (08 §8.5.2).
 *
 * @param usefulVolumeM3 полезный объём V_u, м³ (игровые 30–300)
 * @param tuyeres        фурмы, 4–12
 * @param hearthAreaM2   площадь горна, м²
 * @param tapholeHeightM лётка над лещадью, м
 * @param tuyereLevelM   ось фурм над лещадью, м
 * @param ratedBlastNm3S номинальное дутьё, нм³/с физ.
 */
public record BlastFurnaceSpec(double usefulVolumeM3, int tuyeres, double hearthAreaM2, double tapholeHeightM,
        double tuyereLevelM, double ratedBlastNm3S) {
    /** Номинальная производительность и удельное дутьё, по которым считается {@code ratedBlastNm3S}. */
    public static final double NOMINAL_T_M3_DAY = 2.0, NOMINAL_BLAST_NM3_T = 1100;

    public BlastFurnaceSpec {
        if (!(usefulVolumeM3 >= 30 && usefulVolumeM3 <= 300)) throw new IllegalArgumentException("usefulVolumeM3");
        if (tuyeres < 4 || tuyeres > 12) throw new IllegalArgumentException("tuyeres");
        if (!(hearthAreaM2 > 0 && tapholeHeightM >= 0 && tuyereLevelM > tapholeHeightM + 0.5 && ratedBlastNm3S > 0))
            throw new IllegalArgumentException("hearth");
    }

    /**
     * Типовые пропорции: горн 0,09·V_u м² (V_u 54 → ⌀ 2,5 м), лётка 0,3 м, фурмы 1,8 м — между ними ≈ 2,5
     * межвыпускных периода жидкого при номинале [BALANCE].
     */
    public static BlastFurnaceSpec of(double usefulVolumeM3, int tuyeres) {
        return new BlastFurnaceSpec(usefulVolumeM3, tuyeres, 0.09 * usefulVolumeM3, 0.3, 1.8,
                usefulVolumeM3 * NOMINAL_T_M3_DAY * NOMINAL_BLAST_NM3_T / 86_400);
    }
}

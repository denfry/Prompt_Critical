package dev.denfry.promptcritical.sim.thermo;

/**
 * Формат ресурса {@code if97_tables.bin} (01 §1.3.2). Числа — big-endian
 * ({@link java.io.DataOutputStream}), в конце — CRC32 всего предыдущего.
 *
 * <pre>
 * int MAGIC, int VERSION
 * насыщение:  int N, double lnPmin, double lnPmax,
 *             double[SAT_Q][N] значения, double[SAT_Q][N] производные по ln p
 * (p, h):     int NC, double lnPmin, double dlnP, int[NC] первая строка грубого узла,
 *             int NH, double hMin, double dh,
 *             по узлам [строки][NH]: double[4] группа v, float[PH_Q − 4] остальное
 *             (строки равномерны по ln p внутри грубой ячейки; у p_c ячейки поделены)
 * граница купола: int NR, double lnRhoMin, double dlnRho, double[NR] u_b
 * int CRC32
 * </pre>
 *
 * Узлы насыщения сгущаются к критической точке: ln p_i = a + (b − a)·ξ_i,
 * ξ = 1 − (1 − s)², s = i/(N − 1).
 */
public final class WaterTableFormat {
    public static final String RESOURCE = "/data/promptcritical/physics/if97_tables.bin";
    public static final int MAGIC = 0x50435754; // "PCWT"
    public static final int VERSION = 1;

    /** Величины линии насыщения — в порядке полей {@link SaturationState} после давления. */
    public static final int SAT_T = 0, SAT_VF = 1, SAT_VG = 2, SAT_HF = 3, SAT_HG = 4, SAT_UF = 5, SAT_UG = 6,
            SAT_SF = 7, SAT_SG = 8, SAT_CPF = 9, SAT_CPG = 10, SAT_MUF = 11, SAT_MUG = 12, SAT_KF = 13,
            SAT_KG = 14, SAT_SIGMA = 15, SAT_DTDP = 16;
    public static final int SAT_Q = 17;

    /**
     * Узел таблицы (p, h): v, T, s, c_p — значение и производные ∂/∂ln p, ∂/∂h,
     * ∂²/∂ln p∂h для бикубической интерполяции Эрмита (NaN в производных c_p —
     * эталон в окрестности не определён); μ, k — только значения.
     */
    public static final int PH_V = 0, PH_T = 4, PH_S = 8, PH_CP = 12, PH_MU = 16, PH_K = 17;
    public static final int PH_Q = 18;

    private WaterTableFormat() {}
}

package dev.denfry.promptcritical.sim.thermo;

/**
 * Формат ресурса {@code critical_flow.bin} (01 §1.3.9). Числа — big-endian
 * ({@link java.io.DataOutputStream}), в конце — CRC32 всего предыдущего.
 *
 * <pre>
 * int MAGIC, int VERSION
 * int NP, double lnPmin, double lnPmax      — строки равномерно по ln p₀
 * double hMin, double hMax
 * int[SEGMENTS] число интервалов по h₀ в сегментах недогрев | смесь | перегрев
 * double[NP] h_f(p₀), double[NP] h_g(p₀)   — границы сегментов в строке
 * int MODELS                                — в порядке {@link CriticalFlowTable.Model}
 * по моделям: float[NP][NH] G_c², (кг/(м²·с))², NH = Σ интервалов + 1
 * int CRC32
 * </pre>
 *
 * В строке p₀ узлы по h₀ лежат на отрезках [hMin, h_f], [h_f, h_g], [h_g, hMax]:
 * излом G_c на линии насыщения ложится на узлы. С обеих сторон h_f узлы сгущены
 * квадратично, t = j/K в сегменте: в недогреве h = h_f − (h_f − hMin)(1 − t)², в смеси
 * h = h_f + (h_g − h_f)t², в перегреве равномерно. У слабого недогрева максимум G_c
 * перескакивает с ветви Бернулли (горло на линии вскипания) на двухфазную — излом
 * внутри сегмента; у малых x G_c быстро падает с паросодержанием. Хранится G_c², а не G_c:
 * у недогретой жидкости G_c ≈ √(2ρ(p₀ − p_s(T₀))) с корневой особенностью у h_f,
 * а G_c² там почти линеен.
 */
public final class CriticalFlowFormat {
    public static final String RESOURCE = "/data/promptcritical/physics/critical_flow.bin";
    public static final int MAGIC = 0x50434346; // "PCCF"
    public static final int VERSION = 1;
    public static final int SEGMENTS = 3;

    private CriticalFlowFormat() {}
}

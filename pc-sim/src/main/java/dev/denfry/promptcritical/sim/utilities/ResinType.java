package dev.denfry.promptcritical.sim.utilities;

/**
 * Ионообменная смола (08 §8.12). В M3 — только сильнокислотный катионит в двух формах; анионит {@code SBA_OH} и ФСД
 * {@code MIXED_BED} (обессоливание на NaOH) — с M9.
 */
public enum ResinType {
    /** Катионит в H-форме: берёт все катионы, отдаёт H⁺ (бикарбонаты → CO₂, остальное — минеральная кислота). */
    SAC_H(0.100),
    /** Катионит в Na-форме: берёт Ca²⁺/Mg²⁺, отдаёт Na⁺ — умягчение без изменения солесодержания. */
    SAC_NA(0.150);

    /** Расход реагента регенерации, кг на моль-экв поглощённого: H₂SO₄ 70–120 г/г-экв, NaCl 100–200 [сверить]. */
    public final double regenerantKgPerEq;

    ResinType(double regenerantKgPerEq) {
        this.regenerantKgPerEq = regenerantKgPerEq;
    }

    /** Предмет-реагент регенерации (02). */
    public String regenerantId() {
        return this == SAC_H ? "sulfuric_acid" : "salt";
    }
}

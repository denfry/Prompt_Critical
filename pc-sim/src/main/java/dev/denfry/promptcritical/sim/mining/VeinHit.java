package dev.denfry.promptcritical.sim.mining;

/**
 * Рудный блок жилы (08 §8.1.1).
 *
 * @param body  тело
 * @param role  роль блока
 * @param grade закон содержания; {@code null} у угля (он несёт {@code coal_assay})
 * @param h     относительная высота в теле (у свиты — 0,5)
 */
public record VeinHit(VeinBody body, OreRole role, OreGrade grade, double h) {
    /** Блок угольного пласта. */
    public boolean coal() {
        return grade == null;
    }

    /** ID блока руды. */
    public String mineral() {
        return grade == null ? VeinField.COAL_BLOCK : grade.mineral();
    }

    public String veinId() {
        return body.type().id();
    }
}

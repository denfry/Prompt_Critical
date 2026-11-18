package dev.denfry.promptcritical.sim.acoustics;

/**
 * Геометрия подшипника качения и его дефектные частоты (04a §4a.6.1). Формулы кинематики тел качения без
 * проскальзывания — Harris T. A., Kotzalas M. N., «Essential Concepts of Bearing Technology», 5th ed., §8; McFadden P. D.,
 * Smith J. D., «Model for the vibration produced by a single point defect in a rolling element bearing», J. Sound Vib.
 * 96 (1984).
 *
 * @param rollingElements число тел качения n
 * @param elementDiameter диаметр тела качения d, м
 * @param pitchDiameter   диаметр окружности центров D, м
 * @param contactAngleRad угол контакта φ, рад
 */
public record BearingGeometry(int rollingElements, double elementDiameter, double pitchDiameter, double contactAngleRad) {
    /**
     * Шариковый 6205-2RS (SKF) — подшипник приводного конца стенда Case Western Reserve University Bearing Data
     * Center: 9 шариков 7,94 мм на окружности 39,04 мм. Частоты на 1 Гц вала: BPFO 3,585, BPFI 5,415, FTF 0,398.
     */
    public static final BearingGeometry SKF_6205 = new BearingGeometry(9, 7.94e-3, 39.04e-3, 0);
    /** Пример 04a §4a.6.1 (крупная машина): 10 тел, d/D = 0,2 — BPFO 4,0·f_r, BPFI 6,0·f_r. */
    public static final BearingGeometry LARGE = new BearingGeometry(10, 0.02, 0.1, 0);

    public BearingGeometry {
        if (rollingElements < 3 || !(elementDiameter > 0) || !(pitchDiameter > elementDiameter)
                || !(contactAngleRad >= 0 && contactAngleRad < Math.PI / 2))
            throw new IllegalArgumentException("bearing geometry");
    }

    private double k() {
        return elementDiameter / pitchDiameter * Math.cos(contactAngleRad);
    }

    /** Частота перекатывания тел по наружному кольцу, Гц. */
    public double bpfo(double fr) {
        return rollingElements / 2.0 * fr * (1 - k());
    }

    /** По внутреннему кольцу, Гц. */
    public double bpfi(double fr) {
        return rollingElements / 2.0 * fr * (1 + k());
    }

    /** Вращения тела качения вокруг своей оси, Гц. */
    public double bsf(double fr) {
        double k = k();
        return pitchDiameter / (2 * elementDiameter) * fr * (1 - k * k);
    }

    /** Сепаратора, Гц. */
    public double ftf(double fr) {
        return fr / 2 * (1 - k());
    }
}

package dev.denfry.promptcritical.sim.protection;

/**
 * Проектная модель горячего канала, зашитая в вычислитель DNBR (01 §1.11.4).
 *
 * @param nominalPower            тепловая мощность зоны при N = 1, Вт
 * @param nominalMassFlow         расход через зону при номинале, кг/с
 * @param flowArea                проходное сечение зоны, м²
 * @param hydraulicDiameter       D_e, м
 * @param averageHeatFlux         средний тепловой поток с оболочек при N = 1, Вт/м²
 * @param enthalpyRiseFactor      консервативный F_ΔH
 * @param conservativeAxialPeaking F_z, если внутризонных детекторов нет (1,55)
 * @param axialPoints             точек по высоте ({@code thermal.dnbr_axial_points})
 */
public record HotChannelModel(double nominalPower, double nominalMassFlow, double flowArea, double hydraulicDiameter,
        double averageHeatFlux, double enthalpyRiseFactor, double conservativeAxialPeaking, int axialPoints) {
    /** Наибольший F_z косинусной формы с неотрицательным потоком на торцах. */
    public static final double MAX_AXIAL_PEAKING = Math.PI / 2;

    public HotChannelModel {
        if (!(nominalPower > 0)) throw new IllegalArgumentException("nominalPower");
        if (!(nominalMassFlow > 0)) throw new IllegalArgumentException("nominalMassFlow");
        if (!(flowArea > 0)) throw new IllegalArgumentException("flowArea");
        if (!(hydraulicDiameter > 0)) throw new IllegalArgumentException("hydraulicDiameter");
        if (!(averageHeatFlux > 0)) throw new IllegalArgumentException("averageHeatFlux");
        if (!(enthalpyRiseFactor >= 1)) throw new IllegalArgumentException("enthalpyRiseFactor");
        if (!(conservativeAxialPeaking >= 1 && conservativeAxialPeaking <= MAX_AXIAL_PEAKING))
            throw new IllegalArgumentException("conservativeAxialPeaking");
        if (axialPoints < 1) throw new IllegalArgumentException("axialPoints");
    }

    /** Номинальная массовая скорость, кг/(м²·с). */
    public double nominalMassFlux() {
        return nominalMassFlow / flowArea;
    }
}

package dev.denfry.promptcritical.sim.manufacturing;

/**
 * Пластина статора (18 §18.6.8): наружный и внутренний диаметры, м, число пазов и прямоугольный паз ширины и глубины,
 * м, открытый в расточку.
 */
public record LaminationShape(String id, double outerM, double innerM, int slots, double slotWidthM, double slotDepthM) {
    /**
     * Статор асинхронного двигателя 5,5 кВт, 4 полюса, 50 Гц (габарит 112, серия 4А): Ø191 / Ø125 мм, 36 пазов
     * 8 × 20 мм [сверить по справочнику Кравчика «Асинхронные двигатели серии 4А», 1982].
     */
    public static final LaminationShape STATOR_112 = new LaminationShape("stator_112", 0.191, 0.125, 36, 0.008, 0.020);

    public LaminationShape {
        if (!(outerM > innerM) || !(innerM > 0) || slots < 1 || !(slotWidthM > 0) || !(slotDepthM > 0)
                || innerM + 2 * slotDepthM >= outerM || slots * slotWidthM >= Math.PI * innerM) {
            throw new IllegalArgumentException("lamination geometry is not consistent");
        }
    }

    /** Длина реза при вырубке целиком, м: обе окружности и стенки пазов. */
    public double cutLengthM() {
        return Math.PI * (outerM + innerM) + slots * 2 * slotDepthM;
    }

    /** Контур одного паза при пазовой штамповке, м. */
    public double slotCutM() {
        return 2 * slotDepthM + slotWidthM;
    }

    /** Площадь пластины, м². */
    public double areaM2() {
        return Math.PI / 4 * (outerM * outerM - innerM * innerM) - slots * slotWidthM * slotDepthM;
    }

    /** Площадь паза, м². */
    public double slotAreaM2() {
        return slotWidthM * slotDepthM;
    }

    /** Пластин из стандартного листа: по квадрату со стороной наружного диаметра и перемычкой 5 мм [моё]. */
    public int perSheet() {
        double pitch = outerM + 0.005;
        return (int) Math.floor(ElectricalSheet.SHEET_LENGTH_M / pitch) * (int) Math.floor(ElectricalSheet.SHEET_WIDTH_M / pitch);
    }
}

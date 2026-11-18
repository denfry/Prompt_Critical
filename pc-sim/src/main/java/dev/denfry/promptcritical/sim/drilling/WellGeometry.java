package dev.denfry.promptcritical.sim.drilling;

/**
 * Конструкция вертикальной скважины и бурильной колонны.
 *
 * @param holeDiameterM      диаметр ствола (долота), м
 * @param pipeOuterM         наружный диаметр бурильных труб, м
 * @param pipeInnerM         внутренний диаметр бурильных труб, м
 * @param pipeKgPerM         погонная масса труб с замками, кг/м
 * @param bhaLengthM         длина УБТ (компоновки низа), м
 * @param bhaKgPerM          погонная масса УБТ, кг/м
 * @param nozzleAreaM2       суммарная площадь насадок долота, м²
 * @param shoeDepthM         глубина башмака последней обсадной колонны, м
 * @param shoeFractureKgM3   эквивалентная плотность давления гидроразрыва у башмака (опрессовка LOT), кг/м³
 * @param plasticViscosityPaS пластическая вязкость раствора, Па·с
 * @param yieldPointPa       динамическое напряжение сдвига, Па
 */
public record WellGeometry(double holeDiameterM, double pipeOuterM, double pipeInnerM, double pipeKgPerM,
        double bhaLengthM, double bhaKgPerM, double nozzleAreaM2, double shoeDepthM, double shoeFractureKgM3,
        double plasticViscosityPaS, double yieldPointPa) {
    private static final double IN = 0.0254;

    /**
     * Долото 8½″ под кондуктором на 600 м (давление гидроразрыва у башмака 1,70 г/см³), трубы 5″ 19,5 фунт/фут
     * (внутр. 4,276″, с замками ≈ 32,6 кг/м), 180 м УБТ 6½″ × 2 13/16″ (136 кг/м), насадки 3 × 16/32″ (0,589 дюйм²),
     * раствор μₚ = 20 мПа·с, τ₀ = 15 фунт/100 фут² (7,2 Па) — типичная компоновка из Bourgoyne et al., гл. 4 и
     * API RP 7G.
     */
    public static final WellGeometry STANDARD = new WellGeometry(8.5 * IN, 5 * IN, 4.276 * IN, 32.6, 180, 136,
            0.589 * IN * IN, 600, 1700, 0.020, 7.18);

    public WellGeometry {
        if (!(holeDiameterM > pipeOuterM && pipeOuterM > pipeInnerM && pipeInnerM > 0 && nozzleAreaM2 > 0 && shoeDepthM > 0))
            throw new IllegalArgumentException("inconsistent well geometry");
    }

    /** Площадь кольцевого пространства, м². */
    public double annulusAreaM2() {
        return Math.PI / 4 * (holeDiameterM * holeDiameterM - pipeOuterM * pipeOuterM);
    }

    /** Площадь канала труб, м². */
    public double innerAreaM2() {
        return Math.PI / 4 * pipeInnerM * pipeInnerM;
    }
}

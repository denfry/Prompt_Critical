package dev.denfry.promptcritical.sim.production;

import dev.denfry.promptcritical.sim.drilling.NaturalGas;

/**
 * Горизонтальный трёхфазный сепаратор первой ступени (Arnold &amp; Stewart, «Surface Production Operations», т. 1,
 * гл. 4–5; API Spec 12J): жидкость занимает половину сечения, нефтяная подушка — верхнюю половину жидкости,
 * водяная — нижнюю.
 *
 * <p>Газ: допустимая скорость в паровом пространстве по Саудерсу–Брауну v = K·√((ρ_ж − ρ_г)/ρ_г), K = 0,12 м/с
 * (0,4 фут/с, API 12J для горизонтальных аппаратов); при превышении газ уносит капельную жидкость.
 *
 * <p>Отстой: капля оседает со скоростью Стокса v = gd²Δρ/(18μ). За время пребывания t = V/q через слой высоты h
 * успевают выпасть капли крупнее d_c = √(18μh/(gΔρt)); мельче — уходят с другой фазой. Распределение капель —
 * Розина–Раммлера, F(d) = 1 − exp(−(d/d₆₃)ⁿ) по объёму: вода в нефти d₆₃ = 400 мкм, нефть в воде d₆₃ = 200 мкм, n = 2,5
 * [допущение: типичная дисперсия после штуцера, Arnold &amp; Stewart, гл. 4].
 *
 * @param diameterM  внутренний диаметр, м
 * @param lengthM    длина между днищами, м
 * @param pressurePa рабочее давление (избыточное), Па
 */
public record ThreePhaseSeparator(double diameterM, double lengthM, double pressurePa) {
    public static final double G = 9.806_65, SOUDERS_BROWN_K = 0.12, TEMPERATURE_K = 310;
    public static final double WATER_IN_OIL_D63 = 400e-6, OIL_IN_WATER_D63 = 200e-6, SPREAD = 2.5;
    public static final double OIL_VISCOSITY = 5e-3, WATER_VISCOSITY = 7e-4;
    /** Сепаратор 1200 × 4000 мм на 0,6 МПа. */
    public static final ThreePhaseSeparator STANDARD = new ThreePhaseSeparator(1.2, 4.0, 0.6e6);

    /**
     * Результат разделения: объёмные расходы, м³/с (газ — н. у.), доля воды в нефти, доля нефти в воде, доля
     * жидкости, унесённой газом, время пребывания нефти и воды, с.
     */
    public record Result(double oilM3s, double waterM3s, double gasStdM3s, double waterInOil, double oilInWater,
            double liquidCarryover, double oilRetentionS, double waterRetentionS) {
    }

    /** Объёмная доля капель мельче d в распределении Розина–Раммлера. */
    public static double finerThan(double d, double d63) {
        return 1 - Math.exp(-Math.pow(d / d63, SPREAD));
    }

    /** Скорость осаждения капли по Стоксу, м/с. */
    public static double stokesMs(double diameterM, double deltaRho, double viscosityPaS) {
        return G * diameterM * diameterM * Math.abs(deltaRho) / (18 * viscosityPaS);
    }

    /** Наименьшая капля, успевающая пройти слой h за время t, м. */
    public static double cutDiameterM(double layerM, double timeS, double deltaRho, double viscosityPaS) {
        if (timeS <= 0) return Double.POSITIVE_INFINITY;
        return Math.sqrt(18 * viscosityPaS * layerM / (G * Math.abs(deltaRho) * timeS));
    }

    /** Разделить поток: нефть и вода, м³/с, газ — нормальные м³/с. */
    public Result separate(double oilM3s, double waterM3s, double gasStdM3s, double oilKgM3) {
        double area = Math.PI / 4 * diameterM * diameterM;
        double liquidVol = area / 2 * lengthM, padVol = liquidVol / 2, pad = diameterM / 4;
        double dRho = ProducingReservoir.WATER_KGM3 - oilKgM3;
        double tOil = oilM3s > 0 ? padVol / oilM3s : Double.POSITIVE_INFINITY;
        double tWater = waterM3s > 0 ? padVol / waterM3s : Double.POSITIVE_INFINITY;
        double wInO = finerThan(cutDiameterM(pad, tOil, dRho, OIL_VISCOSITY), WATER_IN_OIL_D63);
        double oInW = finerThan(cutDiameterM(pad, tWater, dRho, WATER_VISCOSITY), OIL_IN_WATER_D63);
        // газ: скорость в верхней половине сечения против допустимой
        double pabs = pressurePa + NaturalGas.STD_PRESSURE_PA;
        double rhoG = NaturalGas.densityKgM3(pabs, TEMPERATURE_K);
        double qGas = NaturalGas.volumeM3(NaturalGas.moles(gasStdM3s, NaturalGas.STD_PRESSURE_PA, NaturalGas.STD_TEMPERATURE_K), pabs,
                TEMPERATURE_K);
        double vMax = SOUDERS_BROWN_K * Math.sqrt((oilKgM3 - rhoG) / rhoG);
        double v = qGas / (area / 2);
        double carry = v > vMax ? Math.min(0.5, 0.05 * (v / vMax - 1)) : 0;
        double waterToOil = waterM3s * wInO, oilToWater = oilM3s * oInW;
        double oilOut = (oilM3s - oilToWater + waterToOil) * (1 - carry);
        double waterOut = (waterM3s - waterToOil + oilToWater) * (1 - carry);
        double oilStream = oilM3s - oilToWater + waterToOil;
        return new Result(oilOut, waterOut, gasStdM3s, oilStream > 0 ? waterToOil / oilStream : 0,
                waterM3s - waterToOil + oilToWater > 0 ? oilToWater / (waterM3s - waterToOil + oilToWater) : 0, carry, tOil, tWater);
    }
}

package dev.denfry.promptcritical.sim.drilling;

/**
 * Антиклинальная ловушка: купол кровли пласта-коллектора под глинистой покрышкой (Selley, Sonnenberg, «Elements of
 * Petroleum Geology», 3-е изд., гл. 7). Кровля — параболоид: на своде глубина {@code crestDepthM}, на радиусе
 * {@code radiusM} — на {@code closureM} глубже (точка перелива), дальше крыло уходит вниз с уклоном
 * {@link #FLANK_DIP}. Углеводороды заполняют купол сверху до контакта {@code contactDepthM} (ГВК или ВНК), ниже —
 * вода. Одна клетка карты — один метр по горизонтали.
 *
 * <p>Давление: водоносный горизонт ловушки имеет градиент {@code gradientPaM} (гидростатический 10,2 кПа/м или
 * аномально высокое пластовое давление, АВПД); в залежи над контактом давление падает вверх по колонне лёгкого флюида —
 * поэтому на своде газовой залежи эквивалентная плотность давления выше, чем в воде (Dake, «Fundamentals of
 * Reservoir Engineering», гл. 1.5).
 *
 * @param crestX         свод, блок X
 * @param crestZ         свод, блок Z
 * @param radiusM        радиус замкнутого контура, м
 * @param crestDepthM    глубина кровли на своде, м
 * @param closureM       амплитуда замыкания (свод — точка перелива), м
 * @param contactDepthM  глубина контакта углеводороды/вода, м; у пустой (водоносной) ловушки — NaN
 * @param thicknessM     мощность коллектора, м
 * @param porosity       пористость, доли
 * @param permeabilityMd проницаемость, мД
 * @param fluid          флюид залежи; {@link FormationFluid#WATER} — покрышка нарушена, ловушка пуста
 * @param gradientPaM    градиент давления в водоносной части, Па/м
 */
public record Trap(int crestX, int crestZ, double radiusM, double crestDepthM, double closureM, double contactDepthM,
        double thicknessM, double porosity, double permeabilityMd, FormationFluid fluid, double gradientPaM) {
    /** Уклон крыла за точкой перелива, м/м (≈ 5°). */
    public static final double FLANK_DIP = 0.087;
    /** Средний геотермический градиент, К/м, и температура у поверхности, К. */
    public static final double GEOTHERM_K_PER_M = 0.030, SURFACE_K = 288.15;
    public static final double G = 9.806_65;
    /** 1 мД = 9,869 233·10⁻¹⁶ м². */
    public static final double M2_PER_MD = 9.869_233e-16;

    public Trap {
        if (!(radiusM > 0 && crestDepthM > 0 && closureM > 0 && thicknessM > 0 && porosity > 0 && porosity < 1
                && permeabilityMd > 0 && gradientPaM > 0))
            throw new IllegalArgumentException("trap parameters must be positive");
        if (fluid.hydrocarbon() && !(contactDepthM > crestDepthM))
            throw new IllegalArgumentException("contact must lie below the crest");
    }

    /** Глубина кровли коллектора в точке (x, z), м. */
    public double topDepthM(double x, double z) {
        double r = Math.hypot(x - crestX, z - crestZ);
        if (r <= radiusM) return crestDepthM + closureM * (r / radiusM) * (r / radiusM);
        return crestDepthM + closureM + FLANK_DIP * (r - radiusM);
    }

    /** Флюид на глубине внутри коллектора. */
    public FormationFluid fluidAt(double depthM) {
        return fluid.hydrocarbon() && depthM < contactDepthM ? fluid : FormationFluid.WATER;
    }

    /** Пластовое давление (избыточное) на глубине, Па. */
    public double pressureAt(double depthM) {
        if (!fluid.hydrocarbon() || depthM >= contactDepthM) return gradientPaM * depthM;
        double pc = gradientPaM * contactDepthM;
        double rho = fluid.densityKgM3(pc + NaturalGas.STD_PRESSURE_PA, temperatureK(contactDepthM));
        return pc - rho * G * (contactDepthM - depthM);
    }

    /** Эквивалентная плотность пластового давления, кг/м³. */
    public double equivalentDensityKgM3(double depthM) {
        return pressureAt(depthM) / (G * depthM);
    }

    /** Проницаемость, м². */
    public double permeabilityM2() {
        return permeabilityMd * M2_PER_MD;
    }

    public static double temperatureK(double depthM) {
        return SURFACE_K + GEOTHERM_K_PER_M * Math.max(0, depthM);
    }
}

package dev.denfry.promptcritical.sim.production;

import dev.denfry.promptcritical.sim.drilling.FormationFluid;
import dev.denfry.promptcritical.sim.drilling.Trap;

/**
 * Нефтяная залежь под скважиной как резервуар-«бак» (материальный баланс нулевой размерности; Dake, «Fundamentals of
 * Reservoir Engineering», гл. 3): пластовое давление падает с отбором, Δp = ΔV_пл / (c_эф·V_пор·(1 + W)), где W —
 * отношение объёма законтурной воды к объёму залежи (упругий водонапорный режим), c_эф — эффективная сжимаемость:
 * выше давления насыщения 1,5·10⁻⁹ 1/Па (порода, нефть, связанная вода), ниже — на порядок больше за счёт выделения
 * растворённого газа.
 *
 * <p>Объём залежи — купол-параболоид ловушки до контакта: площадь сечения на глубине z — πR²(z − z_св)/A, где A —
 * амплитуда; пласт мощностью t вырезает из купола слой не толще половины высоты залежи. Поровый объём нефти —
 * V·φ·(1 − S_св), S_св = 0,25. Объёмный коэффициент нефти B_o = 1,2, газосодержание 90 м³/м³, давление насыщения —
 * 0,75 начального пластового [допущение: залежь недонасыщенная, PVT не моделируется]. Продуктивность — по Дарси для
 * псевдоустановившегося режима, J = 2πkh / (μB_o(ln(r_e/r_w) − 0,75 + S)), скин перфорации S = 2.
 *
 * <p>Газовый фактор выше давления насыщения равен газосодержанию, ниже растёт с выделением свободного газа
 * (Rs(1 + 4(p_b − p)/p_b)); обводнённость растёт квадратично с отбором от подвижных запасов (35 % НГЗ) — эмпирика
 * [допущение] вместо модели вытеснения Бакли–Леверетта.
 */
public final class ProducingReservoir {
    public static final double SWI = 0.25, BO = 1.2, RS_M3M3 = 90, BUBBLE_FRACTION = 0.75, SKIN = 2;
    public static final double AQUIFER_RATIO = 5, C_UNDERSATURATED = 1.5e-9, C_SOLUTION_GAS = 1.5e-8;
    public static final double DRAINAGE_RADIUS_M = 300, WELL_RADIUS_M = 0.108;
    /** Подвижные запасы — доля начальных геологических (коэффициент извлечения при упругом и газовом режиме). */
    public static final double MOVABLE_FRACTION = 0.35;
    /** Плотность дегазированной нефти и пластовой воды в поверхностных условиях, кг/м³. */
    public static final double STOCK_OIL_KGM3 = 850, WATER_KGM3 = 1030;

    private final Trap trap;
    private final double perfTopM, perfBaseM, hcpvM3, ooipM3, initialPa, bubblePa, productivity;
    private double pressurePa, oilM3, waterM3, gasStdM3;

    private ProducingReservoir(Trap trap, double perfTopM, double perfBaseM) {
        this.trap = trap;
        this.perfTopM = perfTopM;
        this.perfBaseM = perfBaseM;
        double column = trap.contactDepthM() - trap.crestDepthM();
        double slab = Math.min(trap.thicknessM(), column / 2);
        double grv = Math.PI * trap.radiusM() * trap.radiusM() * column / trap.closureM() * slab;
        hcpvM3 = grv * trap.porosity() * (1 - SWI);
        ooipM3 = hcpvM3 / BO;
        initialPa = trap.pressureAt(datumM());
        pressurePa = initialPa;
        bubblePa = BUBBLE_FRACTION * initialPa;
        double h = perfBaseM - perfTopM;
        productivity = 2 * Math.PI * trap.permeabilityM2() * h
                / (FormationFluid.OIL.viscosityPaS * BO * (Math.log(DRAINAGE_RADIUS_M / WELL_RADIUS_M) - 0.75 + SKIN));
    }

    /**
     * Залежь под скважиной с кровлей коллектора на {@code topM}: перфорируется нефтенасыщенная часть пласта до ВНК.
     * {@code null} — в точке нет нефти (вода, газ или скважина за контуром).
     */
    public static ProducingReservoir perforate(Trap trap, double topM) {
        if (trap == null || trap.fluid() != FormationFluid.OIL) return null;
        double base = Math.min(topM + trap.thicknessM(), trap.contactDepthM());
        if (base - topM < 0.5) return null;
        return new ProducingReservoir(trap, topM, base);
    }

    public Trap trap() {
        return trap;
    }

    /** Середина интервала перфорации — точка отсчёта давления, м. */
    public double datumM() {
        return 0.5 * (perfTopM + perfBaseM);
    }

    public double perforatedM() {
        return perfBaseM - perfTopM;
    }

    public double perfTopM() {
        return perfTopM;
    }

    public double pressurePa() {
        return pressurePa;
    }

    public double initialPressurePa() {
        return initialPa;
    }

    public double bubblePointPa() {
        return bubblePa;
    }

    public double temperatureK() {
        return Trap.temperatureK(datumM());
    }

    /** Начальные геологические запасы нефти, м³ в поверхностных условиях. */
    public double ooipM3() {
        return ooipM3;
    }

    public Inflow inflow() {
        return new Inflow(pressurePa, bubblePa, productivity);
    }

    /** Газовый фактор, м³ газа (н. у.) на м³ нефти. */
    public double gasOilRatio() {
        if (pressurePa >= bubblePa) return RS_M3M3;
        return RS_M3M3 * (1 + 4 * (bubblePa - pressurePa) / bubblePa);
    }

    /** Обводнённость продукции, доля воды в жидкости. */
    public double waterCut() {
        double f = Math.min(1, oilM3 / (MOVABLE_FRACTION * ooipM3));
        return Math.min(0.95, 0.02 + 0.9 * f * f);
    }

    /** Отбор за {@code dt}: нефть, м³/с (н. у.); вода и газ — по обводнённости и газовому фактору. */
    public void produce(double oilM3s, double dt) {
        if (oilM3s <= 0 || dt <= 0) return;
        double wc = waterCut();
        double oil = oilM3s * dt, water = oil * wc / (1 - wc);
        oilM3 += oil;
        waterM3 += water;
        gasStdM3 += oil * gasOilRatio();
        double c = pressurePa >= bubblePa ? C_UNDERSATURATED : C_SOLUTION_GAS;
        pressurePa = Math.max(0, pressurePa - (oil * BO + water) / (c * hcpvM3 * (1 + AQUIFER_RATIO)));
    }

    public double producedOilM3() {
        return oilM3;
    }

    public double producedWaterM3() {
        return waterM3;
    }

    public double producedGasStdM3() {
        return gasStdM3;
    }

    /** Состояние для сохранения: давление и накопленная добыча. */
    public double[] exportState() {
        return new double[] {pressurePa, oilM3, waterM3, gasStdM3, perfTopM};
    }

    public static ProducingReservoir restore(Trap trap, double[] s) {
        if (s == null || s.length != 5) return null;
        ProducingReservoir r = perforate(trap, s[4]);
        if (r == null || !(s[0] >= 0)) return null;
        r.pressurePa = s[0];
        r.oilM3 = s[1];
        r.waterM3 = s[2];
        r.gasStdM3 = s[3];
        return r;
    }
}

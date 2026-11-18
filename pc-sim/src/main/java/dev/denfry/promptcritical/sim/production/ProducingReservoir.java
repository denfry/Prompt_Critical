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
 *
 * <p><b>Общая залежь.</b> Давление и накопленные отбор и закачка — в {@link State}, одном на ловушку: все скважины
 * ловушки тянут из одного «бака», а нагнетательная поднимает давление всем.
 *
 * <p><b>ППД</b> (09 §9.4.6): нагнетательная скважина — та же модель с отрицательным дебитом; приёмистость по Дарси с
 * вязкостью воды 0,5 мПа·с (пластовая температура около 60 °C) и концевой фазовой проницаемостью воды k_rw(1 − S_or)
 * = {@link #KRW_END} [BALANCE]. Закачка возвращает давление (Δp = W_i·B_w / (c·V_пор·(1 + W))) и вытесняет нефть:
 * подвижные запасы растут с закачанным поровым объёмом до {@link #MOVABLE_FRACTION} + {@link #PPD_EXTRA_FRACTION}
 * (коэффициент извлечения с ППД выше на 0,10 — §9.4.7). Закачка выше давления гидроразрыва (та же эквивалентная
 * плотность 1,70 г/см³, что у башмака буровой) раскрывает трещины: приёмистость ×5, а обводнённость добывающих
 * скважин растёт на 0,3 за 10 физических суток [BALANCE §9.4.6]. Взвесь в воде выше 5 мг/л растит скин
 * нагнетательной ({@link #SKIN_PER_MGL_S} [BALANCE]). Коэффициент компенсации VRR = W_i·B_w / (N_p·B_o + W_p·B_w).
 */
public final class ProducingReservoir {
    public static final double SWI = 0.25, BO = 1.2, RS_M3M3 = 90, BUBBLE_FRACTION = 0.75, SKIN = 2;
    public static final double AQUIFER_RATIO = 5, C_UNDERSATURATED = 1.5e-9, C_SOLUTION_GAS = 1.5e-8;
    public static final double DRAINAGE_RADIUS_M = 300, WELL_RADIUS_M = 0.108;
    /** Подвижные запасы — доля начальных геологических (коэффициент извлечения при упругом и газовом режиме). */
    public static final double MOVABLE_FRACTION = 0.35;
    /** Плотность дегазированной нефти и пластовой воды в поверхностных условиях, кг/м³. */
    public static final double STOCK_OIL_KGM3 = 850, WATER_KGM3 = 1030;
    /** ППД 09 §9.4.6: объёмный коэффициент и вязкость воды, концевая k_rw, прибавка подвижных запасов. */
    public static final double BW = 1.02, WATER_VISCOSITY_PAS = 0.5e-3, KRW_END = 0.3, PPD_EXTRA_FRACTION = 0.10;
    /** Эквивалентная плотность давления гидроразрыва, кг/м³ (как у башмака буровой, {@code WellGeometry.STANDARD}). */
    public static final double FRACTURE_KGM3 = 1700;
    public static final double FRACTURE_INJECTIVITY_FACTOR = 5, FLOOD_WC_STEP = 0.3, FLOOD_STEP_S = 10 * 86_400.0;
    /** Рост скина нагнетательной на мг/л взвеси сверх {@link #TSS_LIMIT_MGL} в секунду закачки [BALANCE]. */
    public static final double SKIN_PER_MGL_S = 1e-7, TSS_LIMIT_MGL = 5;

    /** Изменяемое состояние залежи, общее для всех скважин одной ловушки. */
    public static final class State {
        double pressurePa = Double.NaN, oilM3, waterM3, gasStdM3, injectedM3, floodWc;

        public double pressurePa() {
            return pressurePa;
        }

        public double injectedM3() {
            return injectedM3;
        }

        public double[] export() {
            return new double[] {pressurePa, oilM3, waterM3, gasStdM3, injectedM3, floodWc};
        }

        public static State restore(double[] s) {
            State st = new State();
            if (s == null || s.length < 4 || !(s[0] >= 0)) return st;
            st.pressurePa = s[0];
            st.oilM3 = s[1];
            st.waterM3 = s[2];
            st.gasStdM3 = s[3];
            if (s.length >= 6) {
                st.injectedM3 = s[4];
                st.floodWc = s[5];
            }
            return st;
        }
    }

    private final Trap trap;
    private final double perfTopM, perfBaseM, hcpvM3, ooipM3, initialPa, bubblePa, productivity;
    private final State st;

    private ProducingReservoir(Trap trap, double perfTopM, double perfBaseM, State state) {
        this.trap = trap;
        this.perfTopM = perfTopM;
        this.perfBaseM = perfBaseM;
        double column = trap.contactDepthM() - trap.crestDepthM();
        double slab = Math.min(trap.thicknessM(), column / 2);
        double grv = Math.PI * trap.radiusM() * trap.radiusM() * column / trap.closureM() * slab;
        hcpvM3 = grv * trap.porosity() * (1 - SWI);
        ooipM3 = hcpvM3 / BO;
        initialPa = trap.pressureAt(datumM());
        st = state;
        if (Double.isNaN(st.pressurePa)) st.pressurePa = initialPa;
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
        return perforate(trap, topM, new State());
    }

    /** То же на общем состоянии залежи {@code shared} (одна ловушка — один «бак»). */
    public static ProducingReservoir perforate(Trap trap, double topM, State shared) {
        if (trap == null || trap.fluid() != FormationFluid.OIL) return null;
        double base = Math.min(topM + trap.thicknessM(), trap.contactDepthM());
        if (base - topM < 0.5) return null;
        return new ProducingReservoir(trap, topM, base, shared);
    }

    public State state() {
        return st;
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
        return st.pressurePa;
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
        return new Inflow(st.pressurePa, bubblePa, productivity);
    }

    /** Газовый фактор, м³ газа (н. у.) на м³ нефти. */
    /** Подвижные запасы — доля начальных геологических: растут с закачанным поровым объёмом (ППД). */
    public double movableFraction() {
        return MOVABLE_FRACTION + PPD_EXTRA_FRACTION * Math.min(1, st.injectedM3 * BW / hcpvM3);
    }

    public double gasOilRatio() {
        if (st.pressurePa >= bubblePa) return RS_M3M3;
        return RS_M3M3 * (1 + 4 * (bubblePa - st.pressurePa) / bubblePa);
    }

    /** Обводнённость продукции, доля воды в жидкости. */
    public double waterCut() {
        double f = Math.min(1, st.oilM3 / (movableFraction() * ooipM3));
        return Math.min(0.95, 0.02 + 0.9 * f * f + st.floodWc);
    }

    /** Отбор за {@code dt}: нефть, м³/с (н. у.); вода и газ — по обводнённости и газовому фактору. */
    public void produce(double oilM3s, double dt) {
        if (oilM3s <= 0 || dt <= 0) return;
        double wc = waterCut();
        double oil = oilM3s * dt, water = oil * wc / (1 - wc);
        st.oilM3 += oil;
        st.waterM3 += water;
        st.gasStdM3 += oil * gasOilRatio();
        double c = st.pressurePa >= bubblePa ? C_UNDERSATURATED : C_SOLUTION_GAS;
        st.pressurePa = Math.max(0, st.pressurePa - (oil * BO + water) / (c * hcpvM3 * (1 + AQUIFER_RATIO)));
    }

    public double producedOilM3() {
        return st.oilM3;
    }

    public double producedWaterM3() {
        return st.waterM3;
    }

    public double producedGasStdM3() {
        return st.gasStdM3;
    }

    // --- ППД ---

    /** Давление гидроразрыва пласта на середине интервала, Па. */
    public double fracturePa() {
        return FRACTURE_KGM3 * Trap.G * datumM();
    }

    /** Приёмистость нагнетательной скважины по воде, м³/(с·Па), при скине {@code skin}. */
    public double injectivity(double skin) {
        double h = perforatedM();
        return 2 * Math.PI * trap.permeabilityM2() * KRW_END * h
                / (WATER_VISCOSITY_PAS * BW * (Math.log(DRAINAGE_RADIUS_M / WELL_RADIUS_M) - 0.75 + Math.max(skin, -3)));
    }

    /** Закачка воды {@code qM3s}, м³/с, за {@code dt}; {@code fractured} — забойное давление выше давления гидроразрыва. */
    public void inject(double qM3s, double dt, boolean fractured) {
        if (qM3s <= 0 || dt <= 0) return;
        double v = qM3s * dt;
        st.injectedM3 += v;
        double c = st.pressurePa >= bubblePa ? C_UNDERSATURATED : C_SOLUTION_GAS;
        st.pressurePa += v * BW / (c * hcpvM3 * (1 + AQUIFER_RATIO));
        if (fractured) st.floodWc = Math.min(0.9, st.floodWc + FLOOD_WC_STEP * dt / FLOOD_STEP_S);
    }

    public double injectedM3() {
        return st.injectedM3;
    }

    /** Коэффициент компенсации отбора закачкой VRR (09 §9.4.6). */
    public double voidageReplacementRatio() {
        double out = st.oilM3 * BO + st.waterM3 * BW;
        return out <= 0 ? 0 : st.injectedM3 * BW / out;
    }

    /** Состояние для сохранения: давление и накопленная добыча. */
    public double[] exportState() {
        return new double[] {st.pressurePa, st.oilM3, st.waterM3, st.gasStdM3, perfTopM};
    }

    public static ProducingReservoir restore(Trap trap, double[] s) {
        if (s == null || s.length != 5) return null;
        if (!(s[0] >= 0)) return null;
        return perforate(trap, s[4], State.restore(s));
    }
}

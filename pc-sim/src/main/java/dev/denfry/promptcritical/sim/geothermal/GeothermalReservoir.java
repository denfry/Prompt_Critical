package dev.denfry.promptcritical.sim.geothermal;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.thermo.if97.If97;

/**
 * Геотермальный пласт под парой скважин «добыча — закачка» (15 §15.6.1, §15.6.4). Время — SLOW.
 *
 * <ul>
 *   <li><b>Давление</b> — материальный баланс с подпиткой из законтурной области (как пласт 09, Dake гл. 9):
 *       {@code S·dp/dt = закачка − добыча + r·(p₀ − p)}, шаг неявный по подпитке.</li>
 *   <li><b>Холодный фронт.</b> Закачанная вода охлаждает породу; тепловой фронт идёт медленнее воды в
 *       ρc<sub>пласт</sub>/(ρc)<sub>воды</sub> раз. Охлаждённый объём пласта растёт как
 *       {@code m·c_w·ΔT/(ρc_пласт·ΔT)}; когда он доходит до объёма между скважинами, фронт прорывается, и
 *       температура добычи падает к температуре закачки по мере прохода второго такого объёма. Время прорыва
 *       пропорционально объёму между скважинами и обратно — расходу закачки [BALANCE].</li>
 *   <li><b>Кремнезём</b> в линии и скважине закачки: при индексе насыщения выше 1 ({@link Silica}) нарастает слой,
 *       приёмистость падает; кислотная обработка (HCl, 10) снимает {@value #ACID_REMOVAL} слоя.</li>
 *   <li><b>Кальцит</b> в стволе добывающей скважины, если давление на устье ниже давления насыщения и нет
 *       ингибитора: CO₂ уходит при вскипании, продуктивность падает.</li>
 *   <li><b>Давление на устье</b> — пластовое минус депрессия и вес столба горячего рассола; выше расчётного —
 *       причина ПАЗ «закрытие задвижки устья» (15 §15.13).</li>
 * </ul>
 */
public final class GeothermalReservoir {
    public static final double G = 9.806_65;
    /** Объёмная теплоёмкость скелета, Дж/(м³·К): гранит или песчаник, ρ ≈ 2650 кг/м³, c ≈ 1000 Дж/(кг·К). */
    public static final double ROCK_RHO_C = 2650 * 1000.0;
    public static final double WATER_C = 4180;
    /** Плотность пород над пластом, кг/м³ (литостатическое давление). */
    public static final double OVERBURDEN_KG_M3 = 2500;
    /** [BALANCE] Рост слоя кремнезёма, мм/с на единицу пересыщения (≈ 10 мм в год). */
    public static final double SILICA_MM_PER_S = 10.0 / 3.156e7;
    /** [BALANCE] Рост кальцита в стволе при вскипании, мм/с (≈ 20 мм в год). */
    public static final double CALCITE_MM_PER_S = 20.0 / 3.156e7;
    /** [BALANCE] Слой, при котором приёмистость или продуктивность падает вдвое, мм. */
    public static final double HALF_MM = 10;
    /** Доля слоя кремнезёма, снимаемая одной кислотной обработкой. */
    public static final double ACID_REMOVAL = 0.8;

    /**
     * Параметры пласта.
     *
     * @param tempK               начальная температура пласта, К (180–260 °C, {@code geothermal.field_temperature_c})
     * @param depthM              глубина пласта, м
     * @param porosity            пористость
     * @param storageKgPerPa      ёмкость пласта S, кг/Па
     * @param rechargeKgSPerPa    подпитка r, кг/(с·Па)
     * @param productivityKgSPerPa продуктивность добывающей скважины, кг/(с·Па)
     * @param injectivityKgSPerPa приёмистость нагнетательной скважины, кг/(с·Па)
     * @param sweptBulkM3         объём пласта между скважинами, м³
     * @param ncgMassFrac         доля НКГ в рассоле
     */
    public record Spec(double tempK, double depthM, double porosity, double storageKgPerPa, double rechargeKgSPerPa,
            double productivityKgSPerPa, double injectivityKgSPerPa, double sweptBulkM3, double ncgMassFrac) {
        public Spec {
            if (!(tempK > 373 && tempK < 640 && depthM > 0 && porosity > 0 && porosity < 1 && storageKgPerPa > 0
                    && rechargeKgSPerPa >= 0 && productivityKgSPerPa > 0 && injectivityKgSPerPa > 0 && sweptBulkM3 > 0
                    && ncgMassFrac >= 0 && ncgMassFrac < 0.05))
                throw new IllegalArgumentException("bad geothermal reservoir");
        }

        /** Начальное давление — гидростатическое, Па. */
        public double initialPressurePa() {
            return 1000 * G * depthM;
        }

        /** Литостатическое давление на глубине пласта, Па. */
        public double lithostaticPa() {
            return OVERBURDEN_KG_M3 * G * depthM;
        }

        /** Объёмная теплоёмкость насыщенного пласта, Дж/(м³·К). */
        public double bulkRhoC() {
            return porosity * 1000 * WATER_C + (1 - porosity) * ROCK_RHO_C;
        }

        /** Кремнезём пласта по кварцевому геотермометру, мг/кг. */
        public double silicaMgKg() {
            return Silica.quartzMgKg(tempK);
        }

        /**
         * Средняя пара скважин на месторождении, найденном на глубине {@code depthM} с температурой {@code tempK}:
         * 50 кг/с при депрессии 1 МПа, расстояние между скважинами ≈ 500 м в пласте мощностью 100 м.
         */
        public static Spec typical(double tempK, double depthM) {
            return new Spec(tempK, depthM, 0.1, 5e3, 2e-5, 5e-5, 5e-5, 500.0 * 500 * 100, 0.01);
        }
    }

    private final Spec spec;
    private double pressurePa;
    private double coldBulkM3;
    /** Средневзвешенная по массе температура закачки, К. */
    private double injTempK;
    private double injMassKg;
    private double silicaMm;
    private double calciteMm;
    private boolean flashingInBore;

    public GeothermalReservoir(Spec spec) {
        this.spec = spec;
        this.pressurePa = spec.initialPressurePa();
        this.injTempK = spec.tempK();
    }

    public Spec spec() {
        return spec;
    }

    public double pressurePa() {
        return pressurePa;
    }

    /** Температура добываемого рассола, К. */
    public double productionTempK() {
        double over = (coldBulkM3 - spec.sweptBulkM3()) / spec.sweptBulkM3();
        double f = Math.max(0, Math.min(1, over));
        return spec.tempK() - (spec.tempK() - injTempK) * f;
    }

    /** Прорвался ли холодный фронт закачки к добывающей скважине. */
    public boolean coldBreakthrough() {
        return coldBulkM3 > spec.sweptBulkM3();
    }

    /** Доля объёма между скважинами, уже охлаждённая закачкой (больше 1 — фронт прорвался). */
    public double coldFrontFraction() {
        return coldBulkM3 / spec.sweptBulkM3();
    }

    public double silicaMm() {
        return silicaMm;
    }

    public double calciteMm() {
        return calciteMm;
    }

    public boolean flashingInBore() {
        return flashingInBore;
    }

    /** Продуктивность с учётом кальцита, кг/(с·Па). */
    public double productivity() {
        return spec.productivityKgSPerPa() / (1 + calciteMm / HALF_MM);
    }

    /** Приёмистость с учётом кремнезёма, кг/(с·Па). */
    public double injectivity() {
        return spec.injectivityKgSPerPa() / (1 + silicaMm / HALF_MM);
    }

    /** Наибольший приток к устью при открытой задвижке и давлении на устье {@code pWellheadPa}, кг/с. */
    public double maxProductionKgS(double pWellheadPa) {
        double drive = pressurePa - columnPa() - Math.max(0, pWellheadPa);
        return Math.max(0, drive * productivity());
    }

    /** Давление на устье добывающей скважины при расходе {@code productionKgS}, Па. */
    public double wellheadPressurePa(double productionKgS) {
        return Math.max(0, pressurePa - Math.max(0, productionKgS) / productivity() - columnPa());
    }

    /** Забойное давление нагнетательной скважины, нужное для расхода {@code injectionKgS}, Па. */
    public double injectionBottomholePa(double injectionKgS) {
        return pressurePa + Math.max(0, injectionKgS) / injectivity();
    }

    /** Энтальпия добываемого рассола на устье (адиабатический подъём: энтальпия пласта), Дж/кг. */
    public double productionEnthalpyJkg() {
        double t = productionTempK();
        return If97.pt(Math.max(pressurePa, If97.psat(t) * 1.01), t).h();
    }

    /** Вес столба горячего рассола в стволе, Па. */
    private double columnPa() {
        double t = productionTempK();
        double rho = If97.pt(Math.max(pressurePa, If97.psat(t) * 1.01), t).rho();
        return rho * G * spec.depthM();
    }

    /**
     * Шаг SLOW.
     *
     * @param productionKgS  добыча, кг/с
     * @param injectionKgS   закачка, кг/с
     * @param injectionTempK температура закачки, К
     * @param silicaIndex    индекс насыщения закачиваемого рассола по аморфному кремнезёму ({@link Silica})
     * @param inhibitor      подаётся ли ингибитор кальцита в ствол
     */
    public void step(double dt, double productionKgS, double injectionKgS, double injectionTempK, double silicaIndex,
            boolean inhibitor) {
        if (!(dt > 0)) return;
        double prod = Double.isFinite(productionKgS) ? Math.max(0, productionKgS) : 0;
        double inj = Double.isFinite(injectionKgS) ? Math.max(0, injectionKgS) : 0;
        double s = spec.storageKgPerPa(), r = spec.rechargeKgSPerPa(), p0 = spec.initialPressurePa();
        pressurePa = Math.max(0, (pressurePa + dt / s * (inj - prod + r * p0)) / (1 + dt * r / s));

        if (inj > 0 && Double.isFinite(injectionTempK) && injectionTempK < spec.tempK()) {
            double m = inj * dt;
            injTempK = (injTempK * injMassKg + injectionTempK * m) / (injMassKg + m);
            injMassKg += m;
            coldBulkM3 += m * WATER_C / spec.bulkRhoC();
        }
        if (inj > 0 && silicaIndex > 1 && Double.isFinite(silicaIndex))
            silicaMm += SILICA_MM_PER_S * (silicaIndex - 1) * dt;

        flashingInBore = prod > 0 && wellheadPressurePa(prod) < If97.psat(productionTempK());
        if (flashingInBore && !inhibitor) calciteMm += CALCITE_MM_PER_S * dt;
    }

    /** Кислотная обработка нагнетательной скважины (HCl, 10). */
    public void acidize() {
        silicaMm *= 1 - ACID_REMOVAL;
    }

    /** Механическая чистка ствола добывающей скважины (развальцовка) — снимает кальцит. */
    public void reamCalcite() {
        calciteMm = 0;
    }

    public void save(StateWriter out) {
        out.putDouble("p_pa", pressurePa);
        out.putDouble("cold_m3", coldBulkM3);
        out.putDouble("inj_t_k", injTempK);
        out.putDouble("inj_kg", injMassKg);
        out.putDouble("silica_mm", silicaMm);
        out.putDouble("calcite_mm", calciteMm);
    }

    public void load(StateReader in) {
        pressurePa = finite(in.optDouble("p_pa", spec.initialPressurePa()), spec.initialPressurePa());
        coldBulkM3 = Math.max(0, finite(in.optDouble("cold_m3", 0), 0));
        injTempK = finite(in.optDouble("inj_t_k", spec.tempK()), spec.tempK());
        injMassKg = Math.max(0, finite(in.optDouble("inj_kg", 0), 0));
        silicaMm = Math.max(0, finite(in.optDouble("silica_mm", 0), 0));
        calciteMm = Math.max(0, finite(in.optDouble("calcite_mm", 0), 0));
    }

    private static double finite(double v, double def) {
        return Double.isFinite(v) ? v : def;
    }
}

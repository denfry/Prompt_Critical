package dev.denfry.promptcritical.sim.npp;

/**
 * Бассейн выдержки облучённого топлива с открытой поверхностью: запас воды, её температура, отвод тепла насосами
 * охлаждения через теплообменник техводы, подпитка, кипение и оголение стеллажей.
 *
 * <p>Тепловой баланс одним объёмом: остаточное тепло ТВС минус теплообменник ({@code UA·(T − T_техводы)} до его
 * предельной мощности) минус потери с поверхности ({@code h·A·(T − T_воздуха)}, испарение и конвекция одним
 * коэффициентом). Насосы берут воду из патрубка ниже нормального уровня: упал уровень ниже патрубка — охлаждение
 * теряет всас, даже если питание вернулось (так же подпитка нужна раньше охлаждения). У точки кипения лишнее тепло
 * уходит на испарение, уровень падает. Ниже верха стеллажей ТВС оголяются: доля оголения линейна по высоте топлива,
 * оболочки греются к {@code T_воды + u·Q/(hA_возд·N)} с постоянной времени минут — свежевыгруженное топливо
 * доходит до разрушения оболочек, выдержанное нет (зависимость от времени выдержки — как в NUREG-1738, 2001).
 *
 * <p>Шаг детерминирован, без состояния вне полей. Значения {@link #VVER70} — по модели бассейна АЭС Райнсберг
 * (5 × 5 м, 8 м глубиной, art/blockbench/core/vver70/vver70_fuel_handling.author.js); мощность охлаждения и
 * подпитки — оценки [est].
 */
public final class SpentFuelPool {
    /** Удельная теплоёмкость воды, Дж/(кг·К), и теплота парообразования, Дж/кг; плотность воды бассейна, кг/м³. */
    public static final double CP = 4186, H_FG = 2.257e6, RHO = 990;
    /** Разрушение оболочек на воздухе, °C — тот же порог, что у стеллажа бассейна старой серии. */
    public static final double CLAD_FAIL_C = 800;
    /** Постоянная времени прогрева оголённых оболочек, с [est]. */
    public static final double CLAD_TAU_S = 600;

    /**
     * Конструкция бассейна.
     *
     * @param areaM2              площадь зеркала, м²
     * @param floorM              пол бассейна над низом модели, м
     * @param nominalLevelM       нормальный уровень, м (от низа модели)
     * @param suctionM            патрубок всаса насосов охлаждения, м
     * @param rackTopM            верх стеллажей (головки ТВС), м
     * @param fuelBottomM         низ топливного столба в стеллаже, м
     * @param coolingUaWK         UA теплообменника охлаждения, Вт/К
     * @param coolingMaxW         предельная мощность охлаждения, Вт
     * @param serviceWaterC       температура техводы, °C
     * @param makeupKgS           подпитка, кг/с
     * @param surfaceHWm2K        потери с зеркала, Вт/(м²·К)
     * @param airHaPerAssemblyWK  отвод тепла от оголённой ТВС воздухом и паром, Вт/К
     * @param boilC               температура кипения у зеркала, °C (бассейн открыт в зал)
     */
    public record Spec(double areaM2, double floorM, double nominalLevelM, double suctionM, double rackTopM, double fuelBottomM,
            double coolingUaWK, double coolingMaxW, double serviceWaterC, double makeupKgS, double surfaceHWm2K,
            double airHaPerAssemblyWK, double boilC) {
        /** Масса воды при уровне {@code levelM}, кг. */
        public double waterKgAt(double levelM) {
            return Math.max(0, levelM - floorM) * areaM2 * RHO;
        }
    }

    /**
     * Бассейн ВВЭР-70: зеркало 5 × 5 м, пол 0,15 м, вода до 7,5 м, всас насосов на 6,5 м, головки ТВС на 3,5 м, топливо
     * с 0,5 м; охлаждение 50 кВт/К до 1,5 МВт на техводе 25 °C, подпитка 5 кг/с [est].
     */
    public static final Spec VVER70 = new Spec(25, 0.15, 7.5, 6.5, 3.5, 0.5, 50e3, 1.5e6, 25, 5, 15, 3, 100);

    private final Spec spec;
    private double waterKg, waterC, cladC, coolingW, boilKgS;
    private boolean cladFailed;

    public SpentFuelPool(Spec spec) {
        this.spec = spec;
        waterKg = spec.waterKgAt(spec.nominalLevelM());
        waterC = spec.serviceWaterC();
        cladC = waterC;
    }

    /**
     * Шаг {@code dt} с.
     *
     * @param decayW     остаточное тепло всех ТВС в бассейне, Вт
     * @param assemblies ТВС в стеллажах
     * @param cooling    насосы охлаждения запитаны
     * @param makeup     подпитка запитана
     * @param airC       воздух зала, °C
     */
    public void step(double dt, double decayW, int assemblies, boolean cooling, boolean makeup, double airC) {
        double level = levelM();
        coolingW = cooling && level >= spec.suctionM()
                ? Math.max(0, Math.min(spec.coolingMaxW(), spec.coolingUaWK() * (waterC - spec.serviceWaterC()))) : 0;
        if (makeup) {
            double add = Math.min(spec.makeupKgS() * dt, spec.waterKgAt(spec.nominalLevelM()) - waterKg);
            if (add > 0) {
                waterC = (waterC * waterKg + spec.serviceWaterC() * add) / (waterKg + add);
                waterKg += add;
            }
        }
        double surfaceW = spec.surfaceHWm2K() * spec.areaM2() * (waterC - airC);
        double netJ = (decayW - coolingW - surfaceW) * dt;
        boilKgS = 0;
        if (waterKg <= 0) {
            waterKg = 0;
        } else {
            double toBoil = (spec.boilC() - waterC) * waterKg * CP;
            if (netJ <= toBoil) {
                waterC += netJ / (waterKg * CP);
            } else {
                waterC = spec.boilC();
                double boiled = Math.min(waterKg, (netJ - Math.max(0, toBoil)) / H_FG);
                waterKg -= boiled;
                boilKgS = boiled / dt;
            }
        }
        // оголённые оболочки: воздух и пар уносят тепло хуже воды
        double u = uncovered();
        double target = waterC + (u > 0 && assemblies > 0 ? u * decayW / (spec.airHaPerAssemblyWK() * assemblies) : 0);
        cladC += (target - cladC) * Math.min(1, dt / CLAD_TAU_S);
        if (cladC > CLAD_FAIL_C) cladFailed = true;
    }

    /** Уровень воды от низа модели, м. */
    public double levelM() {
        return spec.floorM() + waterKg / (RHO * spec.areaM2());
    }

    /** Доля высоты топлива над водой, 0…1. */
    public double uncovered() {
        double h = spec.rackTopM() - spec.fuelBottomM();
        return Math.max(0, Math.min(1, (spec.rackTopM() - levelM()) / h));
    }

    /** Слой воды над головками ТВС, см — защита сверху. */
    public double shieldWaterCm() {
        return Math.max(0, levelM() - spec.rackTopM()) * 100;
    }

    public double waterKg() {
        return waterKg;
    }

    public double waterC() {
        return waterC;
    }

    public double cladC() {
        return cladC;
    }

    /** Отвод теплообменником на последнем шаге, Вт. */
    public double coolingW() {
        return coolingW;
    }

    /** Выкипание на последнем шаге, кг/с. */
    public double boilKgS() {
        return boilKgS;
    }

    /** Оболочки разрушились (защёлка). */
    public boolean cladFailed() {
        return cladFailed;
    }

    public Spec spec() {
        return spec;
    }

    /** Восстановить состояние (сохранение мира). */
    public void restore(double waterKg, double waterC, double cladC, boolean cladFailed) {
        this.waterKg = Math.max(0, waterKg);
        this.waterC = waterC;
        this.cladC = cladC;
        this.cladFailed = cladFailed;
    }
}

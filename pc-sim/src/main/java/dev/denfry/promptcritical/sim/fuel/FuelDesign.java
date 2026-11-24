package dev.denfry.promptcritical.sim.fuel;

import dev.denfry.promptcritical.sim.uranium.UraniumLedger;

/**
 * Конструкции ТВС и твэлов. ВВЭР — шестигранная решётка, оболочка из сплава Э110 (Zr–1 % Nb), таблетка с центральным
 * отверстием; PWR — квадратная решётка 17×17, Zircaloy-4, сплошная таблетка. Размеры — Олдер (Olander, «Fundamental
 * Aspects of Nuclear Reactor Fuel Elements», 1976, гл. 1), Тихоненко и др. «Топливо ВВЭР» (ОКБ «Гидропресс»), NUREG/CR-6410.
 *
 * <p>Уран в твэле — из геометрии столба таблеток и плотности: m_U = ρ·π/4·(d² − d_о²)·L·M_U/M_UO₂.
 */
public enum FuelDesign {
    /** ВВЭР-440: 126 твэлов в чехле, шестигранник 144 мм, столб 2,42 м. */
    VVER_440(126, 0, 10, 7.57e-3, 1.4e-3, 2.42, 9.1e-3, 0.65e-3, 0.6e6, Cladding.E110, true),
    /** ВВЭР-1000 (ТВС-2): 312 твэлов, 18 направляющих каналов и центральная труба, 15 решёток, столб 3,53 м. */
    VVER_1000(312, 19, 15, 7.57e-3, 1.4e-3, 3.53, 9.1e-3, 0.65e-3, 2.0e6, Cladding.E110, true),
    /** PWR 17×17: 264 твэла, 24 направляющие и измерительная трубки, 11 решёток, столб 3,66 м. */
    PWR_17X17(264, 25, 11, 8.19e-3, 0, 3.66, 9.5e-3, 0.57e-3, 2.4e6, Cladding.ZIRCALOY_4, false),
    /**
     * Канал АМ-1: 4 трубчатых твэла (стальные трубки ⌀14×0,2 и ⌀9×0,4 мм, топливо в кольце между ними, вода внутри) и
     * центральная труба подвода воды, длина 1,7 м. Исторически топливо — крупка U-Mo в магниевой матрице; у нас —
     * кольцевые таблетки UO₂ ⌀13,6/9,8 мм [замена материала]: 4,4 кг U на канал, ≈ 560 кг на 128 каналов, как в
     * АМ-1 (Kotchetkov, NEI, 2004).
     */
    AM_1(4, 1, 0, 13.6e-3, 9.8e-3, 1.70, 14e-3, 0.2e-3, 0.1e6, Cladding.STEEL, false),
    /**
     * Пучок CANDU-6: 37 элементов ⌀13,08 мм, оболочка Zircaloy-4 0,38 мм, таблетка ⌀12,15 мм, столб 0,48 м (пучок 0,495 м),
     * природный UO₂ — ≈ 19 кг урана в пучке (AECL, «CANDU 6 Technical Summary», 2005). 12 пучков в канале.
     */
    CANDU_37(37, 0, 0, 12.15e-3, 0, 0.48, 13.08e-3, 0.38e-3, 0.1e6, Cladding.ZIRCALOY_4, false),
    /**
     * Пучок NPD (Rolphton, 1962): 19 элементов в трёх кольцах 1 + 6 + 12 в трубе давления ⌀82,8 мм (IAEA-NDS, WIMS-D
     * burnup benchmarks, ячейка NPD), природный UO₂; элемент ⌀15,25 мм, оболочка 0,4 мм, таблетка ⌀14,4 мм, пучок
     * 0,495 м [оценка: размеры элемента]. Оболочка — Zircaloy-2, у нас {@link Cladding#ZIRCALOY_4} [замена материала].
     * Девять пучков в канале каландра 4,6 м.
     */
    NPD_19(19, 0, 0, 14.4e-3, 0, 0.48, 15.25e-3, 0.4e-3, 0.1e6, Cladding.ZIRCALOY_4, false);

    public enum Cladding { E110, ZIRCALOY_4, STEEL }

    public final int rods, guideTubes, grids;
    /** Таблетка: диаметр и центральное отверстие, м; столб таблеток, м. */
    public final double pelletD, holeD, stackM;
    /** Оболочка: наружный диаметр и толщина, м; давление гелия при заправке, Па. */
    public final double cladOd, cladWall, heliumPa;
    public final Cladding cladding;
    public final boolean hexagonal;

    FuelDesign(int rods, int guideTubes, int grids, double pelletD, double holeD, double stackM, double cladOd, double cladWall,
            double heliumPa, Cladding cladding, boolean hexagonal) {
        this.rods = rods;
        this.guideTubes = guideTubes;
        this.grids = grids;
        this.pelletD = pelletD;
        this.holeD = holeD;
        this.stackM = stackM;
        this.cladOd = cladOd;
        this.cladWall = cladWall;
        this.heliumPa = heliumPa;
        this.cladding = cladding;
        this.hexagonal = hexagonal;
    }

    /** Объём топлива в столбе таблеток, м³. */
    public double fuelVolumeM3() {
        return Math.PI / 4 * (pelletD * pelletD - holeD * holeD) * stackM;
    }

    /** Масса UO₂ в твэле при плотности {@code fractionTd} от теоретической, кг. */
    public double uo2KgPerRod(double fractionTd, double u235) {
        return fuelVolumeM3() * Uo2.theoreticalDensity(u235) * fractionTd;
    }

    /** Уран в твэле, кг. */
    public double uKgPerRod(double fractionTd, double u235) {
        return uo2KgPerRod(fractionTd, u235) * Uo2.uraniumShare(u235);
    }

    /** Уран в ТВС, кг. */
    public double uKgPerAssembly(double fractionTd, double u235) {
        return rods * uKgPerRod(fractionTd, u235);
    }

    /**
     * Плотность таблетки в допуске: ВВЭР — 10,4–10,7 г/см³ (ТУ ТВЭЛ, 94,9–97,6 % от 10,96), PWR — 94,5–96,5 %
     * теоретической (Olander, гл. 14).
     */
    public boolean densityInSpec(double fractionTd) {
        return hexagonal ? fractionTd >= 10.4 / 10.96 - 1e-9 && fractionTd <= 10.7 / 10.96 + 1e-9
                : fractionTd >= 0.945 - 1e-9 && fractionTd <= 0.965 + 1e-9;
    }

    /** Таблеток в твэле при высоте таблетки {@link Uo2#PELLET_HEIGHT_M}. */
    public int pelletsPerRod() {
        return (int) Math.round(stackM / Uo2.PELLET_HEIGHT_M);
    }

    /** UO₂ — константы. */
    public static final class Uo2 {
        /** Высота таблетки, м. */
        public static final double PELLET_HEIGHT_M = 0.010;

        private Uo2() {
        }

        /** Теоретическая плотность UO₂ (10,96 г/см³ для природного; чуть меньше с обогащением), кг/м³. */
        public static double theoreticalDensity(double u235) {
            double mu = u235 * 235.044 + (1 - u235) * 238.051;
            return 10_960 * (mu + 32.0) / (UraniumLedger.M_U + 32.0);
        }

        /** Доля урана в UO₂ по массе. */
        public static double uraniumShare(double u235) {
            double mu = u235 * 235.044 + (1 - u235) * 238.051;
            return mu / (mu + 31.998);
        }
    }
}

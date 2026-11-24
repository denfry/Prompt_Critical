package dev.denfry.promptcritical.sim.radiation;

import java.util.List;

/**
 * Транспортно-упаковочный контейнер ОЯТ класса CASTOR V/19 (аналог ТУК-13; 02 §2.7.5): корпус из высокопрочного
 * чугуна с шаровидным графитом ∅2,44 м, стенка ≈ 0,38 м, в сверлениях стенки — стержни полиэтилена (замедлитель
 * нейтронов), 19 ТВС PWR. Стенка около 44 см взята слоями чугун 18 см — полиэтилен 8 см (два ряда стержней ПЭ,
 * пересчитанные в сплошной слой) — чугун 18 см; плотность чугуна 7,1 г/см³ (GNS, CASTOR V/19 product info, 2023).
 *
 * <p>Пределы перевозки по IAEA SSR-6 (исключительное использование): мощность дозы на поверхности ≤ 2 мЗв/ч, на 2 м
 * от поверхности ≤ 0,1 мЗв/ч. Тепловыделение загрузки — не больше {@value #HEAT_LIMIT_W} Вт
 * ({@code storage.cask_heat_limit_w}, 02 §2.7.4).
 */
public final class SpentFuelCask {
    public static final double CAST_IRON_DENSITY = 7.1, RADIUS_M = 1.22, HEAT_LIMIT_W = 35_000;
    public static final double SURFACE_LIMIT_SV_H = 2e-3, AT_2M_LIMIT_SV_H = 1e-4;
    public static final List<DoseRate.Layer> WALL = List.of(new DoseRate.Layer(ShieldMaterial.IRON, 18, CAST_IRON_DENSITY),
            new DoseRate.Layer(ShieldMaterial.POLYETHYLENE, 8), new DoseRate.Layer(ShieldMaterial.IRON, 18, CAST_IRON_DENSITY));
    /** Место для ТВС в корзине. */
    public static final int ASSEMBLIES = 19;

    private SpentFuelCask() {
    }

    /** Источник загрузки, видимый снаружи стенки. */
    public static PointSource outside(PointSource load) {
        return DoseRate.behind(load, WALL);
    }

    /** Мощность дозы на поверхности, Зв/ч. */
    public static double surfaceSvH(PointSource load) {
        return DoseRate.totalSvH(outside(load), RADIUS_M, List.of());
    }

    /** Мощность дозы в 2 м от поверхности, Зв/ч. */
    public static double at2mSvH(PointSource load) {
        return DoseRate.totalSvH(outside(load), RADIUS_M + 2, List.of());
    }

    /** Загрузку можно закрыть и везти: тепло и обе мощности дозы в пределах. */
    public static boolean transportable(PointSource load, double heatW) {
        return heatW <= HEAT_LIMIT_W && surfaceSvH(load) <= SURFACE_LIMIT_SV_H && at2mSvH(load) <= AT_2M_LIMIT_SV_H;
    }
}

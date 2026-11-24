package dev.denfry.promptcritical.sim.radiation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Материалы защиты 04b §4b.4 и примеры «проверки на салфетке» §4b.4.4: стена горячей камеры, Co-60 за блоком бетона,
 * нейтроны контейнера ОЯТ за полиэтиленом, поправка «нет водорода» и вторичные γ захвата.
 */
class ShieldingTest {
    private static double uSvH(PointSource s, double r, DoseRate.Layer... layers) {
        return DoseRate.totalSvH(s, r, List.of(layers)) * 1e6;
    }

    @Test
    void tableCoefficientsAtCs137() {
        // 04b §4b.4.1: μ при 0,662 МэВ, см⁻¹ (у магнетитового бетона массовый μ взят от обычного: −2 %)
        assertThat(ShieldMaterial.HEAVY_CONCRETE.mu(0.662, 3.6)).isCloseTo(0.2835, within(0.009));
        assertThat(ShieldMaterial.POLYETHYLENE.massAttenuation(0.662)).isCloseTo(0.08799, within(0.0005));
        assertThat(ShieldMaterial.BORATED_POLYETHYLENE.mu(1.25, 1.0)).isCloseTo(0.0650, within(0.0005));
        assertThat(ShieldMaterial.LEAD_GLASS.mu(0.662, 4.8)).isCloseTo(0.48, within(0.03));
        // сечения выведения §4b.4.3, см⁻¹
        assertThat(ShieldMaterial.HEAVY_CONCRETE.removal(3.6)).isCloseTo(0.11, within(1e-9));
        assertThat(ShieldMaterial.POLYETHYLENE.removal(0.95)).isCloseTo(0.123, within(1e-9));
        assertThat(ShieldMaterial.BORATED_POLYETHYLENE.removal(1.0)).isCloseTo(0.120, within(1e-9));
        // сетка продлена до 6 МэВ: γ N-16 проходит бетон легче, чем 2 МэВ
        assertThat(ShieldMaterial.CONCRETE.massAttenuation(6.129)).isLessThan(ShieldMaterial.CONCRETE.massAttenuation(2.0));
    }

    @Test
    void hotCellWallHoldsAPetabecquerelOfCaesium() {
        // §4b.4.4 п. 1: 1 ПБк Cs-137, оператор в 1,5 м; 1,12 м бетона дают ≤ 10 мкЗв/ч
        PointSource cs = Emitter.CS137.source(1e15);
        double concrete = uSvH(cs, 1.5, new DoseRate.Layer(ShieldMaterial.CONCRETE, 112));
        assertThat(concrete).isBetween(2.0, 10.0);
        // 0,72 м тяжёлого бетона — то же самое
        double heavy = uSvH(cs, 1.5, new DoseRate.Layer(ShieldMaterial.HEAVY_CONCRETE, 72));
        assertThat(heavy / concrete).isBetween(0.5, 2.0);
        // окно: 1 м свинцового стекла держит не хуже стены
        assertThat(uSvH(cs, 1.5, new DoseRate.Layer(ShieldMaterial.LEAD_GLASS, 100))).isLessThan(concrete);
    }

    @Test
    void cobaltBehindOneBlockOfConcrete() {
        // §4b.4.4 п. 3: 1 ТБк Co-60, r = 2 м, 1 м бетона — 4,1 мкЗв/ч (там с H*(10)/K = 1,16, здесь керма); за
        // 14 длинами пробега формы фактора накопления расходятся в разы, точечное ядро держит порядок
        assertThat(uSvH(Emitter.CO60.source(1e12), 2, new DoseRate.Layer(ShieldMaterial.CONCRETE, 100)) * 1.16)
                .isBetween(4.1 / 2, 4.1 * 2);
    }

    @Test
    void caskNeutronsBehindPolyethylene() {
        // §4b.4.4 п. 4: 10⁸ н/с на 1 м — около 1 мЗв/ч; 30 см полиэтилена — e^{−3,69} = 0,025
        PointSource n = new PointSource(new double[0], new double[0], 1e8);
        double bare = DoseRate.neutronSvH(n, 1, List.of()) * 1e6;
        assertThat(bare).isBetween(900.0, 1200.0);
        List<DoseRate.Layer> pe = List.of(new DoseRate.Layer(ShieldMaterial.POLYETHYLENE, 30));
        assertThat(DoseRate.neutronSvH(n, 1, pe) * 1e6 / bare).isCloseTo(Math.exp(-3.69), within(0.001));
        // водород светит 2,2 МэВ, борированный ПЭ — мягкой линией 0,478 МэВ: вторичных γ меньше
        double capPe = DoseRate.captureGammaGyH(n, 1, pe);
        double capBpe = DoseRate.captureGammaGyH(n, 1, List.of(new DoseRate.Layer(ShieldMaterial.BORATED_POLYETHYLENE, 30)));
        assertThat(capPe).isPositive();
        assertThat(capBpe).isLessThan(capPe);
    }

    @Test
    void leadAloneStopsFewNeutrons() {
        PointSource n = new PointSource(new double[0], new double[0], 1e8);
        double lead = DoseRate.neutronSvH(n, 1, List.of(new DoseRate.Layer(ShieldMaterial.LEAD, 20)));
        // без замедлителя сечение свинца вдвое меньше: e^{−0,118·20/2}
        assertThat(lead / DoseRate.neutronSvH(n, 1, List.of())).isCloseTo(Math.exp(-0.118 * 10), within(0.002));
        // тот же свинец за 30 см воды работает полным сечением
        double withWater = DoseRate.neutronSvH(n, 1, List.of(new DoseRate.Layer(ShieldMaterial.WATER, 30),
                new DoseRate.Layer(ShieldMaterial.LEAD, 20)));
        assertThat(withWater / DoseRate.neutronSvH(n, 1, List.of(new DoseRate.Layer(ShieldMaterial.WATER, 30))))
                .isCloseTo(Math.exp(-0.118 * 20), within(0.002));
    }

    @Test
    void captureGammasAreAttenuatedByTheRestOfTheShield() {
        // вода, а за ней свинец: 2,2 МэВ водорода гасит свинец; наоборот — нет
        PointSource n = new PointSource(new double[0], new double[0], 1e8);
        DoseRate.Layer water = new DoseRate.Layer(ShieldMaterial.WATER, 60), lead = new DoseRate.Layer(ShieldMaterial.LEAD, 10);
        double leadOutside = DoseRate.captureGammaGyH(n, 2, List.of(water, lead));
        double waterOutside = DoseRate.captureGammaGyH(n, 2, List.of(lead, water));
        assertThat(leadOutside).isLessThan(waterOutside * 0.1);
        // без нейтронов вторичных γ нет
        assertThat(DoseRate.captureGammaGyH(Emitter.CS137.source(1e12), 2, List.of(water))).isZero();
    }

    @Test
    void behindEqualsTheRayThroughTheSameLayers() {
        PointSource s = Emitter.CO60.source(1e12).plus(new PointSource(new double[0], new double[0], 1e8));
        List<DoseRate.Layer> wall = List.of(new DoseRate.Layer(ShieldMaterial.CONCRETE, 60), new DoseRate.Layer(ShieldMaterial.IRON, 10));
        assertThat(DoseRate.totalSvH(DoseRate.behind(s, wall), 2, List.of()))
                .isCloseTo(DoseRate.totalSvH(s, 2, wall), within(DoseRate.totalSvH(s, 2, wall) * 1e-9));
    }

    @Test
    void hotCellKeepsTheOperatorUnderTheDesignRate() {
        assertThat(HotCell.operatorSvH(Emitter.CS137.source(1e15))).isLessThan(HotCell.DESIGN_SV_H);
        // 1 ПБк Co-60 стена тяжёлого бетона уже не держит
        assertThat(HotCell.operatorSvH(Emitter.CO60.source(1e15))).isGreaterThan(HotCell.DESIGN_SV_H);
    }

    @Test
    void castorWithNineteenAssembliesAfterFiveYearsMayTravel() {
        // 19 ТВС PWR по 0,46 т ТМ, 40 МВт·сут/кг, 5 лет выдержки
        ActivityInventory fuel = SpentFuel.discharge(19 * 460, 40, 38, 10.4, true).decayed(5 * 365.25 * 86400);
        PointSource load = fuel.source();
        assertThat(fuel.decayHeatW()).isBetween(15_000.0, SpentFuelCask.HEAT_LIMIT_W);
        assertThat(SpentFuelCask.surfaceSvH(load)).isLessThan(SpentFuelCask.SURFACE_LIMIT_SV_H);
        assertThat(SpentFuelCask.at2mSvH(load)).isLessThan(SpentFuelCask.AT_2M_LIMIT_SV_H);
        assertThat(SpentFuelCask.transportable(load, fuel.decayHeatW())).isTrue();
        // свежевыгруженное (30 сут) — нет: тепло и доза за пределами
        ActivityInventory fresh = SpentFuel.discharge(19 * 460, 40, 38, 10.4, true).decayed(30 * 86400);
        assertThat(SpentFuelCask.transportable(fresh.source(), fresh.decayHeatW())).isFalse();
    }

    @Test
    void dryStorageCladdingFollowsTheVents() {
        double amb = 293.15;
        // 02 §2.7.4: 35 кВт — +280 К (300 °C), половина каналов — +560 К, предел превышен
        assertThat(DryStorage.steadyCladK(amb, 35_000, 1) - 273.15).isCloseTo(300, within(0.01));
        assertThat(DryStorage.overLimit(DryStorage.steadyCladK(amb, 35_000, 1))).isFalse();
        assertThat(DryStorage.overLimit(DryStorage.steadyCladK(amb, 35_000, 0.5))).isTrue();
        // инерция: за постоянную времени — 63 % пути
        double t = DryStorage.step(amb, amb, 35_000, 1, DryStorage.TAU_S);
        assertThat(t - amb).isCloseTo(280 * (1 - Math.exp(-1)), within(0.01));
    }
}

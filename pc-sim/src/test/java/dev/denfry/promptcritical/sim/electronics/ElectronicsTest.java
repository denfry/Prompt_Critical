package dev.denfry.promptcritical.sim.electronics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * Кремний и планарный процесс против литературы: энергия рудно-термической печи 11–13 кВт·ч/кг, стехиометрия,
 * Дил–Гроув против таблицы Deal & Grove (1965) и правил Plummer, литература по литографии, глубина перехода, класс
 * ISO 14644-1, Пуассон и 8086, Шейль, Престон, Ричардсон–Дешман и Чайлд–Ленгмюр.
 */
class ElectronicsTest {

    @Test
    void submergedArcFurnaceEnergyAndBalance() {
        assertThat(SiliconChain.reactionKwhPerKg()).isCloseTo(6.82, within(0.05));
        assertThat(SiliconChain.safKwhPerKg()).isBetween(11.0, 13.0);
        // 2,14 кг SiO₂ на кг Si при полном извлечении, с извлечением 85 % — 2,5 кг; углерода — 0,86 кг и больше
        assertThat(SiliconChain.quartzPerKgSi()).isCloseTo(2.517, within(0.01));
        assertThat(SiliconChain.carbonPerKgSi()).isBetween(0.86 / 0.85, 1.2);
    }

    @Test
    void chlorosilaneStoichiometry() {
        // 3 HCl на Si: 106,8 моль/кг → 2,39 нм³; с SiCl₄ чуть больше
        assertThat(SiliconChain.hclNm3PerKgSi()).isBetween(2.39, 2.5);
        // SiHCl₃: 4,82 кг на кг Si при 100 % селективности → 3,6 л; при 88 % — 3,16 л
        assertThat(SiliconChain.tcsLitresPerKgSi()).isCloseTo(3.16, within(0.02));
        assertThat(SiliconChain.SIEMENS_KWH_PER_KG).isBetween(45.0, 200.0);
        assertThat(SiliconChain.hclOutNm3PerKgPoly()).isCloseTo(2.394, within(0.01));
    }

    @Test
    void dealGroveMatchesDealGroveTable() {
        // Deal & Grove 1965, табл. I (111): сухой 1000 °C — B = 0,0117 мкм²/ч, B/A = 0,071 мкм/ч; влажный 1000 °C — B = 0,287,
        // B/A = 1,27; сухой 1200 °C — B = 0,045, B/A = 1,12. Аррениусовы подгонки Plummer — в пределах 25 %
        assertThat(PlanarProcess.parabolicB(PlanarProcess.Ambient.DRY, 1000) / 0.0117).isBetween(0.8, 1.2);
        assertThat(PlanarProcess.linearBA(PlanarProcess.Ambient.DRY, 1000, false) / 0.071).isBetween(0.8, 1.2);
        assertThat(PlanarProcess.parabolicB(PlanarProcess.Ambient.WET, 1000) / 0.287).isBetween(0.8, 1.25);
        assertThat(PlanarProcess.linearBA(PlanarProcess.Ambient.WET, 1000, false) / 1.27).isBetween(0.8, 1.2);
        assertThat(PlanarProcess.parabolicB(PlanarProcess.Ambient.DRY, 1200) / 0.045).isBetween(0.8, 1.25);
        assertThat(PlanarProcess.linearBA(PlanarProcess.Ambient.DRY, 1200, false) / 1.12).isBetween(0.75, 1.25);
    }

    @Test
    void oxideThicknessesMatchProcessCharts() {
        // влажный 1100 °C, 1 ч, (100) — около 0,7 мкм; сухой 1000 °C, 1 ч, (100) — 40–50 нм (номограммы Plummer, рис. 6-17/6-18)
        assertThat(PlanarProcess.oxideUm(PlanarProcess.Ambient.WET, 1100, 1, true)).isBetween(0.6, 0.8);
        assertThat(PlanarProcess.oxideUm(PlanarProcess.Ambient.DRY, 1000, 1, true) * 1000).isBetween(35.0, 60.0);
        // между линейным (×4) и параболическим (×2) режимом, толстый влажный окисел — почти параболический
        double x1 = PlanarProcess.oxideUm(PlanarProcess.Ambient.WET, 1100, 1, true), x4 = PlanarProcess.oxideUm(PlanarProcess.Ambient.WET, 1100, 4, true);
        assertThat(x4 / x1).isBetween(2.0, 2.5);
    }

    @Test
    void lithographyLadder() {
        assertThat(PlanarProcess.proximityCdUm(0)).isEqualTo(2.0);
        assertThat(PlanarProcess.proximityCdUm(20)).isCloseTo(4.3, within(0.1));
        assertThat(PlanarProcess.proximityCdUm(100)).isBetween(8.0, 10.0);
        assertThat(PlanarProcess.gLineCdUm()).isCloseTo(1.0, within(0.01));
        assertThat(PlanarProcess.depthOfFocusUm(0.436, 0.35, 0.5)).isCloseTo(1.78, within(0.01));
    }

    @Test
    void phosphorusJunctionDepth() {
        // фосфор при 1000 °C, 30 мин, подложка 10¹⁵: D ≈ 1,2·10⁻¹⁴ см²/с, x_j ≈ 0,3 мкм
        assertThat(PlanarProcess.Dopant.PHOSPHORUS.diffusivity(1000)).isBetween(1e-14, 1.5e-14);
        assertThat(PlanarProcess.junctionDepthUm(PlanarProcess.Dopant.PHOSPHORUS, 1000, 0.5, 1e15)).isBetween(0.27, 0.33);
        assertThat(PlanarProcess.erfc(0)).isCloseTo(1, within(1e-6));
        assertThat(PlanarProcess.erfcInv(PlanarProcess.erfc(2.0))).isCloseTo(2.0, within(1e-5));
    }

    @Test
    void isoClassesAndYield() {
        // ISO 5: 3520 частиц/м³ ≥ 0,5 мкм, 100 000 ≥ 0,1 мкм
        assertThat(Cleanroom.isoLimit(5, 0.5)).isCloseTo(3520, within(5.0));
        assertThat(Cleanroom.isoLimit(5, 0.1)).isCloseTo(1e5, within(1.0));
        assertThat(Cleanroom.isoClass(3520)).isCloseTo(5.0, within(0.01));
        // комната под подпором: нефильтрованного подсоса 0,1 м³/ч, 1500 м³/ч через HEPA, никого внутри — около ISO 5
        double c = Cleanroom.concentration(0.1, 1500, 0);
        assertThat(Cleanroom.isoClass(c)).isBetween(4.5, 5.5);
        // открытая дверь шлюза — грязно
        assertThat(Cleanroom.isoClass(Cleanroom.concentration(500, 1500, 1))).isGreaterThan(6.5);
        assertThat(Cleanroom.poissonYield(1, 1)).isCloseTo(Math.exp(-1), within(1e-12));
    }

    @Test
    void intel8086IsAnEarly80sChip() {
        // 3 мкм: 29 000 транзисторов на ≈ 33 мм², 5 МГц
        assertThat(ChipDesign.MICROPROCESSOR.dieAreaMm2(3)).isCloseTo(33.6, within(1.0));
        assertThat(ChipDesign.clockMhz(3)).isEqualTo(5.0);
        // ISO 5 при 3 мкм: D₀ меньше 1 см⁻², выход больше половины; ISO 7 — почти ноль
        double y5 = Cleanroom.poissonYield(0.336, Cleanroom.defectDensity(Cleanroom.isoLimit(5, 0.5), 3, false));
        double y7 = Cleanroom.poissonYield(0.336, Cleanroom.defectDensity(Cleanroom.isoLimit(7, 0.5), 3, false));
        assertThat(y5).isBetween(0.5, 0.97);
        assertThat(y7).isLessThan(1e-3);
        // 50-мм пластина: около 40 кристаллов
        assertThat(Cleanroom.diesPerWafer(50, 33.6)).isBetween(30, 50);
    }

    @Test
    void fabRunHonoursProcessWindow() {
        // g-line 1 мкм: сухой окисел 25 нм (1000 °C ≈ 25 мин), фосфор 950 °C 20 мин → x_j ≈ 0,15 мкм
        WaferFab good = new WaferFab(ChipDesign.MICROPROCESSOR, WaferFab.Litho.G_LINE, 0, PlanarProcess.Ambient.DRY, 1000, 0.3, 950, 0.33, 1e15);
        WaferFab.Result r = good.run(Cleanroom.isoLimit(5, 0.5), 50);
        assertThat(r.ok()).as("%s", r).isTrue();
        assertThat(r.cdUm()).isCloseTo(1.0, within(0.01));
        assertThat(r.good()).isGreaterThan(0);
        // влажный окисел час при 1100 °C — 0,7 мкм: это полевой окисел, не подзатворный
        WaferFab thick = new WaferFab(ChipDesign.MICROPROCESSOR, WaferFab.Litho.G_LINE, 0, PlanarProcess.Ambient.WET, 1100, 1, 950, 0.33, 1e15);
        assertThat(thick.run(Cleanroom.isoLimit(5, 0.5), 50).reject()).isEqualTo(ChipDesign.Reject.OXIDE_THICK);
        // микропроцессор бесконтактной печатью с зазором 100 мкм (≈ 9 мкм) — слишком велик
        WaferFab coarse = new WaferFab(ChipDesign.MICROPROCESSOR, WaferFab.Litho.PROXIMITY, 100, PlanarProcess.Ambient.DRY, 1100, 3, 1100, 2, 1e15);
        assertThat(coarse.run(Cleanroom.isoLimit(5, 0.5), 50).reject()).isEqualTo(ChipDesign.Reject.TOO_LARGE);
    }

    @Test
    void czochralskiWindowAndScheil() {
        assertThat(Czochralski.singleCrystal(1, 15)).isTrue();
        assertThat(Czochralski.singleCrystal(3, 15)).isFalse();
        assertThat(Czochralski.singleCrystal(1, 0)).isFalse();
        // бор k = 0,8: на половине слитка концентрация на 15 % выше начальной; фосфор k = 0,35 — в 1,6 раза
        assertThat(Czochralski.scheil(0.8, 1, 0.5) / Czochralski.scheil(0.8, 1, 0)).isCloseTo(Math.pow(0.5, -0.2), within(1e-12));
        assertThat(Czochralski.scheil(0.35, 1, 0.5) / Czochralski.scheil(0.35, 1, 0)).isCloseTo(1.569, within(0.01));
        assertThat(Czochralski.usableFraction(0.8, 0.2)).isGreaterThan(Czochralski.usableFraction(0.35, 0.2));
        // 10 Ом·см p-типа — около 1,3·10¹⁵ см⁻³ бора (кривые Irvin)
        assertThat(Czochralski.resistivityP(1.3e15)).isBetween(9.0, 12.0);
    }

    @Test
    void waferingAndPreston() {
        // 100 мм слитка — 172 пластины по 280 мкм с пропилом 300 мкм
        assertThat(Wafering.wafersPerIngot(100)).isEqualTo(172);
        assertThat(Wafering.removalUmMin(1)).isCloseTo(0.54, within(0.01));
        assertThat(Wafering.polishMinutes(1)).isCloseTo(37, within(1.0));
    }

    @Test
    void vacuumTubeEmission() {
        // оксидный катод при 1050 К — доли А/см², вольфрам при той же температуре — ничто
        assertThat(VacuumTube.Cathode.OXIDE.emissionAcm2(1050)).isBetween(0.1, 2.0);
        assertThat(VacuumTube.Cathode.TUNGSTEN.emissionAcm2(1050)).isLessThan(1e-12);
        // вольфрам при 2500 К — около 0,3 А/см² (Dushman)
        assertThat(VacuumTube.Cathode.TUNGSTEN.emissionAcm2(2500)).isBetween(0.1, 1.0);
        // Чайлд–Ленгмюр: 100 В, 1 мм — 2,33·10⁻⁶·V^1,5/d² = 2,33·10³ А/м²
        assertThat(VacuumTube.childLangmuirAm2(100, 1e-3)).isCloseTo(2330, within(10.0));
    }
}

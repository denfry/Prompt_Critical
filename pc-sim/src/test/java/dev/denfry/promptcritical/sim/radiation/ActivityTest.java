package dev.denfry.promptcritical.sim.radiation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Наведённая активность и отработанное топливо: распад против периодов полураспада, накопление дочерних (вековое и
 * переходное равновесие), инвентарь Cs-137 в ТВС, мощность дозы и остаточное тепло выдержанной ТВС против
 * справочных порядков (Benedict–Pigford–Levi, гл. 7; IAEA «self-protecting» — больше 1 Зв/ч на 1 м).
 */
class ActivityTest {
    private static final double YEAR = 365.25 * 86400, DAY = 86400;

    @Test
    void activitiesFollowHalfLives() {
        ActivityInventory a = new ActivityInventory(Map.of(Emitter.CS137, 1e12, Emitter.I131, 1e12, Emitter.CO60, 1e12), 0, 1);
        ActivityInventory b = a.decayed(30.08 * YEAR);
        assertThat(b.bq(Emitter.CS137)).isCloseTo(5e11, within(1e8));
        assertThat(a.decayed(8.0252 * DAY).bq(Emitter.I131)).isCloseTo(5e11, within(1e8));
        // за 10 периодов I-131 (80 суток) — тысячная доля
        assertThat(a.decayed(80.25 * DAY).bq(Emitter.I131) / 1e12).isCloseTo(1.0 / 1024, within(1e-5));
        assertThat(a.decayed(5.2714 * YEAR).bq(Emitter.CO60)).isCloseTo(5e11, within(1e8));
    }

    @Test
    void daughtersGrowIntoEquilibrium() {
        // вековое: Y-90 догоняет Sr-90 за ~ 3 недели
        ActivityInventory sr = new ActivityInventory(Map.of(Emitter.SR90, 1e12), 0, 1).decayed(30 * DAY);
        assertThat(sr.bq(Emitter.Y90) / sr.bq(Emitter.SR90)).isCloseTo(1, within(0.01));
        // переходное: Nb-95/Zr-95 → λNb/(λNb − λZr) = 2,2
        ActivityInventory zr = new ActivityInventory(Map.of(Emitter.ZR95, 1e12), 0, 1).decayed(1000 * DAY);
        double ratio = Emitter.NB95.lambda() / (Emitter.NB95.lambda() - Emitter.ZR95.lambda());
        assertThat(zr.bq(Emitter.NB95) / zr.bq(Emitter.ZR95)).isCloseTo(ratio, within(0.02));
        assertThat(ratio).isCloseTo(2.2, within(0.05));
    }

    @Test
    void cs137InventoryOfAPwrAssembly() {
        // 0,46 т ТМ, 40 МВт·сут/кг, 38 Вт/г: Cs-137 ≈ 2·10¹⁵ Бк (≈ 55–65 кКи) — BPL, табл. 7.x
        ActivityInventory f = SpentFuel.discharge(460, 40, 38, 3, true);
        assertThat(f.bq(Emitter.CS137)).isBetween(1.8e15, 2.6e15);
        assertThat(f.bq(Emitter.SR90)).isBetween(1.6e15, 2.4e15);
        assertThat(f.bq(Emitter.CS134) / f.bq(Emitter.CS137)).isCloseTo(1, within(1e-9));
        // короткоживущий I-131 к выгрузке насыщен: A = F·y
        double rate = SpentFuel.fissions(460, 40) / (460 * 40 * SpentFuel.J_PER_MWD / (460e3 * 38));
        assertThat(f.bq(Emitter.I131)).isCloseTo(rate * SpentFuel.Y_I131, within(rate * SpentFuel.Y_I131 * 1e-6));
    }

    @Test
    void cooledAssemblyDoseAndHeat() {
        ActivityInventory f = SpentFuel.discharge(460, 40, 38, 3, true);
        ActivityInventory tenYears = f.decayed(10 * YEAR);
        // 10 лет выдержки: на 1 м без защиты — 10–100 Зв/ч по разным оценкам (NAS 2000, ORNL), ≫ порога самозащиты 1 Зв/ч
        assertThat(tenYears.doseRateSvH(1)).isBetween(10.0, 300.0);
        // год выдержки — заметно больше (Cs-134, Co-60, Ru-106)
        assertThat(f.decayed(YEAR).doseRateSvH(1)).isGreaterThan(2 * tenYears.doseRateSvH(1));
        // тепло через 10 лет — порядка 1 кВт на тонну (без актинидов модель ниже на треть)
        assertThat(tenYears.decayHeatW() / 0.46).isBetween(700.0, 2000.0);
        // самопоглощение: ТВС пропускает наружу 10–30 % γ Cs-137
        assertThat(f.escape(0.6617)).isBetween(0.08, 0.35);
        // три метра воды бассейна: доза над ним — микрозиверты
        double water = DoseRate.totalSvH(tenYears.source(), 3.5, java.util.List.of(new DoseRate.Layer(ShieldMaterial.WATER, 300)));
        assertThat(water).isLessThan(1e-4);
    }

    @Test
    void lowBurnupSlugIsMilder() {
        // блочок котла: 2,5 кг металлического урана, 0,1 МВт·сут/кг за месяц, без стали
        ActivityInventory slug = SpentFuel.discharge(2.5, 0.1, 0.004, 19, false);
        assertThat(slug.bq(Emitter.CO60)).isZero();
        assertThat(slug.doseRateSvH(1)).isLessThan(SpentFuel.discharge(460, 40, 38, 3, true).doseRateSvH(1));
        assertThat(slug.doseRateSvH(0.3)).isGreaterThan(1e-3); // в кармане — опасно
    }
}

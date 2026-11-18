package dev.denfry.promptcritical.sim.waste;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import dev.denfry.promptcritical.sim.waste.Supercompactor.Material;
import dev.denfry.promptcritical.sim.waste.Vitrification.Glass;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Переработка и кондиционирование РАО против практики: выпарка ЖРО (≈ 1 м³/ч, куб 300 г/л, очистка 10⁴), цементирование
 * (≈ 10 % солей, 110 л куба на бочку, прочность выше 4,9 МПа), битумирование (втрое меньше объёма), суперпрессование
 * (3–10 раз), остекловывание (включение, предельное тепло по стеклованию), хранилище с вентиляцией.
 */
class WasteConditioningTest {
    private static final LrwEvaporator.Feed NPP_LRW = new LrwEvaporator.Feed(5,
            Map.of(Emitter.CS137, 2.2e6, Emitter.CS134, 0.6e6, Emitter.CO60, 0.9e6), 303.15);

    @Test
    void evaporatorConcentratesSaltsAndKeepsActivityInTheSump() {
        LrwEvaporator ev = new LrwEvaporator();
        double fed = 0, condensate = 0, condBq = 0;
        double available = 20_000;
        // час работы: около кубометра упаренной воды
        LrwEvaporator.Step first = ev.step(3600, NPP_LRW, available, 1e9, true);
        assertThat(first.condensateL()).isBetween(800.0, 1100.0);
        fed += first.feedL();
        available -= first.feedL();
        condensate += first.condensateL();
        condBq += first.condensateBq();
        for (int i = 0; i < 400 && !ev.ready(); i++) {
            LrwEvaporator.Step s = ev.step(600, NPP_LRW, available, 1e9, true);
            fed += s.feedL();
            available -= s.feedL();
            condensate += s.condensateL();
            condBq += s.condensateBq();
        }
        assertThat(ev.ready()).isTrue();
        assertThat(ev.saltGL()).isBetween(285.0, 300.0 + 1e-6);
        // баланс солей и воды
        assertThat(ev.saltKg()).isCloseTo(fed * 5 / 1000, within(1e-6));
        assertThat(ev.waterL() + condensate).isCloseTo(fed, within(1e-6));
        // активность: почти вся в кубе, в конденсате — около 1/DF от упаренного раствора
        double total = fed * 3.7e6;
        assertThat(condBq / total).isLessThan(1e-3).isGreaterThan(1e-6);
        assertThat(ev.liquor().totalBq() + condBq).isCloseTo(total, within(total * 1e-3));
        // слив на цементирование уносит соль и активность пропорционально
        double before = ev.liquor().totalBq();
        WasteInventory part = ev.draw(ev.waterL() / 2);
        assertThat(part.totalBq()).isCloseTo(before / 2, within(before * 1e-9));
    }

    @Test
    void evaporatorStateRoundTrips() {
        LrwEvaporator ev = new LrwEvaporator();
        ev.step(3600, NPP_LRW, 5000, 1e9, true);
        MutableStateTree t = new MutableStateTree();
        ev.save(new TreeStateWriter(t));
        LrwEvaporator back = new LrwEvaporator();
        back.load(new TreeStateReader(t));
        assertThat(back.saltKg()).isEqualTo(ev.saltKg());
        assertThat(back.liquor().bq(Emitter.CS137)).isEqualTo(ev.liquor().bq(Emitter.CS137));
    }

    @Test
    void cementationOfEvaporatorConcentrate() {
        Conditioning.Recipe r = Conditioning.cement(300);
        assertThat(r.saltLoading()).isBetween(0.09, 0.11);
        assertThat(r.feedL()).isBetween(100.0, 125.0);
        assertThat(r.productKg() / r.productL()).isBetween(1.8, 2.0);
        assertThat(r.strengthMPa()).isGreaterThan(Conditioning.MIN_STRENGTH_MPA);
        assertThat(r.compliant()).isTrue();
        // Абрамс: 40 МПа при В/Ц 0,45 без солей
        assertThat(Conditioning.strengthMPa(0.45, 0)).isCloseTo(40, within(1.0));
        // пересоленный компаунд рассыпается
        Conditioning.Recipe salty = Conditioning.cement(900);
        assertThat(salty.saltLoading()).isGreaterThan(0.25);
        assertThat(salty.compliant()).isFalse();
    }

    @Test
    void bitumenTakesAboutThreeTimesMoreConcentratePerDrum() {
        Conditioning.Recipe c = Conditioning.cement(300), b = Conditioning.bitumen(300);
        assertThat(b.feedL() / c.feedL()).isBetween(2.3, 3.5);
        assertThat(b.saltLoading()).isEqualTo(Conditioning.BITUMEN_LOADING);
        assertThat(b.compliant()).isTrue();
        // вода выпаривается целиком
        assertThat(b.evaporatedKg()).isCloseTo(b.feedL() * (1 - 0.3 / Conditioning.SALT_KG_L), within(1e-9));
        Conditioning.Recipe resin = Conditioning.cementResin();
        assertThat(resin.feedL()).isCloseTo(63, within(1.0));
        assertThat(resin.compliant()).isTrue();
    }

    @Test
    void supercompactionReducesVolumeThreeToTenTimes() {
        double p = Supercompactor.pressureMPa(Supercompactor.FORCE_T);
        assertThat(p).isBetween(70.0, 85.0);
        assertThat(Supercompactor.volumeReduction(Material.DAW, p)).isBetween(3.0, 10.0);
        assertThat(Supercompactor.volumeReduction(Material.METAL, p)).isBetween(1.8, 3.0);
        assertThat(Supercompactor.relativeDensity(Material.DAW, p)).isLessThan(1);
        // ручной пресс на 50 т почти не уплотняет
        assertThat(Supercompactor.volumeReduction(Material.DAW, Supercompactor.pressureMPa(50))).isLessThan(1.5);
    }

    @Test
    void vitrificationLoadingAndHeatLimits() {
        // 100 кг оксидов при предельном включении R7T7 — 540 кг стекла; больше предела не включить
        assertThat(Vitrification.glassKg(Glass.BOROSILICATE, 100, 0.185)).isCloseTo(540.5, within(0.5));
        assertThat(Vitrification.glassKg(Glass.BOROSILICATE, 100, 0.30)).isCloseTo(540.5, within(0.5));
        assertThat(Vitrification.glassKg(Glass.PHOSPHATE, 100, 0.3)).isCloseTo(1000, within(1e-9));
        // CSD-V (400 кг) с 2 кВт в воздухе 50 °C: центр ниже стеклования
        double t = Vitrification.centreK(Glass.BOROSILICATE, 2000, 400, 323.15);
        assertThat(t).isBetween(500.0, Glass.BOROSILICATE.limitK);
        double maxB = Vitrification.maxHeatW(Glass.BOROSILICATE, 400, 323.15);
        assertThat(maxB).isBetween(2500.0, 6000.0);
        assertThat(Vitrification.maxHeatW(Glass.PHOSPHATE, 400, 323.15)).isLessThan(maxB);
        assertThat(Vitrification.centreK(Glass.BOROSILICATE, maxB, 400, 323.15)).isCloseTo(Glass.BOROSILICATE.limitK, within(1e-6));
    }

    @Test
    void ventilatedStoreRunsHotWhenOverloadedOrWithoutFans() {
        assertThat(InterimStore.outletK(293.15, 50_000, 50_000, true)).isCloseTo(323.15, within(1e-9));
        assertThat(InterimStore.outletK(293.15, 100_000, 50_000, true)).isCloseTo(353.15, within(1e-9));
        assertThat(InterimStore.outletK(293.15, 50_000, 50_000, false)).isGreaterThan(450);
    }
}

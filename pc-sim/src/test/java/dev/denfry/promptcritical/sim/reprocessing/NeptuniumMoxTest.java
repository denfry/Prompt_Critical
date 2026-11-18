package dev.denfry.promptcritical.sim.reprocessing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import dev.denfry.promptcritical.sim.enrichment.Criticality;
import dev.denfry.promptcritical.sim.fuel.MoxBlend;
import dev.denfry.promptcritical.sim.radiation.ActivityInventory;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Замыкание цикла I4 против литературы: Np-237 в облучённом топливе (BPL, OECD/NEA 2006), выделение Np в PUREX с
 * корректировкой валентности, смешение МОКС (5–8 % Pu) и эквивалентность Pu_fiss ↔ U-235 (0,7–0,8; EDF ≈ 0,66 по
 * кампании).
 */
class NeptuniumMoxTest {
    private static final ActivityInventory NONE = new ActivityInventory(Map.of(), 1, 3);

    @Test
    void neptuniumInventoryOfPwrFuel() {
        // 0,43–0,6 кг/т ТМ при 33 МВт·сут/кг, ≈ 0,7 кг/т при 50
        assertThat(ActinideBuildup.at(0.032, 33).np237PerHm() * 1000).isBetween(0.43, 0.6);
        assertThat(ActinideBuildup.at(0.042, 50).np237PerHm() * 1000).isBetween(0.6, 0.8);
        assertThat(ActinideBuildup.at(0.032, 0).np237PerHm()).isZero();
        // масса ТМ сохраняется: U + Pu + ПД + Np = 1
        ActinideBuildup.Composition c = ActinideBuildup.at(0.032, 33);
        assertThat(c.uPerHm() + c.puPerHm() + c.fpPerHm() + c.np237PerHm()).isCloseTo(1, within(1e-12));
    }

    @Test
    void purexRecoversNeptuniumOnlyWithValenceAdjustment() {
        ActinideBuildup.Composition c = ActinideBuildup.at(0.032, 33);
        PurexPlant adjusted = new PurexPlant();
        PurexPlant.Batch a = adjusted.process(1000, c, 0.0002, NONE, true);
        double np = 1000 * c.np237PerHm();
        // нитрит: Np(VI) экстрагируется, выделяется > 80 %
        assertThat(a.npKg() / np).isGreaterThan(0.8);
        assertThat(a.npKg() + a.npToHlwKg()).isLessThanOrEqualTo(np + 1e-12);
        // без корректировки Np(V) уходит в рафинат: большая часть — в стекло
        PurexPlant plain = new PurexPlant();
        plain.setNpValence(false);
        PurexPlant.Batch b = plain.process(1000, c, 0.0002, NONE, true);
        assertThat(b.npToHlwKg() / np).isGreaterThan(0.5);
        assertThat(b.npKg()).isLessThan(a.npKg() / 2);
    }

    @Test
    void moxBlendHitsTargetPuFraction() {
        // реакторный Pu (BPL, 33 МВт·сут/кг) на отвальном уране 0,25 %
        ActinideBuildup.Composition c = ActinideBuildup.at(0.032, 33);
        MoxBlend.Mix m = MoxBlend.blend(1, c.pu239(), c.pu240(), c.pu241(), c.pu242(), 100, 0.0025, 0.07);
        assertThat(m.puFraction()).isCloseTo(0.07, within(1e-9));
        assertThat(m.uKg()).isCloseTo(1 * 0.93 / 0.07, within(1e-9));
        // нехватка урана: Pu не переобогащается, лишний остаётся
        MoxBlend.Mix short_ = MoxBlend.blend(1, c.pu239(), c.pu240(), c.pu241(), c.pu242(), 5, 0.0025, 0.07);
        assertThat(short_.puFraction()).isCloseTo(0.07, within(1e-9));
        assertThat(short_.puKg()).isLessThan(1);
        // доля выше 12 % не лицензируется — срезается
        assertThat(MoxBlend.blend(1, 0.6, 0.25, 0.1, 0.05, 100, 0.0025, 0.3).puFraction()).isCloseTo(MoxBlend.MAX_PU, within(1e-9));
    }

    @Test
    void fissilePuEquivalenceMatchesPwrPractice() {
        assertThat(MoxBlend.PU_FISS_TO_U235).isBetween(0.7, 0.8);
        // EDF: 7,08 % реакторного Pu на отвале 0,25 % — замена UOX 3,25 % по кампании; на начало кампании МОКС
        // эквивалентен чуть большему обогащению, в пределах 3,25–4 %
        MoxBlend.Mix m = MoxBlend.blend(7.08, 0.54, 0.25, 0.10, 0.11, 1000, 0.0025, 0.0708);
        assertThat(m.equivalentU235()).isBetween(0.0325, 0.040);
        // чистый уран: эквивалент = обогащение
        assertThat(MoxBlend.equivalentEnrichment(0.04, 0)).isEqualTo(0.04);
    }

    @Test
    void blendingVesselLimitedBySafePuMass() {
        double safe = Criticality.safePuKg(0.23);
        assertThat(MoxBlend.vesselSafe(safe * 0.9, 0.23)).isTrue();
        assertThat(MoxBlend.vesselSafe(safe * 1.1, 0.23)).isFalse();
        // реакторный Pu (23 % Pu-240) допускает больше, чем оружейный
        assertThat(Criticality.safePuKg(0.23)).isGreaterThan(Criticality.safePuKg(0.01));
    }
}

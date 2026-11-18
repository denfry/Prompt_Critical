package dev.denfry.promptcritical.sim.reprocessing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import dev.denfry.promptcritical.sim.enrichment.Criticality;
import dev.denfry.promptcritical.sim.radiation.ActivityInventory;
import dev.denfry.promptcritical.sim.radiation.AirborneDose;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import dev.denfry.promptcritical.sim.radiation.SpentFuel;
import org.junit.jupiter.api.Test;

/**
 * PUREX против литературы: состав облучённого топлива (BPL, PWR 3,2 % / 33 МВт·сут/кг), коэффициенты распределения
 * ТБФ (BPL гл. 10, Schulz), потери U и Pu 0,1–0,5 %, очистка 10⁶–10⁷ за два цикла, разделение U/Pu, масса стекла,
 * безопасная масса Pu (LA-10860), газовые выбросы.
 */
class PurexTest {
    @Test
    void actinideCompositionMatchesPwrTable() {
        ActinideBuildup.Composition c = ActinideBuildup.at(0.032, 33);
        assertThat(c.u235()).isCloseTo(0.0084, within(0.0084 * 0.12));
        assertThat(c.u236()).isCloseTo(0.0041, within(0.0041 * 0.15));
        assertThat(c.puPerHm()).isCloseTo(0.0090, within(0.0090 * 0.15));
        assertThat(c.pu239()).isCloseTo(0.57, within(0.05));
        assertThat(c.pu240()).isCloseTo(0.23, within(0.03));
        assertThat(c.pu241()).isCloseTo(0.13, within(0.03));
        assertThat(c.fpPerHm()).isCloseTo(0.0347, within(0.003));
        assertThat(c.pu239() + c.pu240() + c.pu241() + c.pu242()).isCloseTo(1, within(1e-12));
        // котёл на природном уране, 0,1 МВт·сут/кг: оружейный плутоний, сотня граммов на тонну
        ActinideBuildup.Composition t0 = ActinideBuildup.at(0.00711, 0.1);
        assertThat(t0.pu239()).isGreaterThan(0.99);
        assertThat(t0.puPerHm() * 1000).isBetween(0.02, 0.05);
    }

    @Test
    void distributionCoefficientsOfTbp() {
        // BPL рис. 10.x, 30 % ТБФ, 3 М HNO₃: U(VI) ≈ 20, Pu(IV) ≈ 6, Pu(III) ≈ 0,01–0,02, Zr ≈ 0,1
        assertThat(PurexFlowsheet.Species.U6.d(3)).isBetween(15.0, 30.0);
        assertThat(PurexFlowsheet.Species.PU4.d(3)).isBetween(4.0, 9.0);
        assertThat(PurexFlowsheet.Species.PU3.d(3)).isLessThan(0.05);
        assertThat(PurexFlowsheet.Species.ZR.d(3)).isBetween(0.05, 0.3);
        // высаливание: при 1 М уран экстрагируется в разы хуже
        assertThat(PurexFlowsheet.Species.U6.d(1)).isLessThan(PurexFlowsheet.Species.U6.d(3) / 4);
        assertThat(PurexFlowsheet.unextracted(1, 8)).isCloseTo(1.0 / 9, within(1e-12));
    }

    @Test
    void decontaminationOverTwoCycles() {
        for (PurexFlowsheet.Species s : new PurexFlowsheet.Species[] {PurexFlowsheet.Species.ZR, PurexFlowsheet.Species.RU,
                PurexFlowsheet.Species.CS_SR})
            assertThat(PurexFlowsheet.decontamination(s, 3)).isBetween(1e6, 1e8);
        // слишком кислое питание тянет цирконий в экстракт
        assertThat(PurexFlowsheet.decontamination(PurexFlowsheet.Species.ZR, 6))
                .isLessThan(PurexFlowsheet.decontamination(PurexFlowsheet.Species.ZR, 2));
    }

    @Test
    void plantBalanceLossesAndPartition() {
        PurexPlant p = new PurexPlant();
        ActinideBuildup.Composition c = ActinideBuildup.at(0.032, 33);
        ActivityInventory act = SpentFuel.discharge(100, 33, 38, 3, true).decayed(3 * 365.25 * 86400);
        PurexPlant.Batch b = p.process(100, c, 0.0002, act, true);
        // потери: уран и плутоний 0,1–0,5 % (BPL табл. 10.x)
        assertThat(b.uLossKg() / (100 * c.uPerHm())).isBetween(0.001, 0.005);
        assertThat(b.puLossKg() / (100 * c.puPerHm())).isBetween(0.001, 0.005);
        assertThat(b.puInUKg()).isLessThan(1e-5 * 100 * c.puPerHm() + 1e-9);
        // баланс по урану и плутонию
        assertThat(p.ledger().inKg()).isCloseTo(p.ledger().outKg() + p.ledger().lossKg(), within(1e-9));
        assertThat(p.ledger().puInKg()).isCloseTo(p.ledger().puOutKg() + p.ledger().puLossKg() + p.puTankKg() + p.puInFlowKg(),
                within(1e-9));
        // регенерат несёт U-236
        assertThat(b.u236()).isGreaterThan(0.003);
        // стекло ≈ 0,1 м³ (≈ 280 кг) на тонну ТМ
        assertThat(b.glassKg() * 10).isBetween(200.0, 400.0);
        // криптон уходит весь, иод — через фильтр
        assertThat(b.kr85Bq()).isCloseTo(act.bq(Emitter.KR85), within(1.0));
        assertThat(b.hlw().bq(Emitter.CS137)).isCloseTo(act.bq(Emitter.CS137), within(1.0));
        // без восстановителя плутоний уходит с ураном
        PurexPlant q = new PurexPlant();
        q.setReductant(0.5);
        PurexPlant.Batch half = q.process(100, c, 0.0002, act, true);
        assertThat(half.puInUKg() / (half.puInUKg() + half.puToTankKg())).isCloseTo(0.5, within(0.01));
    }

    @Test
    void plutoniumTankCriticality() {
        assertThat(Criticality.minCriticalPuKg(0.05)).isCloseTo(0.51, within(1e-9));
        assertThat(Criticality.safePuKg(0.05)).isCloseTo(0.23, within(0.005));
        // реакторный плутоний с 23 % Pu-240 — критмасса выше
        assertThat(Criticality.minCriticalPuKg(0.23)).isGreaterThan(0.6);
        PurexPlant p = new PurexPlant();
        ActinideBuildup.Composition c = ActinideBuildup.at(0.032, 33);
        ActivityInventory act = SpentFuel.discharge(100, 33, 38, 3, true);
        p.process(100, c, 0.0002, act, true); // ≈ 0,9 кг Pu идёт в буфер потоком
        assertThat(p.puTankKg()).isZero();
        p.meter(0.1);
        assertThat(p.criticalityFissions()).isZero(); // 100 г — меньше безопасной массы
        p.meter(10); // осадитель встал: весь плутоний в буфере
        assertThat(p.puTankAboveSafe()).isTrue();
        assertThat(p.criticalityFissions()).isGreaterThan(1e17);
        double pu = p.precipitate(10);
        assertThat(p.puTankKg()).isZero();
        assertThat(p.criticalityFissions()).isZero();
        assertThat(pu).isCloseTo(100 * c.puPerHm() * (1 - p.puLossFraction()) * (1 - p.puUnreduced()) * 0.995, within(1e-6));
    }

    @Test
    void iodineFilterAndAirborneDose() {
        PurexPlant p = new PurexPlant();
        ActinideBuildup.Composition c = ActinideBuildup.at(0.032, 33);
        ActivityInventory fresh = SpentFuel.discharge(10, 33, 38, 3, true).decayed(30 * 86400);
        double withFilter = p.process(10, c, 0.0002, fresh, true).i131ReleasedBq();
        double without = p.process(10, c, 0.0002, fresh, false).i131ReleasedBq();
        assertThat(without / withFilter).isCloseTo(1000, within(1e-6));
        // I-131: ингаляция ≫ погружения; 1 кБк/м³ — около 9 мкЗв/ч
        assertThat(AirborneDose.svH(Emitter.I131, 1000) * 1e6).isBetween(8.0, 10.5);
        // Kr-85: 1 МБк/м³ — около 0,4 мкЗв/ч
        assertThat(AirborneDose.svH(Emitter.KR85, 1e6) * 1e6).isBetween(0.3, 0.5);
    }
}

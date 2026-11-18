package dev.denfry.promptcritical.sim.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.drilling.FormationFluid;
import dev.denfry.promptcritical.sim.drilling.Trap;
import org.junit.jupiter.api.Test;

class ProductionTest {
    private static final double PSI = 6894.757, BPD = 0.158_987 / 86_400, G = 9.806_65;

    /**
     * Пример Вогеля (Brown, т. 1, пример 1.2): p̄ = 2400 psi, замер 100 барр/сут при p_wf = 1800 psi →
     * q_max = 100/(1 − 0,15 − 0,45) = 250 барр/сут; при p_wf = 1200 psi — 250·(1 − 0,1 − 0,2) = 175 барр/сут.
     */
    @Test
    void vogelExample() {
        double qmax = Inflow.vogelMaxRate(100 * BPD, 1800 * PSI, 2400 * PSI);
        assertThat(qmax / BPD).isCloseTo(250, within(1e-9));
        Inflow ipr = new Inflow(2400 * PSI, 2400 * PSI, qmax * 1.8 / (2400 * PSI));
        assertThat(ipr.maxRateM3s() / BPD).isCloseTo(250, within(1e-6));
        assertThat(ipr.rateM3s(1200 * PSI) / BPD).isCloseTo(175, within(1e-6));
        assertThat(ipr.pwfAtPa(175 * BPD) / PSI).isCloseTo(1200, within(0.01));
    }

    /** Составная кривая: выше p_b — прямая J·Δp, на p_b непрерывна, ниже отстаёт от прямой. */
    @Test
    void compositeIprIsContinuousAtBubblePoint() {
        Inflow ipr = new Inflow(20e6, 15e6, 1e-9);
        assertThat(ipr.rateM3s(17e6)).isCloseTo(3e-3, within(1e-12));
        assertThat(ipr.rateM3s(15e6 + 1)).isCloseTo(ipr.rateM3s(15e6 - 1), within(1e-8));
        assertThat(ipr.rateM3s(5e6)).isLessThan(1e-9 * 15e6);
        assertThat(ipr.maxRateM3s()).isCloseTo(5e-3 + 1e-9 * 15e6 / 1.8, within(1e-12));
    }

    /** Гилберт: R = 1 тыс. фут³/барр, 600 барр/сут, штуцер 32/64″ → 435·600/32^1,89 ≈ 373 psi. */
    @Test
    void gilbertChoke() {
        double glr = 1000 / 5.614_6;
        double p = Tubing.gilbertWellheadPa(600 * BPD, glr, 32) / PSI;
        assertThat(p).isCloseTo(435 * 600 / Math.pow(32, 1.89), within(0.01));
        assertThat(p).isCloseTo(373, within(1.0));
    }

    /**
     * Проскальзывание газа: при почти нулевом дебите столб с газовым фактором 90 почти жидкий (как статика), а не
     * облегчённый газом; пласт с пониженным давлением (8 кПа/м) из закрытого состояния не фонтанирует.
     */
    @Test
    void slipKeepsALowRateColumnHeavyAndALowPressureWellDoesNotKickOff() {
        ProducingReservoir r = oilField(8_000);
        Inflow ipr = r.inflow();
        double stat = Tubing.bottomholePa(0.6e6, 0, r.gasOilRatio(), r.waterCut(), r.datumM(), ipr.bubblePointPa());
        double creep = Tubing.bottomholePa(0.6e6, ipr.maxRateM3s() * 1e-6, r.gasOilRatio(), r.waterCut(), r.datumM(), ipr.bubblePointPa());
        assertThat(creep).isCloseTo(stat, within(0.02 * stat));
        assertThat(Tubing.kicksOff(ipr, r.gasOilRatio(), r.waterCut(), r.datumM(), 0.6e6)).isFalse();
        ProductionWell w = new ProductionWell(r);
        w.setChoke64(32);
        w.step(1, true, 0, 0.6e6);
        assertThat(w.mode()).isEqualTo(ProductionWell.Mode.DEAD);
        assertThat(Tubing.kicksOff(oilField(12_500).inflow(), 90, 0.02, 930, 0.6e6)).isTrue();
    }

    @Test
    void staticWaterColumnIsHydrostatic() {
        assertThat(Tubing.bottomholePa(1e6, 0, 0, 1, 1500, 10e6)).isCloseTo(1e6 + 1030 * G * 1500, within(1.0));
    }

    private static ProducingReservoir oilField(double gradientPaM) {
        Trap t = new Trap(0, 0, 100, 900, 80, 960, 20, 0.22, 300, FormationFluid.OIL, gradientPaM);
        return ProducingReservoir.perforate(t, 900);
    }

    /** Свежая залежь с АВПД фонтанирует; штуцер больше — дебит больше; с истощением фонтан умирает. */
    @Test
    void flowingWellDiesWithDepletion() {
        ProductionWell w = new ProductionWell(oilField(12_500));
        w.setChoke64(12);
        w.step(1, true, 0, 0.6e6);
        assertThat(w.mode()).isEqualTo(ProductionWell.Mode.FLOWING);
        double small = w.oilM3s();
        assertThat(w.wellheadPa()).isGreaterThan(0.6e6);
        w.setChoke64(32);
        w.step(1, true, 0, 0.6e6);
        assertThat(w.oilM3s()).isGreaterThan(small);
        assertThat(w.gasStdM3s()).isCloseTo(w.oilM3s() * ProducingReservoir.RS_M3M3, within(1e-12));
        double t = 0;
        while (w.mode() == ProductionWell.Mode.FLOWING && t < 5 * 365 * 86_400) {
            w.step(3600, true, 0, 0.6e6);
            t += 3600;
        }
        assertThat(w.mode()).isEqualTo(ProductionWell.Mode.DEAD);
        assertThat(w.reservoir().pressurePa()).isLessThan(w.reservoir().initialPressurePa());
        assertThat(w.reservoir().producedOilM3()).isPositive();
    }

    /** Кинематика: ход ≈ 2R·A/C, наибольшее ускорение — порядка (S/2)ω² (гармоника), простое движение повторяется. */
    @Test
    void pumpingUnitKinematics() {
        double s = BeamPump.strokeM();
        assertThat(s).isCloseTo(2 * BeamPump.R * BeamPump.A / BeamPump.C, within(0.1 * s));
        double spm = 8, w = spm * 2 * Math.PI / 60, amax = 0;
        for (int i = 0; i < 360; i++) amax = Math.max(amax, Math.abs(BeamPump.acceleration(Math.toRadians(i), spm)));
        assertThat(amax / (s / 2 * w * w)).isBetween(0.8, 1.8);
        assertThat(BeamPump.position(0.3)).isCloseTo(BeamPump.position(0.3 + 2 * Math.PI), within(1e-12));
    }

    /**
     * Динамограмма: на ходу вверх нагрузка выше веса штанг в жидкости на нагрузку столба, на ходу вниз — ниже; полное
     * заполнение — ход плунжера равен ходу штока минус растяжение штанг; неполное — средняя мощность меньше.
     */
    @Test
    void dynamometerCard() {
        double l = 900, rho = 900, fo = BeamPump.PLUNGER_AREA_M2 * rho * G * l;
        BeamPump.Card full = BeamPump.card(8, l, rho, fo, 1);
        double wrf = BeamPump.ROD_KG_PER_M * l * G * (1 - rho / BeamPump.STEEL_KGM3);
        assertThat(full.peakN()).isGreaterThan(wrf + fo);
        assertThat(full.minN()).isLessThan(wrf);
        assertThat(full.plungerStrokeM()).isCloseTo(BeamPump.strokeM() - BeamPump.rodStretchM(fo, l), within(1e-9));
        assertThat(full.positionM()).hasSize(BeamPump.CARD_POINTS);
        // полезная работа за цикл ≈ подъём жидкости: F_o·S_п на ход
        double lift = fo * full.plungerStrokeM() * 8 / 60 / BeamPump.SYSTEM_EFFICIENCY;
        assertThat(full.shaftPowerW()).isCloseTo(lift, within(0.15 * lift));
        // удар о жидкость: штанги держат столб дольше на ходу вниз — площадь карты (работа) меньше
        BeamPump.Card pounding = BeamPump.card(8, l, rho, fo, 0.5);
        assertThat(pounding.shaftPowerW()).isCloseTo(full.shaftPowerW() * 0.5, within(0.15 * full.shaftPowerW()));
    }

    /**
     * Залежь с гидростатическим давлением сначала фонтанирует за счёт газа (90 м³/м³), после истощения — нет; насос
     * быстрее притока — откачка до уровня и заполнение меньше 1; медленнее — уровень стоит, заполнение 1.
     */
    @Test
    void rodPumpMatchesInflow() {
        ProductionWell w = new ProductionWell(oilField(10_200));
        w.setChoke64(32);
        for (int i = 0; i < 20_000 && w.mode() != ProductionWell.Mode.DEAD; i++) w.step(3600, true, 0, 0.6e6);
        assertThat(w.mode()).isEqualTo(ProductionWell.Mode.DEAD);
        w.step(1, true, 12, 0.6e6);
        assertThat(w.mode()).isEqualTo(ProductionWell.Mode.PUMPING);
        assertThat(w.oilM3s()).isPositive();
        assertThat(w.card()).isNotNull();
        double fast = w.fillage();
        w.step(1, true, 2, 0.6e6);
        assertThat(w.fillage()).isGreaterThanOrEqualTo(fast);
        assertThat(w.oilM3s()).isLessThanOrEqualTo(w.reservoir().inflow().maxRateM3s());
    }

    /** Стокс: капля воды 100 мкм в нефти (Δρ 180 кг/м³, 5 мПа·с) оседает со скоростью 0,196 мм/с. */
    @Test
    void separatorSettlesBySokesLaw() {
        assertThat(ThreePhaseSeparator.stokesMs(100e-6, 180, 5e-3)).isCloseTo(1.96e-4, within(0.01e-4));
        ThreePhaseSeparator s = ThreePhaseSeparator.STANDARD;
        ThreePhaseSeparator.Result slow = s.separate(5e-4, 2e-4, 0.05, 850);
        ThreePhaseSeparator.Result fast = s.separate(5e-3, 2e-3, 0.05, 850);
        assertThat(fast.waterInOil()).isGreaterThan(slow.waterInOil());
        assertThat(slow.oilRetentionS()).isGreaterThan(180);
        assertThat(slow.oilM3s() + slow.waterM3s()).isCloseTo(7e-4, within(1e-12));
        assertThat(slow.liquidCarryover()).isZero();
        assertThat(s.separate(5e-4, 2e-4, 50, 850).liquidCarryover()).isPositive();
    }
}

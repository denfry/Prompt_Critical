package dev.denfry.promptcritical.sim.economy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Экономика раздела 14 против тест-кейсов §14.16: эластичный спрос (T-14-12), скоропортящийся Mo-99 (T-14-13), квоты
 * CO₂ (T-14-14), контракт SUPPLY (T-14-15), E_SAME_TEAM (T-14-16); плюс свойства цены §14.6.2 и антиэксплойт §14.8.2.
 */
class EconomyTest {
    private static final UUID TEAM = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void elasticSaleMatchesT14_12() {
        assertThat(ElasticDemand.saleRevenue(100, 10, 0.5, 0, 10)).isCloseTo(500, within(0.01));
        assertThat(ElasticDemand.saleRevenue(100, 10, 2, 0, 10)).isCloseTo(828.4, within(0.1));
        assertThat(ElasticDemand.saleRevenue(100, 10, 0.5, 0, 1e9)).isLessThan(1000);
        assertThat(ElasticDemand.saleRevenueLimit(100, 10, 0.5, 0)).isCloseTo(1000, within(1e-9));
        // ε = 1 — логарифмическая ветвь
        assertThat(ElasticDemand.saleRevenue(100, 10, 1, 0, 10)).isCloseTo(1000 * Math.log(2), within(1e-9));
    }

    @Test
    void revenueInverseRoundTrips() {
        for (double eps : new double[] {0.5, 1, 2, 4}) {
            double r = ElasticDemand.saleRevenue(100, 10, eps, 7, 13);
            assertThat(ElasticDemand.volumeForRevenue(100, 10, eps, 7, r)).isCloseTo(13, within(1e-9));
        }
        assertThat(ElasticDemand.volumeForRevenue(100, 10, 0.5, 0, 1000)).isInfinite();
    }

    @Test
    void sellingInPiecesEarnsTheSameAsOneBatchAndVolumeRecovers() {
        double whole = ElasticDemand.saleRevenue(100, 10, 2, 0, 10);
        double halves = ElasticDemand.saleRevenue(100, 10, 2, 0, 5) + ElasticDemand.saleRevenue(100, 10, 2, 5, 5);
        assertThat(halves).isCloseTo(whole, within(1e-9));
        // за τ_rec = 2 сут объём падает в e раз
        assertThat(ElasticDemand.recover(10, ElasticDemand.DEFAULT_RECOVERY_S, ElasticDemand.DEFAULT_RECOVERY_S))
                .isCloseTo(10 / Math.E, within(1e-12));
    }

    @Test
    void mo99LosesValueT14_13() {
        // T½ = 65,94 ч физики = 54,95 реальной минуты при K_slow = 72; 120 реальных минут = 120·72 физических минут
        var mo99 = new Perishability.Decay("mo99", 65.94 * 3600, 0.25);
        double age = 120 * 60 * 72;
        assertThat(mo99.valueFraction(age)).isCloseTo(0.220, within(0.002));
        assertThat(mo99.accepts(age)).isFalse();
        assertThat(mo99.accepts(60 * 60 * 72)).isTrue();
    }

    @Test
    void quotaSettlementMatchesT14_14() {
        double e = QuotaLedger.co2e(1150, 2);
        var acc = new QuotaAccount(TEAM, 1000, 1000, 0, 0);
        var s = QuotaLedger.settle(acc, e, QuotaLedger.CALCULATED_FACTOR, QuotaLedger.DEFAULT_PENALTY_CENTS_PER_T, 950);
        assertThat(s.obligationT()).isCloseTo(1447.2, within(1e-9));
        assertThat(s.deficitT()).isCloseTo(447.2, within(1e-9));
        assertThat(s.fineCents()).isEqualTo(44_720_00L);
        assertThat(s.next().carriedDebtT()).isCloseTo(447.2, within(1e-9));
        assertThat(s.next().heldT()).isCloseTo(950, within(1e-9));
        assertThat(QuotaLedger.allocation(1000, QuotaLedger.DEFAULT_DECLINE, 1)).isCloseTo(950, within(1e-9));
    }

    @Test
    void quotaSurplusCarriesOverWithoutFine() {
        var acc = new QuotaAccount(TEAM, 1000, 1000, 0, 0);
        var s = QuotaLedger.settle(acc, 600, 1.0, QuotaLedger.DEFAULT_PENALTY_CENTS_PER_T, 950);
        assertThat(s.fineCents()).isZero();
        assertThat(s.next().heldT()).isCloseTo(400 + 950, within(1e-9));
        assertThat(QuotaLedger.trade(s.next(), -400).heldT()).isCloseTo(950, within(1e-9));
    }

    @Test
    void supplyContractPenaltyAndDefaultT14_15() {
        var c = SupplyContract.propose(1, TEAM, OTHER, "methanol", 100, 50_00, 10).accept();
        assertThat(c.depositCents()).isEqualTo(1000_00L);
        var s = c.settlePeriod(80, 0, 0);
        assertThat(s.penaltyCents()).isEqualTo(200_00L);
        assertThat(s.paymentCents()).isEqualTo(4000_00L);
        assertThat(s.state()).isEqualTo(ContractState.ACTIVE);
        int streak = 0;
        SupplyContract.Settlement last = null;
        for (int i = 0; i < 3; i++) {
            last = c.settlePeriod(40, streak, 0);
            streak = last.shortStreak();
        }
        assertThat(last.state()).isEqualTo(ContractState.DEFAULTED);
        assertThat(last.depositToBuyerCents()).isEqualTo(c.depositCents());
        // неустойка не больше депозита
        assertThat(c.settlePeriod(0, 0, 100).penaltyCents()).isEqualTo(c.depositCents());
    }

    @Test
    void sameTeamContractIsRejectedT14_16() {
        assertThat(SupplyContract.validateParties(TEAM, TEAM)).contains(SupplyContract.Error.E_SAME_TEAM);
        assertThat(SupplyContract.validateParties(TEAM, OTHER)).isEmpty();
        assertThat(SupplyContract.validateParties(TEAM, null)).isEmpty();
    }

    @Test
    void ouStepHasTheStationaryDeviationOfTenPercent() {
        // много шагов по 1 игровому часу: стандартное отклонение x → σ/√(2θ) = 0,10
        double x = 0, sum2 = 0;
        int n = 200_000;
        for (int i = 0; i < n; i++) {
            x = PriceProcess.step(x, Commodity.DEFAULT_THETA, Commodity.DEFAULT_SIGMA, 3600,
                    PriceProcess.gaussian(42, "coal", i));
            sum2 += x * x;
        }
        assertThat(Math.sqrt(sum2 / n)).isCloseTo(0.10, within(0.01));
    }

    @Test
    void marketIsDeterministicForASeed() {
        NpcMarket a = market(7), b = market(7), c = market(8);
        for (int i = 0; i < 50; i++) {
            a.step(3600);
            b.step(3600);
            c.step(3600);
        }
        assertThat(a.price("coal")).isEqualTo(b.price("coal"));
        assertThat(a.price("coal")).isNotEqualTo(c.price("coal"));
    }

    @Test
    void trainingModeHasNoNoise() {
        var m = new NpcMarket(1, new MarketParams(0.04, ElasticDemand.DEFAULT_RECOVERY_S, 5_000_000L,
                7 * Commodity.DAY_S, 0), List.of(coal()), t -> 0);
        for (int i = 0; i < 20; i++) m.step(3600);
        assertThat(m.price("coal")).isCloseTo(100_00, within(1e-6));
    }

    @Test
    void saleThroughMarketPaysElasticRevenueAndDepressesBid() {
        NpcMarket m = market(1, new MarketParams(0.04, ElasticDemand.DEFAULT_RECOVERY_S, 5_000_000L,
                7 * Commodity.DAY_S, 0));
        long bid0 = m.quote("coal").bidCents();
        Fill f = m.execute(TEAM, new Order("coal", -10, 0));
        assertThat(f.status()).isEqualTo(FillStatus.FILLED);
        assertThat(f.totalCents()).isEqualTo(828_42L); // T-14-12 при ε = 2 в центах
        assertThat(m.account(TEAM).balanceCents()).isEqualTo(828_42L);
        assertThat(m.quote("coal").bidCents()).isLessThan(bid0);
        // за сутки объём частично рассасывается и цена восстанавливается
        long bidAfter = m.quote("coal").bidCents();
        m.step(Commodity.DAY_S);
        assertThat(m.quote("coal").bidCents()).isGreaterThan(bidAfter);
    }

    @Test
    void buyAndSellBackLoses() {
        NpcMarket m = market(1, new MarketParams(0.04, ElasticDemand.DEFAULT_RECOVERY_S, 5_000_000L,
                7 * Commodity.DAY_S, 0));
        m.transfer(TEAM, 10_000_00);
        Fill buy = m.execute(TEAM, new Order("coal", 5, 0));
        Fill sell = m.execute(TEAM, new Order("coal", -5, 0));
        assertThat(buy.status()).isEqualTo(FillStatus.FILLED);
        assertThat(sell.totalCents()).isLessThan(buy.totalCents());
    }

    @Test
    void dailyCapLimitsRevenueToWholeUnits() {
        NpcMarket m = market(1, new MarketParams(0.04, ElasticDemand.DEFAULT_RECOVERY_S, 500_00L,
                7 * Commodity.DAY_S, 0));
        Fill f = m.execute(TEAM, new Order("coal", -10, 0));
        assertThat(f.status()).isEqualTo(FillStatus.PARTIAL);
        assertThat(f.qty()).isEqualTo(Math.rint(f.qty())).isLessThan(0);
        assertThat(f.totalCents()).isLessThanOrEqualTo(500_00L);
        assertThat(m.execute(TEAM, new Order("coal", -10, 0)).status()).isIn(FillStatus.PARTIAL, FillStatus.REJECTED_CAP);
        m.step(Commodity.DAY_S);
        assertThat(m.account(TEAM).salesTodayCents()).isZero();
    }

    @Test
    void epochAndRestrictionGateNpcSales() {
        var fuel = new Commodity("fuel_assembly", "pcs", 1_000_00, 4, false, false, 1, 0.5, 0,
                Commodity.DEFAULT_THETA, 0, null, true);
        var iso = Commodity.standard("co60_source", "TBq", 50_00, 4, 10, 0.5, null);
        var m = new NpcMarket(1, MarketParams.DEFAULTS, List.of(fuel, iso), t -> 2);
        m.transfer(TEAM, 1_000_000_00L);
        assertThat(m.execute(TEAM, new Order("fuel_assembly", 1, 0)).status()).isEqualTo(FillStatus.REJECTED_RESTRICTED);
        assertThat(m.execute(TEAM, new Order("co60_source", 1, 0)).status()).isEqualTo(FillStatus.REJECTED_EPOCH);
        assertThat(m.execute(TEAM, new Order("nothing", 1, 0)).status()).isEqualTo(FillStatus.REJECTED_UNKNOWN);
    }

    @Test
    void pollutionFeeAppliesElvSurcharge() {
        // 1000 кг SO₂, из них 100 сверх ELV, CALCULATED: (900 + 5·100)·1,2·0,5 CR = 840 CR
        assertThat(PollutionFee.SO2.feeCents(1000, 100, 1.2)).isEqualTo(840_00L);
    }

    private static Commodity coal() {
        return Commodity.standard("coal", "t", 100_00, 0, 10, 2, null);
    }

    private static NpcMarket market(long seed) {
        return market(seed, MarketParams.DEFAULTS);
    }

    private static NpcMarket market(long seed, MarketParams p) {
        return new NpcMarket(seed, p, List.of(coal()), t -> 0);
    }
}

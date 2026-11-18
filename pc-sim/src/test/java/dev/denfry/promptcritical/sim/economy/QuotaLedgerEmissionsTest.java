package dev.denfry.promptcritical.sim.economy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.environment.EmissionLedger;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Квоты CO₂ списываются по журналу выбросов: рассчитанный источник платит на 20 % больше измеренного (14 §14.1.4). */
class QuotaLedgerEmissionsTest {
    @Test
    void periodEmissionsSurrenderAllowances() {
        EmissionLedger l = new EmissionLedger();
        // 1000 т CO₂ от котла без САКВ и 500 т от котла с САКВ
        l.record("a", EmissionLedger.Category.BOILER, EmissionLedger.Pollutant.CO2, 1000, 1000);
        l.record("b", EmissionLedger.Category.BOILER, EmissionLedger.Pollutant.CO2, 500, 1000);
        l.source("b", EmissionLedger.Category.BOILER).method = EmissionLedger.Method.MEASURED;
        QuotaAccount acc = new QuotaAccount(UUID.randomUUID(), 1500, 1500, 0, 0);
        QuotaLedger.Settlement s = QuotaLedger.settle(acc, l, QuotaLedger.DEFAULT_PENALTY_CENTS_PER_T, 1425);
        assertThat(s.obligationT()).isCloseTo(1000 * 1.2 + 500, within(1e-9));
        assertThat(s.surrenderedT()).isCloseTo(1500, within(1e-9));
        assertThat(s.deficitT()).isCloseTo(200, within(1e-9));
        assertThat(s.fineCents()).isEqualTo(200 * QuotaLedger.DEFAULT_PENALTY_CENTS_PER_T);
        // журнал начал новый период, долг перенесён
        assertThat(l.reportedCo2eT()).isZero();
        assertThat(s.next().carriedDebtT()).isCloseTo(200, within(1e-9));
    }
}

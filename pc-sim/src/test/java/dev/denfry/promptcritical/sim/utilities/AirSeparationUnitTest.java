package dev.denfry.promptcritical.sim.utilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** ВРУ (08 §8.11): захолаживание, продукт по мощности, адсорберы, углеводороды в LOX. */
class AirSeparationUnitTest {
    private static final UtilitiesConfig CFG = UtilitiesConfig.defaults();
    private static final double W = 2e6, CW = 100, DT = 600;

    private static final class Log implements UtilitiesEvents {
        final List<AsuState> states = new ArrayList<>();
        int breakthroughs, alarms;
        double explosionJ;

        @Override public void asuStateChanged(AsuState from, AsuState to) { states.add(to); }
        @Override public void asuMolsieveBreakthrough() { breakthroughs++; }
        @Override public void asuHydrocarbonHigh(double ppm) { alarms++; }
        @Override public void asuExploded(double energyJ) { explosionJ = energyJ; }
    }

    /** Захолаживание при номинальном компрессоре; время в физ. ч. */
    private static double cooldown(AirSeparationUnit asu) {
        double t = 0;
        while (asu.state() != AsuState.RUNNING && t < 100 * 3600) {
            asu.stepSlow(DT, W, CW);
            t += DT;
        }
        return t / 3600;
    }

    /** {@code asu.cooldown_hours_phys} = 36: продукта нет, пока блок не дошёл до рабочей температуры. */
    @Test
    void cooldownTakesConfiguredTime() {
        Log log = new Log();
        AirSeparationUnit asu = new AirSeparationUnit(AsuSpec.defaults(), CFG, log);
        asu.stepSlow(DT, W, CW);
        assertThat(asu.state()).isEqualTo(AsuState.COOLDOWN);
        assertThat(asu.productKgS("oxygen")).isZero();
        assertThat(cooldown(asu) + DT / 3600).isCloseTo(36, within(0.02 * 36));
        assertThat(asu.coldBoxK()).isEqualTo(AirSeparationUnit.RUNNING_K);
        assertThat(log.states).containsExactly(AsuState.COOLDOWN, AsuState.RUNNING);
    }

    /** 08 §8.11: компрессор 2 МВт → 2000/0,42 = 4760 нм³/ч O₂ 99,5 %; N₂ и Ar попутно. */
    @Test
    void productRateFromCompressorPower() {
        AirSeparationUnit asu = new AirSeparationUnit(AsuSpec.defaults(), CFG, UtilitiesEvents.NONE);
        cooldown(asu);
        asu.stepSlow(DT, W, CW);
        assertThat(asu.oxygenNm3S() * 3600).isCloseTo(4762, within(0.01 * 4762));
        assertThat(asu.productKgS("oxygen")).isCloseTo(asu.oxygenNm3S() * AirSeparationUnit.RHO_O2, within(1e-12));
        assertThat(asu.oxygenPurity()).isEqualTo(AirSeparationUnit.O2_PURITY);
        assertThat(asu.productKgS("nitrogen")).isGreaterThan(asu.productKgS("oxygen"));
        double airNm3S = asu.oxygenNm3S() / (AirSeparationUnit.AIR_O2 * AirSeparationUnit.O2_RECOVERY);
        assertThat(asu.productKgS("argon"))
                .isCloseTo(airNm3S * AirSeparationUnit.AIR_AR * AirSeparationUnit.AR_RECOVERY * AirSeparationUnit.RHO_AR,
                        within(1e-9));
        assertThat(asu.productKgS("liquid_oxygen")).isZero();

        // половина охлаждающей воды — половина мощности: продукт вдвое меньше, чистота ниже
        asu.stepSlow(DT, W, W / (4186 * AirSeparationUnit.COOLING_WATER_DT) / 2);
        assertThat(asu.state()).isEqualTo(AsuState.RUNNING);
        assertThat(asu.oxygenNm3S() * 3600).isCloseTo(4762 / 2.0, within(0.01 * 4762));
        assertThat(asu.oxygenPurity()).isBetween(AirSeparationUnit.LOW_LOAD_PURITY, AirSeparationUnit.O2_PURITY);

        // жидкий продукт забирает мощность
        asu.setLiquidFraction(0.2, true);
        asu.stepSlow(DT, W, CW);
        assertThat(asu.productKgS("liquid_oxygen")).isCloseTo(0.2 * W / (0.8 * 3.6e6), within(1e-9));
        assertThat(asu.oxygenNm3S() * 3600).isCloseTo(0.8 * 4762, within(0.01 * 4762));

        // ниже минимальной нагрузки колонна не держит режим и блок греется
        for (int k = 0; k < 6; k++) asu.stepSlow(DT, 0.2 * W, CW);
        assertThat(asu.state()).isEqualTo(AsuState.COOLDOWN);
        assertThat(asu.productKgS("oxygen")).isZero();
        assertThat(asu.coldBoxK()).isGreaterThan(AirSeparationUnit.RUNNING_K);
    }

    /** Нагреватель регенерации выключен: второй адсорбер не готов, рабочий проскакивает — лёд, ICED; отогрев → OFF. */
    @Test
    void molsieveBreakthroughIcesExchanger() {
        Log log = new Log();
        AirSeparationUnit asu = new AirSeparationUnit(AsuSpec.defaults(), CFG, log);
        cooldown(asu);
        asu.setRegenerationHeater(false);
        double t = 0;
        while (asu.state() == AsuState.RUNNING && t < 48 * 3600) {
            asu.stepSlow(DT, W, CW);
            t += DT;
        }
        assertThat(log.breakthroughs).isEqualTo(1);
        assertThat(asu.state()).isEqualTo(AsuState.ICED);
        assertThat(t / 3600).as("цикл 4 ч + запас ёмкости + лёд").isBetween(6.0, 14.0);
        assertThat(asu.productKgS("oxygen")).isZero();
        for (int k = 0; k < 2000 && asu.state() == AsuState.ICED; k++) asu.stepSlow(DT, 0, 0);
        assertThat(asu.state()).isEqualTo(AsuState.OFF);
        assertThat(asu.iceKg()).isZero();
    }

    /** С регенерацией адсорберы чередуются, проскока нет сутками. */
    @Test
    void molsieveAlternatesWithRegeneration() {
        Log log = new Log();
        AirSeparationUnit asu = new AirSeparationUnit(AsuSpec.defaults(), CFG, log);
        cooldown(asu);
        for (int k = 0; k < 72 * 6; k++) asu.stepSlow(DT, W, CW);
        assertThat(log.breakthroughs).isZero();
        assertThat(asu.molsieveLoading()).isLessThan(1);
        assertThat(asu.state()).isEqualTo(AsuState.RUNNING);
    }

    /**
     * Ацетилен с воздухом у коксохима проходит через неисправные адсорберы и копится в LOX: на 1 ppm — сигнал и
     * останов; слив LOX сбрасывает. С выведенной защитой — взрыв конденсатора-испарителя на 5 ppm.
     */
    @Test
    void hydrocarbonsInLox() {
        Log log = new Log();
        AirSeparationUnit asu = new AirSeparationUnit(AsuSpec.defaults(), CFG, log);
        cooldown(asu);
        asu.setInletHydrocarbons(0.5);
        asu.setRegenerationHeater(false);
        for (int k = 0; k < 200 && asu.state() == AsuState.RUNNING; k++) asu.stepSlow(DT, W, CW);
        assertThat(asu.state()).isEqualTo(AsuState.TRIPPED);
        assertThat(asu.loxHydrocarbonsPpm()).isBetween(1.0, 1.5);
        assertThat(log.alarms).isEqualTo(1);
        asu.stepSlow(DT, W, CW);
        assertThat(asu.productKgS("oxygen")).isZero();
        asu.drainLox();
        assertThat(asu.loxHydrocarbonsPpm()).isZero();
        assertThat(asu.state()).isEqualTo(AsuState.OFF);

        Log log2 = new Log();
        AirSeparationUnit bypassed = new AirSeparationUnit(AsuSpec.defaults(), CFG, log2);
        cooldown(bypassed);
        bypassed.setInletHydrocarbons(0.5);
        bypassed.setRegenerationHeater(false);
        bypassed.setHydrocarbonTrip(false);
        for (int k = 0; k < 400 && bypassed.state() == AsuState.RUNNING; k++) bypassed.stepSlow(DT, W, CW);
        assertThat(bypassed.state()).isEqualTo(AsuState.DESTROYED);
        assertThat(log2.explosionJ).isPositive().isEqualTo(bypassed.explosionEnergyJ());
        bypassed.stepSlow(DT, W, CW);
        assertThat(bypassed.productKgS("oxygen")).isZero();
    }

    /** Исправные адсорберы и почти чистый воздух: углеводородов в LOX мало. */
    @Test
    void cleanAirKeepsLoxClean() {
        AirSeparationUnit asu = new AirSeparationUnit(AsuSpec.defaults(), CFG, UtilitiesEvents.NONE);
        cooldown(asu);
        asu.setInletHydrocarbons(0.05);
        for (int k = 0; k < 6 * 24; k++) asu.stepSlow(DT, W, CW);
        assertThat(asu.loxHydrocarbonsPpm()).isLessThan(0.1);
        assertThat(asu.state()).isEqualTo(AsuState.RUNNING);
    }
}

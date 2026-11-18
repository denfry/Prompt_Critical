package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.environment.AirQualityService;
import dev.denfry.promptcritical.mod.market.MarketData;
import dev.denfry.promptcritical.mod.market.PlantAccounting;
import dev.denfry.promptcritical.sim.economy.NpcMarket;
import dev.denfry.promptcritical.sim.environment.EmissionLedger;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Хозяйственный учёт (14 §14.6.6, §14.7): первая неделя — базовая, выделение следующей на 5 % меньше; выброс сверх
 * квот — штраф 100 CR/т; договор поставки платит за поставленное и берёт неустойку за недопоставку.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AccountingGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";

    private AccountingGameTests() {
    }

    private static void emitCo2(GameTestHelper h, double tonnes) {
        EmissionLedger led = AirQualityService.ledger(h.getLevel());
        // измерено (множитель 1,0) независимо от порядка тестов: иначе источник «рассчитан» (×1,2), пока его не пометил
        // тест САКВ, и база выходит 120 т вместо 100
        led.source("test_boiler", EmissionLedger.Category.BOILER).method = EmissionLedger.Method.MEASURED;
        led.record("test_boiler", EmissionLedger.Category.BOILER, EmissionLedger.Pollutant.CO2, tonnes * 1000, 1);
    }

    @GameTest(template = LARGE, timeoutTicks = 100)
    public static void quotasDeclineAndAShortfallIsFinedAndContractsSettle(GameTestHelper h) {
        var server = h.getLevel().getServer();
        PlantAccounting acct = PlantAccounting.get(server);
        UUID op = acct.operator() != null ? acct.operator() : UUID.nameUUIDFromBytes("pc-accounting-test".getBytes());
        acct.claimOperator(op);
        NpcMarket market = MarketData.get(server).market(server.overworld().getGameTime());
        market.transfer(op, 1_000_000_00L);
        boolean fresh = acct.quota() == null;
        // базовая неделя (если учёт новый): 100 т CO2 → выделение 95 т
        if (fresh) {
            for (var l : server.getAllLevels()) AirQualityService.ledger(l).rollPeriod();
            emitCo2(h, 100);
            acct.settle(server);
            h.assertTrue(Math.abs(acct.baseT() - 100) < 1, "base period " + acct.baseT());
            h.assertTrue(Math.abs(acct.quota().heldT() - 95) < 1, "allocation 95 t: " + acct.quota().heldT());
        }
        // мир GameTest сохраняется между прогонами: договоры прошлого прогона расторгаются
        acct.cancel(market, op);
        // договор на кокс: поставить половину, остальное — недопоставка
        var contract = acct.sign(market, op, "coke", 120_00);
        h.assertTrue(contract != null, "contract signed");
        acct.deliver(op, "coke", 32);
        long before = market.account(op).balanceCents();
        double held = acct.quota().heldT();
        for (var l : server.getAllLevels()) AirQualityService.ledger(l).rollPeriod();
        // 50 т сверх квот с учётом долга прошлых недель (мир GameTest сохраняется между прогонами)
        emitCo2(h, Math.max(0, held + 50 - acct.quota().carriedDebtT()));
        acct.settle(server);
        long after = market.account(op).balanceCents();
        // оплата 32 ед. × 120 CR минус неустойка, штраф 50 т × 100 CR, платы за SO2/NOx/PM — нули
        long expectedFine = 50 * 100_00L;
        long payment = 32 * 120_00L;
        long penalty = Math.round(0.2 * 120_00 * 32);
        h.assertTrue(Math.abs((after - before) - (payment - penalty - expectedFine)) < 100_00,
                "balance change " + (after - before) + " vs " + (payment - penalty - expectedFine));
        h.assertTrue(acct.quota().carriedDebtT() > 49, "deficit carried: " + acct.quota().carriedDebtT());
        h.succeed();
    }
}

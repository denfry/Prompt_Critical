package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.market.MarketData;
import dev.denfry.promptcritical.mod.market.MarketTerminalBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Биржа (14 §14.6): терминал продаёт сталь из слота NPC-рынку и зачисляет CR на счёт владельца; ванильный товар вне
 * белого списка не принимается (§14.8.2).
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MarketGameTests {
    private static final String SMALL = "gametest/empty_12x12x12";
    private static final BlockPos TERMINAL = new BlockPos(3, 2, 3);

    private MarketGameTests() {
    }

    @GameTest(template = SMALL, timeoutTicks = 100)
    public static void terminalSellsSteelForCredits(GameTestHelper h) {
        h.setBlock(TERMINAL, PcBlocks.MARKET_TERMINAL.get());
        Player p = TestPlayers.of(h);
        long before = MarketData.get(h.getLevel().getServer()).account(p.getUUID()).balanceCents();
        h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    MarketTerminalBlockEntity t = h.getBlockEntity(TERMINAL);
                    ItemStack steel = new ItemStack(PcItems.STEEL_INGOT.get(), 16);
                    t.useItem(p, InteractionHand.MAIN_HAND, steel);
                    h.assertTrue(steel.isEmpty(), "terminal takes the steel: " + steel);
                    h.assertTrue(p.getUUID().equals(t.owner()), "the first user owns the account");
                })
                .thenWaitUntil(() -> {
                    MarketTerminalBlockEntity t = h.getBlockEntity(TERMINAL);
                    h.assertTrue(t.panelItems().getStackInSlot(0).isEmpty(), "steel sold");
                    long after = MarketData.get(h.getLevel().getServer()).account(p.getUUID()).balanceCents();
                    h.assertTrue(after > before, "credits paid: " + before + " -> " + after);
                })
                .thenSucceed();
    }

    @GameTest(template = SMALL, timeoutTicks = 40)
    public static void terminalRefusesVanillaGoods(GameTestHelper h) {
        h.setBlock(TERMINAL, PcBlocks.MARKET_TERMINAL.get());
        h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    MarketTerminalBlockEntity t = h.getBlockEntity(TERMINAL);
                    ItemStack cobble = new ItemStack(Items.COBBLESTONE, 64);
                    h.assertTrue(t.panelItems().insertItem(0, cobble, true).getCount() == 64, "cobblestone is not on the list");
                })
                .thenSucceed();
    }
}

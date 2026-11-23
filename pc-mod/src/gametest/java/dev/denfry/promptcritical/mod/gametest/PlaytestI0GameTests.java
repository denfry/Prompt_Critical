package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.site.ConstructionSiteBlockEntity;
import dev.denfry.promptcritical.mod.site.SitePlans;
import dev.denfry.promptcritical.mod.site.SiteRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Находки игрового прохода I0 (PLAYTEST.md, «Путь игрока»), закреплённые тестами. */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlaytestI0GameTests {
    private PlaytestI0GameTests() {}

    /**
     * Машина, достроенная на площадке, засчитывается игроку, приложившему чертёж, как постановка и её самой, и её
     * двойника старой серии: задание маршрута «тигельная печь» и глава справочника ждут {@code crucible_furnace}.
     */
    @GameTest(template = "gametest/empty_12x12x12", timeoutTicks = 1200)
    public static void finishedSiteCountsForThePlayerWhoLaidTheDrawing(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 2, 5);
        for (int x = 0; x <= 1; x++)
            for (int z = 0; z <= 1; z++) h.setBlock(p.offset(x, -2, z), Blocks.STONE);
        h.setBlock(p, SiteRegistry.SITE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        ServerPlayer player = (ServerPlayer) h.makeMockServerPlayerInLevel();
        var site = (ConstructionSiteBlockEntity) h.getBlockEntity(p);
        h.assertTrue(site.accept("crucible_furnace_real") == null, "plan accepted");
        site.setOwner(player);
        SitePlans.Plan plan = SitePlans.get("crucible_furnace_real");
        h.assertTrue(SitePlans.twin("crucible_furnace_real") != null, "the melting hole has the crucible furnace as its twin");
        h.onEachTick(() -> {
            // подаём всё, чего не хватает на текущем этапе, как воронка
            if (site.isRemoved() || site.plan() == null || site.stage() >= plan.stages().size()) return;
            for (SitePlans.Need n : plan.stages().get(site.stage()).needs())
                site.items().insertItem(0, new ItemStack(n.item().get(), 64), false);
        });
        h.succeedWhen(() -> {
            h.assertBlockPresent(PcBlocks.CRUCIBLE_FURNACE_REAL.get(), p);
            h.assertTrue(player.getStats().getValue(Stats.ITEM_USED.get(PcBlocks.CRUCIBLE_FURNACE_REAL.get().asItem())) == 1,
                    "the machine counts as placed by the player");
            h.assertTrue(player.getStats().getValue(Stats.ITEM_USED.get(PcBlocks.CRUCIBLE_FURNACE.get().asItem())) == 1,
                    "and as its old-series twin for the route and the handbook");
        });
    }

    /** Каждый чертёж стройплощадки рисуется своей моделью предмета и стоит во вкладке творческого режима. */
    @GameTest(template = "gametest/empty_12x12x12", timeoutTicks = 20)
    public static void everyDrawingIsInTheCreativeTab(GameTestHelper h) {
        int n = 0;
        for (var d : SiteRegistry.drawings()) {
            n++;
            h.assertTrue(SiteRegistry.items().contains(d), d.getId() + " in the creative tab");
        }
        h.assertTrue(n >= 30, "all drawings are listed, got " + n);
        h.succeed();
    }
}

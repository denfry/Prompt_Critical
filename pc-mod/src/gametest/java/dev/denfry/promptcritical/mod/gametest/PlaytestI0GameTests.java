package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.site.ConstructionSiteBlockEntity;
import dev.denfry.promptcritical.mod.site.SitePlans;
import dev.denfry.promptcritical.mod.site.SiteRegistry;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.denfry.promptcritical.mod.multiblock.WholeShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
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

    /** Машины I0 реального масштаба с цельной моделью (PLAYTEST.md, «Путь игрока I0»). */
    private static final String[] I0_WHOLE = {"lancashire_boiler", "lancashire_stoker", "mill_engine", "winding_engine",
            "headframe_steel", "guibal_fan", "mine_drainage_pump_geared", "cupola_furnace_real", "crucible_furnace_real",
            "coal_jig_real", "longwall_shearer_real", "powered_roof_support_real", "machine_lathe_real", "drill_press_real",
            "milling_machine_real", "shaping_machine_real", "surface_grinder_real", "molding_station_real", "shakeout_grate_real"};

    /**
     * Сквозь барабан подъёмной машины не пройти (плейтест I0: игрок проходил по оси контроллера всю машину насквозь,
     * барабан — вторая цельная модель, которой не было в форме клеток): клетки на оси внутри барабана твёрдые при любом
     * фасаде.
     */
    @GameTest(template = "gametest/empty_16x24x16", timeoutTicks = 20)
    public static void windingEngineDrumIsSolidOnTheCentreLine(GameTestHelper h) {
        BlockPos p = new BlockPos(7, 2, 7);
        var level = h.getLevel();
        for (Direction f : Direction.Plane.HORIZONTAL) {
            h.setBlock(p, PcBlocks.WINDING_ENGINE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f));
            for (int dy = 0; dy <= 2; dy++)
                for (int dz = -2; dz <= 2; dz++) {
                    // клетка модели (0, dy, dz) — в мире по фасаду машины
                    BlockPos w = h.absolutePos(p.above(dy).relative(f.getOpposite(), dz));
                    h.assertTrue(!level.getBlockState(w).getCollisionShape(level, w).isEmpty(),
                            "drum cell (0, " + dy + ", " + dz + ") is solid, facing " + f);
                }
            h.setBlock(p, Blocks.AIR);
        }
        h.succeed();
    }

    /**
     * У каждой машины I0 с цельной моделью клетка, где модель что-то рисует (сама модель, вторая цельная модель и
     * вращающиеся на месте детали — WholeShapes), входит в объём машины и не пропускает игрока. Пустые клетки коробки
     * (проход между стойками копра, лестницы) моделью не заняты и остаются проходимыми.
     */
    @GameTest(template = "gametest/empty_16x24x16", timeoutTicks = 40)
    public static void everyDrawnCellOfI0WholeModelsBlocksThePlayer(GameTestHelper h) {
        BlockPos p = new BlockPos(7, 5, 4);
        var level = h.getLevel();
        StringBuilder bad = new StringBuilder();
        for (String id : I0_WHOLE) {
            Block b = BuiltInRegistries.BLOCK.get(PromptCritical.id(id));
            h.assertTrue(b instanceof PcBlock, id + " is a machine block");
            h.setBlock(p, b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
            BlockPos abs = h.absolutePos(p);
            java.util.Set<BlockPos> drawn = new java.util.HashSet<>(WholeShapes.occupied(id));
            h.assertTrue(!drawn.isEmpty(), id + " has cell shape data");
            for (Footprint.Cell c : ((PcBlock) b).spec().footprint().cells(abs, Direction.NORTH)) {
                BlockPos m = c.pos().subtract(abs);   // фасад на север: клетка мира = клетка модели
                if (!drawn.remove(m)) continue;
                if (level.getBlockState(c.pos()).getCollisionShape(level, c.pos()).isEmpty()) bad.append(' ').append(id).append(m.toShortString());
            }
            for (BlockPos m : drawn) bad.append(' ').append(id).append(m.toShortString()).append("(outside the volume)");
            h.setBlock(p, Blocks.AIR);
        }
        h.assertTrue(bad.isEmpty(), "drawn cells the player walks through:" + bad);
        h.succeed();
    }
}

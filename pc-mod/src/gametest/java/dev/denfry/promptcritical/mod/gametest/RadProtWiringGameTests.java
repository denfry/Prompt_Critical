package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.environment.ActivityData;
import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.environment.ShieldBlocks;
import dev.denfry.promptcritical.mod.radiation.CaskCartBlockEntity;
import dev.denfry.promptcritical.mod.radiation.DryStorageModuleBlockEntity;
import dev.denfry.promptcritical.mod.radiation.HotCellBlockEntity;
import dev.denfry.promptcritical.mod.radiation.PortalMonitorBlockEntity;
import dev.denfry.promptcritical.mod.radiation.SpentFuelCaskBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.radiation.ActivityInventory;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import dev.denfry.promptcritical.sim.radiation.HotCell;
import dev.denfry.promptcritical.sim.radiation.ShieldMaterial;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Радзащита реального масштаба: плитки защиты со своими материалами (04b §4b.4), горячая камера держит источник за
 * стеной, контейнер не закрывается сверх пределов SSR-6, путь контейнер → тележка → модуль сухого хранения, вентканалы
 * модуля, проход портальной рамы. BE, поставленный через setBlock, получает onLoad тиком позже — проверки после паузы.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RadProtWiringGameTests {
    private static final String ROOM = "gametest/empty_12x12x12";

    private RadProtWiringGameTests() {}

    private static BlockState north(Block b) {
        return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    }

    /** Облучённый предмет: {@code bq} Бк нуклида {@code e} в 1 кг. */
    private static ItemStack hot(GameTestHelper h, Emitter e, double bq) {
        ItemStack s = new ItemStack(Items.IRON_INGOT);
        s.set(ActivityData.ACTIVITY.get(), ActivityData.of(new ActivityInventory(Map.of(e, bq), 1, 7.8), h.getLevel().getGameTime()));
        return s;
    }

    @GameTest(template = ROOM, timeoutTicks = 20)
    public static void shieldTilesCarryTheirMaterials(GameTestHelper h) {
        h.assertTrue(ShieldBlocks.of(PcBlocks.SHIELD_HEAVY_CONCRETE_REAL.get().defaultBlockState(), 100).material() == ShieldMaterial.HEAVY_CONCRETE,
                "heavy concrete tile");
        h.assertTrue(ShieldBlocks.of(PcBlocks.SHIELD_LEAD_BRICK_REAL.get().defaultBlockState(), 100).material() == ShieldMaterial.LEAD, "lead bricks");
        h.assertTrue(ShieldBlocks.of(PcBlocks.SHIELD_BORATED_WATER_TANK_REAL.get().defaultBlockState(), 100).material() == ShieldMaterial.BORATED_WATER,
                "borated water tank");
        h.assertTrue(ShieldBlocks.of(PcBlocks.SHIELD_BORATED_PE_REAL.get().defaultBlockState(), 100).material()
                == ShieldMaterial.BORATED_POLYETHYLENE, "borated polyethylene");
        h.assertTrue(ShieldBlocks.of(north(PcBlocks.HOT_CELL_REAL.get()), 100) == null, "the hot cell shields its own source");
        h.succeed();
    }

    /** 1 ПБк Cs-137 на столе камеры: у окна оператора меньше 10 мкЗв/ч (04b §4b.4.4), без стены были бы десятки Зв/ч. */
    @GameTest(template = ROOM, timeoutTicks = 80)
    public static void hotCellKeepsAPetabecquerelBehindItsWall(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 2, 4);
        h.setBlock(p, north(PcBlocks.HOT_CELL_REAL.get()));
        h.runAfterDelay(1, () -> {
            HotCellBlockEntity c = (HotCellBlockEntity) h.getBlockEntity(p);
            h.assertTrue("hot_cell_real".equals(c.wholeModelId()), "whole model");
            c.slots().insertItem(0, hot(h, Emitter.CS137, 1e15), false);
        });
        h.runAfterDelay(45, () -> {
            HotCellBlockEntity c = (HotCellBlockEntity) h.getBlockEntity(p);
            Vec3 operator = c.table().add(0, 0, -HotCell.OPERATOR_M);
            double rate = RadiationService.rateSvH(h.getLevel(), operator);
            h.assertTrue(rate > 0 && rate < HotCell.DESIGN_SV_H, "operator rate " + rate + " Sv/h");
            h.succeed();
        });
    }

    /** Свежее топливо крышку не закрывает; выдержанная загрузка закрывается, тележка поднимает контейнер и сдаёт пенал в модуль. */
    @GameTest(template = ROOM, timeoutTicks = 120)
    public static void caskCartAndDryStorageMoveTheFuel(GameTestHelper h) {
        BlockPos cask = new BlockPos(2, 1, 2), cart = new BlockPos(7, 1, 2), module = new BlockPos(6, 1, 6);
        h.setBlock(cask, north(PcBlocks.SPENT_FUEL_CASK_REAL.get()).setValue(BlockStateProperties.OPEN, true));
        h.setBlock(cart, north(PcBlocks.CASK_TRANSPORT_CART_REAL.get()));
        h.setBlock(module, north(PcBlocks.DRY_STORAGE_MODULE_REAL.get()));
        var player = TestPlayers.of(h);
        h.runAfterDelay(1, () -> {
            SpentFuelCaskBlockEntity c = (SpentFuelCaskBlockEntity) h.getBlockEntity(cask);
            // 10 ПБк Co-60 — поверхность далеко за 2 мЗв/ч: крышка не закрывается
            c.slots().insertItem(0, hot(h, Emitter.CO60, 1e16), false);
            player.setShiftKeyDown(true);
            c.use(player);
            h.assertTrue(c.open(), "an overloaded cask refuses to close");
            c.slots().extractItem(0, 1, false);
            c.slots().insertItem(0, hot(h, Emitter.CS137, 1e12), false);
            c.use(player);
            h.assertTrue(!c.open(), "a light load closes");
            player.setShiftKeyDown(false);
            CaskCartBlockEntity t = (CaskCartBlockEntity) h.getBlockEntity(cart);
            t.use(player);
            h.assertTrue(t.loaded() && !h.getBlockState(cask).is(PcBlocks.SPENT_FUEL_CASK_REAL.get()), "the cart lifted the cask");
            t.use(player);
            DryStorageModuleBlockEntity m = (DryStorageModuleBlockEntity) h.getBlockEntity(module);
            h.assertTrue(!m.empty() && t.loaded(), "the canister went into the module, the empty cask stayed on the cart");
            h.succeed();
        });
    }

    /** Модуль хранения: заложенные входы воздуха — половина каналов. */
    @GameTest(template = ROOM, timeoutTicks = 60)
    public static void dryStorageCountsItsVents(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 1, 3);
        h.setBlock(p, north(PcBlocks.DRY_STORAGE_MODULE_REAL.get()));
        h.setBlock(p.offset(-1, 0, -1), Blocks.STONE);
        h.setBlock(p.offset(1, 0, -1), Blocks.STONE);
        h.runAfterDelay(1, () -> ((DryStorageModuleBlockEntity) h.getBlockEntity(p)).slots().insertItem(0, hot(h, Emitter.CS137, 1e15), false));
        h.runAfterDelay(45, () -> {
            DryStorageModuleBlockEntity m = (DryStorageModuleBlockEntity) h.getBlockEntity(p);
            h.assertTrue(Math.abs(m.ventOpen() - 0.5) < 1e-9, "two of four vents blocked, got " + m.ventOpen());
            h.assertTrue(m.cladK() > DryStorageModuleBlockEntity.AMBIENT_K, "cladding warms up");
            h.succeed();
        });
    }

    /** Портальная рама реального масштаба: проход — клетка контроллера; одноколонный монитор — клетка перед ним. */
    @GameTest(template = ROOM, timeoutTicks = 20)
    public static void portalFrameGateIsItsOwnCell(GameTestHelper h) {
        BlockPos real = new BlockPos(3, 1, 3), old = new BlockPos(8, 1, 3);
        h.setBlock(real, north(PcBlocks.PORTAL_MONITOR_REAL.get()));
        h.setBlock(old, north(PcBlocks.PORTAL_MONITOR.get()));
        AABB g = ((PortalMonitorBlockEntity) h.getBlockEntity(real)).gate();
        h.assertTrue(g.contains(Vec3.atCenterOf(h.absolutePos(real))), "the frame's passage is the controller cell");
        AABB o = ((PortalMonitorBlockEntity) h.getBlockEntity(old)).gate();
        h.assertTrue(o.contains(Vec3.atCenterOf(h.absolutePos(old.north()))), "the column watches the cell in front");
        h.succeed();
    }
}

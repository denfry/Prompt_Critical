package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.environment.ActivityData;
import dev.denfry.promptcritical.mod.environment.ContainerActivityScan;
import dev.denfry.promptcritical.mod.environment.ContainerShielding;
import dev.denfry.promptcritical.mod.environment.NestingRule;
import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.radiation.FuelRackBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.ShulkerBoxSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Активные предметы светят из контейнеров (R55): сундук, любой блок с обработчиком предметов, сундучная вагонетка;
 * экран из карты данных {@code container_shielding} ослабляет поток; сломанный сундук уходит из индекса.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ContainerRadiationGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos CHEST = new BlockPos(2, 1, 2);

    private ContainerRadiationGameTests() {
    }

    /** Сильный стеклоблок ВАО с цезием: на 2 м даёт заведомо измеримую мощность дозы. */
    private static ItemStack hot(GameTestHelper h) {
        long now = h.getLevel().getGameTime();
        ItemStack s = new ItemStack(PcItems.HLW_GLASS_CANISTER.get());
        s.set(ActivityData.ACTIVITY.get(), new ActivityData(now, 50, 2.7, Map.of(Emitter.CS137.name(), 1.4e15)));
        return s;
    }

    private static Vec3 probe(GameTestHelper h) {
        return Vec3.atCenterOf(h.absolutePos(CHEST.offset(2, 1, 0)));
    }

    private static void fillChest(GameTestHelper h, ItemStack s) {
        h.setBlock(CHEST, Blocks.CHEST);
        ((Container) h.getBlockEntity(CHEST)).setItem(0, s);
    }

    @GameTest(template = LARGE, timeoutTicks = 100)
    public static void spentFuelInAChestIrradiatesTheNeighbour(GameTestHelper h) {
        double empty = RadiationService.rateSvH(h.getLevel(), probe(h));
        fillChest(h, hot(h));
        ContainerActivityScan.scanAllNow(h.getLevel(), probe(h));
        double rate = RadiationService.rateSvH(h.getLevel(), probe(h));
        h.assertTrue(rate > empty + 1e-7, "chest shines: " + rate + " Sv/h (empty " + empty + ")");
        h.succeed();
    }

    @GameTest(template = LARGE, timeoutTicks = 100)
    public static void brokenChestDropsFromTheIndex(GameTestHelper h) {
        fillChest(h, hot(h));
        ContainerActivityScan.scanAllNow(h.getLevel(), probe(h));
        int with = ContainerActivityScan.indexSize(h.getLevel());
        h.assertTrue(with >= 1, "chest indexed: " + with);
        h.setBlock(CHEST, Blocks.AIR);
        ContainerActivityScan.scanAllNow(h.getLevel(), probe(h));
        h.assertTrue(ContainerActivityScan.indexSize(h.getLevel()) == with - 1, "entry gone: " + ContainerActivityScan.indexSize(h.getLevel()));
        h.succeed();
    }

    @GameTest(template = LARGE, timeoutTicks = 100)
    public static void shieldingDataMapReducesTheRate(GameTestHelper h) {
        fillChest(h, hot(h));
        ContainerActivityScan.scanAllNow(h.getLevel(), probe(h));
        double bare = RadiationService.rateSvH(h.getLevel(), probe(h));
        ContainerActivityScan.overrideShielding(Blocks.CHEST, new ContainerShielding("lead", 5));
        try {
            ContainerActivityScan.scanAllNow(h.getLevel(), probe(h));
            double lead = RadiationService.rateSvH(h.getLevel(), probe(h));
            h.assertTrue(bare > 0 && lead < bare * 0.5, "5 cm of lead cuts the rate: " + lead + " vs " + bare);
        } finally {
            ContainerActivityScan.overrideShielding(Blocks.CHEST, null);
        }
        h.succeed();
    }

    @GameTest(template = LARGE, timeoutTicks = 100)
    public static void minecartChestCountsToo(GameTestHelper h) {
        double empty = RadiationService.rateSvH(h.getLevel(), probe(h));
        MinecartChest cart = EntityType.CHEST_MINECART.create(h.getLevel());
        Vec3 at = Vec3.atBottomCenterOf(h.absolutePos(CHEST));
        cart.setPos(at.x, at.y, at.z);
        cart.setItem(0, hot(h));
        h.getLevel().addFreshEntity(cart);
        ContainerActivityScan.scanAllNow(h.getLevel(), probe(h));
        double rate = RadiationService.rateSvH(h.getLevel(), probe(h));
        h.assertTrue(rate > empty + 1e-7, "minecart shines: " + rate + " Sv/h (empty " + empty + ")");
        cart.discard();
        h.succeed();
    }

    /** Стеллаж сам регистрирует свой экранированный источник: сканер контейнеров его предметы не дублирует. */
    @GameTest(template = LARGE, timeoutTicks = 100)
    public static void selfEmittingRackIsNotInTheContainerIndex(GameTestHelper h) {
        h.setBlock(CHEST, PcBlocks.FUEL_RACK.get());
        FuelRackBlockEntity rack = h.getBlockEntity(CHEST);
        rack.slots().setStackInSlot(0, hot(h));
        int before = ContainerActivityScan.indexSize(h.getLevel());
        ContainerActivityScan.scanAllNow(h.getLevel(), probe(h));
        h.assertTrue(ContainerActivityScan.indexSize(h.getLevel()) == before, "rack skipped: " + ContainerActivityScan.indexSize(h.getLevel()));
        h.succeed();
    }

    /** 04d §4d.6.2, 16 §16.8: источник выше уровня освобождения не идёт в шалкер, мешочек и сундук Края. */
    @GameTest(template = LARGE, timeoutTicks = 100)
    public static void sourcesStayOutOfPocketContainers(GameTestHelper h) {
        long now = h.getLevel().getGameTime();
        ItemStack hot = new ItemStack(Items.IRON_NUGGET);
        hot.set(ActivityData.ACTIVITY.get(), new ActivityData(now, 0.01, 7.8, Map.of(Emitter.CS137.name(), 1e6)));
        // 0,5 Бк/г цезия — ниже уровня освобождения 10 Бк/г
        ItemStack cleared = new ItemStack(Items.IRON_NUGGET);
        cleared.set(ActivityData.ACTIVITY.get(), new ActivityData(now, 1, 7.8, Map.of(Emitter.CS137.name(), 500.0)));
        ItemStack plain = new ItemStack(Items.IRON_NUGGET);
        h.assertFalse(NestingRule.mayNest(hot), "100 kBq/g caesium is a source");
        h.assertTrue(NestingRule.mayNest(cleared) && NestingRule.mayNest(plain), "cleared and plain items nest");

        Slot shulker = new ShulkerBoxSlot(new SimpleContainer(27), 0, 0, 0);
        h.assertFalse(shulker.mayPlace(hot), "no source into a shulker slot");
        h.assertTrue(shulker.mayPlace(plain), "plain items still go into a shulker");
        Slot ender = new Slot(new PlayerEnderChestContainer(), 0, 0, 0);
        h.assertFalse(ender.mayPlace(hot), "no source into the ender chest");
        h.assertTrue(ender.mayPlace(cleared), "cleared items go into the ender chest");
        h.assertTrue(new Slot(new SimpleContainer(1), 0, 0, 0).mayPlace(hot), "ordinary chests are untouched");
        BundleContents.Mutable bundle = new BundleContents.Mutable(BundleContents.EMPTY);
        h.assertTrue(bundle.tryInsert(hot.copy()) == 0, "no source into a bundle");
        h.assertTrue(bundle.tryInsert(plain.copy()) == 1, "plain items go into a bundle");

        BlockPos at = new BlockPos(5, 2, 5);
        h.setBlock(at, Blocks.SHULKER_BOX);
        ShulkerBoxBlockEntity box = h.getBlockEntity(at);
        h.assertFalse(box.canPlaceItemThroughFace(0, hot, Direction.UP), "a hopper cannot load a source into a shulker");
        h.assertTrue(box.canPlaceItemThroughFace(0, plain, Direction.UP), "a hopper loads plain items");
        h.succeed();
    }
}

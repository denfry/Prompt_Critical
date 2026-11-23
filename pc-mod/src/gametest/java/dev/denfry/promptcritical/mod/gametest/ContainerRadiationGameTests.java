package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.environment.ActivityData;
import dev.denfry.promptcritical.mod.environment.ContainerActivityScan;
import dev.denfry.promptcritical.mod.environment.ContainerShielding;
import dev.denfry.promptcritical.mod.environment.RadiationService;
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
}

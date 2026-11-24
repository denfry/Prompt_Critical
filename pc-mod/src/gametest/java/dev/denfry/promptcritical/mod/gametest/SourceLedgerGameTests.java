package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.environment.ActivityData;
import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.environment.SourceLedger;
import dev.denfry.promptcritical.mod.radiation.ContaminationService;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Правило призрака (16 §16.8.4): учтённый источник, пропавший из виду, светит из последней точки, пока его не найдут. */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SourceLedgerGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";

    private SourceLedgerGameTests() {
    }

    private static ItemStack canister(long now, double bq) {
        ItemStack s = new ItemStack(PcItems.HLW_GLASS_CANISTER.get());
        s.set(ActivityData.ACTIVITY.get(), new ActivityData(now, 50, 2.7, Map.of(Emitter.CS137.name(), bq)));
        return s;
    }

    @GameTest(template = LARGE)
    public static void unseenSourceRadiatesFromItsLastPlace(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        long now = level.getGameTime();
        Vec3 at = h.absoluteVec(new Vec3(6.5, 2.2, 6.5));
        List<Vec3> watcher = List.of(at);

        ItemStack weak = canister(now, 1e6);
        h.assertTrue(SourceLedger.sight(level, weak, at, null, true) == null && !weak.has(SourceLedger.SOURCE_ID.get()),
                "a weak source is not tracked");

        ItemStack hot = canister(now, 1.4e15);
        UUID id = SourceLedger.sight(level, hot, at, null, true);
        h.assertTrue(id != null && id.equals(hot.get(SourceLedger.SOURCE_ID.get())), "a category 1-3 source gets a source id");
        h.assertValueEqual(SourceLedger.get(level.getServer(), id).status(), SourceLedger.Status.SEEN, "seen");

        SourceLedger.tick(level, now + 100, watcher);
        h.assertValueEqual(SourceLedger.get(level.getServer(), id).status(), SourceLedger.Status.SEEN, "still searched for");
        SourceLedger.tick(level, now + 100 + SourceLedger.GRACE_TICKS, watcher);
        h.assertValueEqual(SourceLedger.get(level.getServer(), id).status(), SourceLedger.Status.UNSEEN, "a ghost after the grace");
        h.assertTrue(RadiationService.holds(level, at), "the ghost radiates from the last place");
        h.assertTrue(RadiationService.rateSvH(level, at.add(2, 0, 0)) > 0, "dose rate next to the ghost");

        SourceLedger.tick(level, now + 100 + SourceLedger.GRACE_TICKS + 20L * 86_400, watcher);
        h.assertValueEqual(SourceLedger.get(level.getServer(), id).status(), SourceLedger.Status.ORPHAN, "an orphan source later");

        SourceLedger.sight(level, hot, at.add(1, 0, 0), null, false);
        h.assertValueEqual(SourceLedger.get(level.getServer(), id).status(), SourceLedger.Status.SEEN, "found again");
        h.assertFalse(RadiationService.holds(level, at), "the ghost is gone once the source is seen");

        ItemEntity drop = new ItemEntity(level, at.x, at.y, at.z, hot);
        level.addFreshEntity(drop);
        drop.kill();
        h.assertTrue(SourceLedger.get(level.getServer(), id) == null, "a destroyed source leaves the ledger");
        h.succeed();
    }

    /**
     * Уничтоженный источник отдаёт активность в мир (16 §16.8.4): взрыв поднимает сотую часть цезия облаком и
     * разбрасывает остальное на три блока, лава поднимает 30 % цезия и оставляет остаток рядом, кактус только рассыпает
     * содержимое. Учтённый источник при этом уходит из реестра.
     */
    @GameTest(template = LARGE)
    public static void explodedSourceScattersItsActivity(GameTestHelper h) {
        ruined(h, h.getLevel().damageSources().explosion(null, null), 0.01, true);
    }

    @GameTest(template = LARGE)
    public static void sourceBurnedInLavaGoesUpAsAerosol(GameTestHelper h) {
        ruined(h, h.getLevel().damageSources().lava(), 0.3, false);
    }

    @GameTest(template = LARGE)
    public static void cactusBreaksTheSourceOpen(GameTestHelper h) {
        ruined(h, h.getLevel().damageSources().cactus(), 0, false);
    }

    /**
     * Источник Cs-137 на каменной площадке уничтожен ударом {@code hit}: в воздух уходит доля {@code airborne}, пол под
     * ним загрязнён, в трёх блоках — только если разброс широкий ({@code wide}).
     */
    private static void ruined(GameTestHelper h, DamageSource hit, double airborne, boolean wide) {
        ServerLevel level = h.getLevel();
        for (int x = 1; x <= 10; x++)
            for (int z = 1; z <= 10; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE.defaultBlockState());
        long now = level.getGameTime();
        double bq = 1.4e15;
        Vec3 at = h.absoluteVec(new Vec3(5.5, 2.2, 5.5));
        ItemStack hot = canister(now, bq);
        UUID id = SourceLedger.sight(level, hot, at, null, true);
        h.assertTrue(id != null, "the source is tracked");
        ItemEntity drop = new ItemEntity(level, at.x, at.y, at.z, hot);
        drop.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(drop);
        Vec3 where = drop.position();
        drop.hurt(hit, 1000);
        h.assertTrue(drop.isRemoved(), "the hit destroys the item");
        h.assertTrue(SourceLedger.get(level.getServer(), id) == null, "a destroyed source leaves the ledger");
        double air = RadiationService.airReleaseBqS(level, where, Emitter.CS137);
        double expect = bq * airborne / (RadiationService.HOLD_TICKS / 20.0);
        h.assertTrue(airborne == 0 ? air == 0 : Math.abs(air - expect) <= 1e-6 * expect,
                "airborne Cs-137 " + air + " Bq/s, expected " + expect);
        h.assertTrue(ContaminationService.surface(level, h.absolutePos(new BlockPos(5, 1, 5))).beta() > 0, "the floor under it is contaminated");
        boolean far = ContaminationService.surface(level, h.absolutePos(new BlockPos(8, 1, 5))).beta() > 0;
        h.assertTrue(far == wide, wide ? "an explosion throws it three blocks away" : "nothing three blocks away");
        h.succeed();
    }
}

package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.environment.BackgroundField;
import dev.denfry.promptcritical.sim.radiation.NaturalBackground;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Природный фон в мире (04b §4b.13): грунт в поле зрения 26 лучей и космическое под толщей породы. */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NaturalBackgroundGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";

    private NaturalBackgroundGameTests() {
    }

    /** Игрок стоит ногами в блоке (6, y, 6) относительно шаблона; возвращает его на нужном месте. */
    private static ServerPlayer playerAt(GameTestHelper h, int y) {
        ServerPlayer p = TestPlayers.of(h);
        p.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(6.5, y, 6.5)));
        BackgroundField.forget(p.getUUID());
        return p;
    }

    private static void fill(GameTestHelper h, int x0, int y0, int z0, int x1, int y1, int z1, net.minecraft.world.level.block.Block b) {
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++) h.setBlock(new BlockPos(x, y, z), b.defaultBlockState());
    }

    @GameTest(template = LARGE)
    public static void playerOnGrassSeesAboutPointOneMicroSievert(GameTestHelper h) {
        fill(h, 3, 1, 3, 9, 1, 9, Blocks.GRASS_BLOCK);
        ServerPlayer p = playerAt(h, 2);
        double v = BackgroundField.ambientSvH(h.getLevel(), p);
        if (v < 0.05e-6 || v > 0.12e-6) h.fail("Фон на траве " + v + " Зв/ч вне 0,05..0,12 мкЗв/ч");
        h.succeed();
    }

    @GameTest(template = LARGE)
    public static void playerBoxedInGraniteSeesMore(GameTestHelper h) {
        fill(h, 3, 1, 3, 9, 8, 9, Blocks.GRANITE);
        fill(h, 6, 4, 6, 6, 5, 6, Blocks.AIR);
        ServerPlayer p = playerAt(h, 4);
        double v = BackgroundField.ambientSvH(h.getLevel(), p);
        if (!(v > 0.2e-6)) h.fail("В граните фон " + v + " Зв/ч, ждали больше 0,2 мкЗв/ч");
        h.succeed();
    }

    /** Железная руда считается породой, в которой лежит, а не пустотой: в коробке из неё фон как у камня. */
    @GameTest(template = LARGE)
    public static void playerBoxedInIronOreReadsLikeStone(GameTestHelper h) {
        fill(h, 3, 1, 3, 9, 8, 9, Blocks.IRON_ORE);
        fill(h, 6, 4, 6, 6, 5, 6, Blocks.AIR);
        ServerPlayer p = playerAt(h, 4);
        double v = BackgroundField.ambientSvH(h.getLevel(), p);
        if (!(v > 0.2e-6)) h.fail("В железной руде фон " + v + " Зв/ч, ждали больше 0,2 мкЗв/ч");
        h.succeed();
    }


    @GameTest(template = LARGE)
    public static void openWaterIsCosmicOnly(GameTestHelper h) {
        fill(h, 1, 1, 1, 10, 2, 10, Blocks.WATER);
        fill(h, 6, 3, 6, 6, 3, 6, Blocks.AIR);
        ServerPlayer p = playerAt(h, 3);
        double v = BackgroundField.ambientSvH(h.getLevel(), p);
        double cosmic = NaturalBackground.cosmicUSvH(NaturalBackground.Dimension.OVERWORLD, p.getY(), 0) * 1e-6;
        if (Math.abs(v - cosmic) > 1e-9) h.fail("Над водой фон " + v + ", космический " + cosmic);
        h.succeed();
    }

    @GameTest(template = LARGE)
    public static void cacheDoesNotRecomputeWithoutMoving(GameTestHelper h) {
        fill(h, 3, 1, 3, 9, 1, 9, Blocks.GRASS_BLOCK);
        ServerPlayer p = playerAt(h, 2);
        int before = BackgroundField.cacheSize();
        double first = BackgroundField.ambientSvH(h.getLevel(), p);
        fill(h, 3, 2, 3, 5, 6, 9, Blocks.GRANITE); // мир изменился, игрок не сдвинулся
        double second = BackgroundField.ambientSvH(h.getLevel(), p);
        if (first != second) h.fail("Значение пересчитано без движения: " + first + " -> " + second);
        if (BackgroundField.cacheSize() != before + 1) h.fail("Размер кэша " + BackgroundField.cacheSize() + ", ждали " + (before + 1));
        p.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(7.5, 2, 6.5)));
        double third = BackgroundField.ambientSvH(h.getLevel(), p);
        if (first == third) h.fail("Сместившись на блок, игрок не получил нового значения");
        BackgroundField.forget(p.getUUID());
        h.succeed();
    }
}

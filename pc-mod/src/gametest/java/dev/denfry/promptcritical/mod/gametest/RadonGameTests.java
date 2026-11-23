package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.environment.RadonField;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Радон в замкнутом воздухе (04b §4b.13.3): комната в граните копит его, пещера и небо нет, выход игрока забывает. */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RadonGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";

    private RadonGameTests() {
    }

    private static void fill(GameTestHelper h, int x0, int y0, int z0, int x1, int y1, int z1, Block b) {
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++) h.setBlock(new BlockPos(x, y, z), b.defaultBlockState());
    }

    /** Игрок стоит ногами в блоке (x, y, z); прежнее состояние радона стёрто. */
    private static ServerPlayer playerAt(GameTestHelper h, double x, double y, double z) {
        ServerPlayer p = TestPlayers.of(h);
        p.moveTo(h.absoluteVec(new Vec3(x, y, z)));
        RadonField.forget(p.getUUID());
        return p;
    }

    @GameTest(template = LARGE)
    public static void sealedGraniteRoomBuildsRadon(GameTestHelper h) {
        fill(h, 4, 1, 4, 8, 5, 8, Blocks.GRANITE);
        fill(h, 5, 2, 5, 7, 4, 7, Blocks.AIR);
        ServerPlayer p = playerAt(h, 6.5, 3, 6.5);
        for (int i = 0; i < 50; i++) RadonField.refresh(h.getLevel(), p);
        double c = RadonField.concentration(p);
        double dose = RadonField.doseSvH(h.getLevel(), p);
        var setting = RadonField.setting(p);
        RadonField.forget(p.getUUID());
        if (setting != dev.denfry.promptcritical.sim.radiation.RadonBox.Setting.MINE_STILL) h.fail("Обстановка " + setting + ", ждали MINE_STILL");
        if (!(c > 0)) h.fail("В закрытой комнате радон " + c + " Бк/м³, ждали больше 0");
        if (!(dose > 0)) h.fail("Доза радона " + dose + " Зв/ч, ждали больше 0");
        h.succeed();
    }

    @GameTest(template = LARGE)
    public static void plankRoomUnderRoofIsABuilding(GameTestHelper h) {
        fill(h, 4, 1, 4, 8, 1, 8, Blocks.DIRT);
        fill(h, 4, 2, 4, 8, 5, 8, Blocks.OAK_PLANKS);
        fill(h, 5, 2, 5, 7, 4, 7, Blocks.AIR);
        ServerPlayer p = playerAt(h, 6.5, 3, 6.5);
        for (int i = 0; i < 50; i++) RadonField.refresh(h.getLevel(), p);
        double c = RadonField.concentration(p);
        var setting = RadonField.setting(p);
        RadonField.forget(p.getUUID());
        // сравнение с гранитной комнатой того же объёма: там ~10 Бк/м³ за те же 50 шагов, здесь около 2,6
        if (setting != dev.denfry.promptcritical.sim.radiation.RadonBox.Setting.BUILDING) h.fail("Обстановка " + setting + ", ждали BUILDING");
        if (!(c < 5.0)) h.fail("В дощатой комнате радон " + c + " Бк/м³, ждали заметно меньше гранитной");
        h.succeed();
    }

    @GameTest(template = LARGE)
    public static void openSkyPocketStaysClean(GameTestHelper h) {
        fill(h, 4, 1, 4, 8, 1, 8, Blocks.GRANITE);
        ServerPlayer p = playerAt(h, 6.5, 2, 6.5);
        for (int i = 0; i < 20; i++) RadonField.refresh(h.getLevel(), p);
        double c = RadonField.concentration(p);
        RadonField.forget(p.getUUID());
        if (c != 0) h.fail("Под открытым небом радон " + c + " Бк/м³, ждали 0");
        h.succeed();
    }

    @GameTest(template = LARGE)
    public static void hugeOpenCaveDoesNotCountAsSealed(GameTestHelper h) {
        // полость 17×17×17 = 4913 блоков, закрытая со всех сторон гранитом: заливка упирается в предел 4096
        fill(h, -1, 1, -1, 17, 19, 17, Blocks.GRANITE);
        fill(h, 0, 2, 0, 16, 18, 16, Blocks.AIR);
        ServerPlayer p = playerAt(h, 8.5, 10, 8.5);
        for (int i = 0; i < 20; i++) RadonField.refresh(h.getLevel(), p);
        double c = RadonField.concentration(p);
        RadonField.forget(p.getUUID());
        if (c != 0) h.fail("В пещере больше предела радон " + c + " Бк/м³, ждали 0");
        h.succeed();
    }

    @GameTest(template = LARGE)
    public static void stateIsRemovedOnLogout(GameTestHelper h) {
        fill(h, 4, 1, 4, 8, 5, 8, Blocks.GRANITE);
        fill(h, 5, 2, 5, 7, 4, 7, Blocks.AIR);
        ServerPlayer p = playerAt(h, 6.5, 3, 6.5);
        int before = RadonField.stateCount();
        RadonField.refresh(h.getLevel(), p);
        if (RadonField.stateCount() != before + 1) h.fail("Состояние не создано: " + RadonField.stateCount());
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(p));
        if (RadonField.stateCount() != before) h.fail("После выхода осталось " + RadonField.stateCount() + " состояний, ждали " + before);
        h.succeed();
    }
}

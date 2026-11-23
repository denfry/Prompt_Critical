package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.mine.MineHoistBlockEntity;
import dev.denfry.promptcritical.mod.mine.ShaftSignalBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.scada.device.DeviceProfile;
import dev.denfry.promptcritical.mod.scada.device.DeviceProfiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Стволовой сигнальный аппарат и периферия {@code pc_hoist} (08 §8.2.8) на стенде подъёма {@link HoistGameTests}. */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShaftSignalGameTests {
    private static final String TALL = "gametest/empty_12x24x12";
    private static final BlockPos CAGE = new BlockPos(5, 16, 5), HOIST = new BlockPos(5, 16, 9);
    /** Аппараты: у верхней посадки (пол клети y 15) и у горизонта −7 м (пол y 8), оба в двух клетках от оси. */
    private static final BlockPos TOP_SIGNAL = new BlockPos(7, 16, 4), LEVEL_SIGNAL = new BlockPos(7, 9, 6);

    private ShaftSignalGameTests() {
    }

    /** Массив с пустым стволом 3×3 до y 2 и штреком на горизонте y 8, как в {@link HoistGameTests}. */
    private static void rig(GameTestHelper h) {
        for (int x = 2; x <= 9; x++)
            for (int y = 1; y <= 14; y++)
                for (int z = 2; z <= 9; z++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());
        for (int x = 4; x <= 6; x++)
            for (int y = 2; y <= 14; y++)
                for (int z = 4; z <= 6; z++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
        for (int x = 7; x <= 9; x++) {
            h.setBlock(new BlockPos(x, 8, 5), Blocks.AIR.defaultBlockState());
            h.setBlock(new BlockPos(x, 9, 5), Blocks.AIR.defaultBlockState());
        }
        h.setBlock(new BlockPos(5, 19, 5), facing(PcBlocks.HEADFRAME.get().defaultBlockState()));
        h.setBlock(new BlockPos(5, 22, 5), facing(PcBlocks.HEADFRAME.get().defaultBlockState()));
        h.setBlock(CAGE, facing(PcBlocks.MINE_CAGE.get().defaultBlockState()));
        h.setBlock(HOIST, facing(PcBlocks.MINE_HOIST.get().defaultBlockState()));
        BlockPos motor = new BlockPos(7, 16, 9);
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.WEST));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
        h.setBlock(TOP_SIGNAL, facing(PcBlocks.SHAFT_SIGNAL.get().defaultBlockState()));
        h.setBlock(LEVEL_SIGNAL, facing(PcBlocks.SHAFT_SIGNAL.get().defaultBlockState()));
    }

    private static BlockState facing(BlockState s) {
        return s.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    }

    private static boolean lit(GameTestHelper h, BlockPos signal) {
        return h.getBlockState(signal).getValue(BlockStateProperties.POWERED);
    }

    /**
     * Аппараты находят подъём и свои посадки; верхний горит, пока клеть у него, и компаратор видит пустую клеть;
     * импульс редстоуна в нижний аппарат вызывает клеть, и после посадки гаснет верхний и загорается нижний.
     */
    @GameTest(template = TALL, timeoutTicks = 1400)
    public static void redstonePulseCallsCageAndLampFollowsIt(GameTestHelper h) {
        rig(h);
        Player player = h.makeMockPlayer(GameType.CREATIVE);
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(h.getBlockState(new BlockPos(5, 22, 5)).getValue(PcProperties.FORMED), "headframe formed");
                    h.assertTrue(h.<MineHoistBlockEntity>getBlockEntity(HOIST).shaft() != null, "shaft found");
                })
                .thenWaitUntil(() -> h.assertTrue(OpsGameTests.defects(h, h.<MineHoistBlockEntity>getBlockEntity(HOIST)),
                        "hoist without installation defects"))
                .thenExecute(() -> h.<MineHoistBlockEntity>getBlockEntity(HOIST)
                        .installRope(player, new ItemStack(PcItems.WIRE_ROPE.get(), 16)))
                .thenWaitUntil(() -> {
                    h.assertValueEqual(h.<ShaftSignalBlockEntity>getBlockEntity(TOP_SIGNAL).landing(), 0, "top box serves landing 0");
                    h.assertValueEqual(h.<ShaftSignalBlockEntity>getBlockEntity(LEVEL_SIGNAL).landing(), 1, "level box serves landing 1");
                    h.assertTrue(lit(h, TOP_SIGNAL), "top lamp lit while the cage stands there");
                    h.assertFalse(lit(h, LEVEL_SIGNAL), "level lamp dark");
                    h.assertValueEqual(h.<ShaftSignalBlockEntity>getBlockEntity(TOP_SIGNAL).comparator(), 1, "comparator: empty cage here");
                })
                .thenExecute(() -> h.setBlock(LEVEL_SIGNAL.above(), Blocks.REDSTONE_BLOCK.defaultBlockState()))
                .thenWaitUntil(() -> h.assertBlockNotPresent(PcBlocks.MINE_CAGE.get(), CAGE))
                .thenWaitUntil(() -> {
                    h.assertBlockPresent(PcBlocks.MINE_CAGE.get(), new BlockPos(5, 9, 5));
                    h.assertTrue(lit(h, LEVEL_SIGNAL), "level lamp lit after arrival");
                    h.assertFalse(lit(h, TOP_SIGNAL), "top lamp dark");
                    h.assertValueEqual(h.<ShaftSignalBlockEntity>getBlockEntity(LEVEL_SIGNAL).comparator(), 1, "comparator at the level");
                })
                .thenSucceed();
    }

    /** Подъёмная машина — устройство CC {@code pc_hoist} с чтениями состояния и посадок и командой {@code call}. */
    @GameTest(template = TALL, timeoutTicks = 400)
    public static void hoistIsAPcHoistDevice(GameTestHelper h) {
        rig(h);
        h.succeedWhen(() -> {
            DeviceProfile p = DeviceProfiles.of(h.getBlockEntity(HOIST));
            h.assertTrue(p != null && "pc_hoist".equals(p.type), "hoist profile is pc_hoist");
            h.assertTrue(p.reads.containsKey("getState") && p.reads.containsKey("getLandings") && p.commands.containsKey("call"),
                    "getState, getLandings and call exposed");
            h.assertTrue(p.additionalTypes.contains("pc_mine"), "also a pc_mine device");
        });
    }
}

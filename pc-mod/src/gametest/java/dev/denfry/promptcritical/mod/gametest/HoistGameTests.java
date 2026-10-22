package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.mine.HoistMenu;
import dev.denfry.promptcritical.mod.mine.MineDrainagePumpBlockEntity;
import dev.denfry.promptcritical.mod.mine.MineHoistBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.mining.HoistSubsystem;
import dev.denfry.promptcritical.sim.mining.HoistUnit;
import dev.denfry.promptcritical.sim.mining.MineNetwork;
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
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Подъём и водоотлив в мире (этап 8б M2, 08 §8.2.8–8.2.9): клеть-контрапция спускается на горизонт и встаёт на
 * посадке блоками, вода сверх водосборника топит низ штрека и уходит обратно, насос на валу Create качает.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HoistGameTests {
    private static final String TALL = "gametest/empty_12x24x12";
    private static final String LONG = "gametest/empty_40x10x10";
    private static final int Z = 5;

    private HoistGameTests() {
    }

    private static SimLevelBinding binding(GameTestHelper h) {
        SimLevelBinding b = SimBindings.get(h.getLevel());
        if (b == null) throw new IllegalStateException("no simulation binding for the test level");
        return b;
    }

    private static void motor(GameTestHelper h, BlockPos rel, Direction toward, int rpm) {
        h.setBlock(rel, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, toward));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(rel)).generatedSpeed.setValue(rpm));
    }

    // --- подъём ---------------------------------------------------------------------------------------------

    /** Массив 2…9 × 1…14 × 2…9, ствол 3×3 с x, z = 4…6 до y = 2, штрек на горизонте y = 8 к +x. */
    private static void shaftSite(GameTestHelper h) {
        for (int x = 2; x <= 9; x++)
            for (int y = 1; y <= 14; y++)
                for (int z = 2; z <= 9; z++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());
        for (int x = 4; x <= 6; x++)
            for (int y = 2; y <= 14; y++)
                for (int z = 4; z <= 6; z++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
        for (int x = 7; x <= 9; x++) {
            h.setBlock(new BlockPos(x, 8, Z), Blocks.AIR.defaultBlockState());
            h.setBlock(new BlockPos(x, 9, Z), Blocks.AIR.defaultBlockState());
        }
    }

    private static BlockState facing(BlockState s, Direction d) {
        return s.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
    }

    /**
     * Копёр из двух сегментов над стволом, клеть на верхней посадке, подъёмная машина на моторе Create, канат из
     * бухт: ствол найден (посадки 0, 7, 13 м); клеть едет контрапцией на горизонт и встаёт там блоками с закрытой
     * дверью, двойник стоит на посадке 1.
     */
    @GameTest(template = TALL, timeoutTicks = 1200)
    public static void cageRidesToLevelAndLandsAsBlocks(GameTestHelper h) {
        shaftSite(h);
        h.setBlock(new BlockPos(5, 19, 5), facing(PcBlocks.HEADFRAME.get().defaultBlockState(), Direction.NORTH));
        h.setBlock(new BlockPos(5, 22, 5), facing(PcBlocks.HEADFRAME.get().defaultBlockState(), Direction.NORTH));
        BlockPos cage = new BlockPos(5, 16, 5);
        h.setBlock(cage, facing(PcBlocks.MINE_CAGE.get().defaultBlockState(), Direction.NORTH));
        BlockPos hoist = new BlockPos(5, 16, 9);
        h.setBlock(hoist, facing(PcBlocks.MINE_HOIST.get().defaultBlockState(), Direction.NORTH));
        motor(h, new BlockPos(7, 16, 9), Direction.WEST, 64);
        Player player = h.makeMockPlayer(GameType.CREATIVE);
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(h.getBlockState(new BlockPos(5, 22, 5)).getValue(PcProperties.FORMED), "headframe formed");
                    MineHoistBlockEntity be = h.getBlockEntity(hoist);
                    h.assertTrue(be.shaft() != null, "shaft found under the headframe");
                    h.assertValueEqual(java.util.Arrays.toString(be.shaft().landings()), "[0, 7, 13]", "landings");
                })
                .thenWaitUntil(() -> h.assertTrue(OpsGameTests.defects(h, h.<MineHoistBlockEntity>getBlockEntity(hoist)),
                        "hoist without installation defects"))
                .thenExecute(() -> {
                    MineHoistBlockEntity be = h.getBlockEntity(hoist);
                    be.installRope(player, new ItemStack(PcItems.WIRE_ROPE.get(), 16));
                    be.onButton(player, 1);
                })
                .thenWaitUntil(() -> {
                    h.assertBlockNotPresent(PcBlocks.MINE_CAGE.get(), cage);
                    h.assertTrue(!h.getLevel().getEntitiesOfClass(ControlledContraptionEntity.class,
                            new AABB(h.absolutePos(new BlockPos(0, 0, 0))).expandTowards(12, 24, 12)).isEmpty(), "cage is a contraption");
                })
                .thenWaitUntil(() -> {
                    BlockPos landed = new BlockPos(5, 16 - 7, 5);
                    h.assertBlockPresent(PcBlocks.MINE_CAGE.get(), landed);
                    h.assertFalse(h.getBlockState(landed).getValue(BlockStateProperties.OPEN), "door stays closed");
                    MineHoistBlockEntity be = h.getBlockEntity(hoist);
                    HoistUnit u = binding(h).world().subsystem(HoistSubsystem.class).unit(be.simId());
                    h.assertTrue(u != null && u.landingAtCage() == 1, "hoist twin stands at landing 1");
                })
                .thenSucceed();
    }

    /** Без каната подъём не пускается; кнопка посадки ничего не трогает. */
    @GameTest(template = TALL, timeoutTicks = 300)
    public static void hoistRefusesWithoutRope(GameTestHelper h) {
        shaftSite(h);
        h.setBlock(new BlockPos(5, 19, 5), facing(PcBlocks.HEADFRAME.get().defaultBlockState(), Direction.NORTH));
        h.setBlock(new BlockPos(5, 22, 5), facing(PcBlocks.HEADFRAME.get().defaultBlockState(), Direction.NORTH));
        BlockPos cage = new BlockPos(5, 16, 5);
        h.setBlock(cage, facing(PcBlocks.MINE_CAGE.get().defaultBlockState(), Direction.NORTH));
        BlockPos hoist = new BlockPos(5, 16, 9);
        h.setBlock(hoist, facing(PcBlocks.MINE_HOIST.get().defaultBlockState(), Direction.NORTH));
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(((MineHoistBlockEntity) h.getBlockEntity(hoist)).shaft() != null, "shaft found"))
                .thenExecute(() -> ((MineHoistBlockEntity) h.getBlockEntity(hoist)).onButton(player, 2))
                .thenIdle(40)
                .thenExecute(() -> h.assertBlockPresent(PcBlocks.MINE_CAGE.get(), cage))
                .thenSucceed();
    }

    /** Меню панели передаёт номера кнопок без пересечения с посадками. */
    @GameTest(template = "gametest/empty_3x3x3")
    public static void panelButtonsDoNotOverlapLandings(GameTestHelper h) {
        h.assertTrue(HoistMenu.BUTTON_STOP >= dev.denfry.promptcritical.mod.mine.HoistGeometry.MAX_LANDINGS, "button ids");
        h.succeed();
    }

    // --- водоотлив ------------------------------------------------------------------------------------------

    /** Каменный массив y = 0…5 (насос в почве штрека не касается пустоты под ним) со штреком 2×1 на y = 2…3. */
    private static void drift(GameTestHelper h, int x0, int x1) {
        for (int x = 0; x < 40; x++)
            for (int y = 0; y <= 5; y++)
                for (int z = 3; z <= 7; z++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());
        for (int x = x0; x <= x1; x++) {
            h.setBlock(new BlockPos(x, 2, Z), Blocks.AIR.defaultBlockState());
            h.setBlock(new BlockPos(x, 3, Z), Blocks.AIR.defaultBlockState());
        }
    }

    private static MineNetwork mine(GameTestHelper h, BlockPos rel) {
        MineNetwork n = binding(h).mines().mineAt(h.absolutePos(rel));
        h.assertTrue(n != null && n.ready(), "mine traced at " + rel.toShortString());
        return n;
    }

    private static int floodCount(GameTestHelper h, int x0, int x1) {
        int c = 0;
        for (int x = x0; x <= x1; x++)
            for (int y = 2; y <= 3; y++)
                if (h.getLevel().getFluidState(h.absolutePos(new BlockPos(x, y, Z))).getType() == PcFluids.MINE_WATER.source.get()) c++;
        return c;
    }

    /**
     * Вода сверх водосборника — блоки шахтной воды снизу штрека (1 м³ на блок); отбор воды снимает их обратно, и
     * снятый блок не наполняется заново (шахтная вода не даёт источников).
     */
    @GameTest(template = LONG, timeoutTicks = 600)
    public static void excessWaterFloodsDriftAndRecedes(GameTestHelper h) {
        drift(h, 1, 20);
        BlockPos pump = new BlockPos(15, 1, Z);                   // насос без вала — якорь шахты
        h.setBlock(pump.east(), Blocks.AIR.defaultBlockState());
        h.setBlock(pump, facing(PcBlocks.MINE_DRAINAGE_PUMP.get().defaultBlockState(), Direction.NORTH));
        MineNetwork[] n = new MineNetwork[1];
        h.startSequence()
                .thenWaitUntil(() -> n[0] = mine(h, new BlockPos(10, 2, Z)))
                .thenExecute(() -> n[0].drainage().injectM3(n[0].drainage().capacityM3() + 12))
                .thenWaitUntil(() -> {
                    int placed = n[0].floodBlocks().length;
                    h.assertTrue(placed >= 12 && placed <= 14, "flood blocks 12–14 (filtration adds a little): " + placed);
                    StringBuilder where = new StringBuilder();
                    for (long k : n[0].floodBlocks()) {
                        BlockPos r = BlockPos.of(k).subtract(h.absolutePos(BlockPos.ZERO));
                        where.append(' ').append(r.toShortString()).append('=').append(h.getBlockState(r).getBlock());
                    }
                    h.assertValueEqual(floodCount(h, 1, 20), placed, "mine water sources in the drift;" + where);
                    // сначала нижний ряд
                    for (long k : n[0].floodBlocks()) h.assertValueEqual(BlockPos.of(k).getY(), h.absolutePos(BlockPos.ZERO).getY() + 2, "floor row first");
                })
                .thenExecute(() -> n[0].drainage().injectM3(-8))
                .thenWaitUntil(() -> {
                    int placed = n[0].floodBlocks().length;
                    h.assertTrue(placed <= 7, "receded: " + placed);
                    h.assertValueEqual(floodCount(h, 1, 20), placed, "water blocks follow the list");
                })
                .thenIdle(60)
                .thenExecute(() -> h.assertValueEqual(floodCount(h, 1, 20), n[0].floodBlocks().length, "removed blocks do not refill"))
                .thenSucceed();
    }

    /** Насос ЦНС на валу Create в штреке качает воду из водосборника: подача есть, объём падает, насос в ходу. */
    @GameTest(template = LONG, timeoutTicks = 900)
    public static void drainagePumpLowersSump(GameTestHelper h) {
        drift(h, 1, 20);
        BlockPos pump = new BlockPos(10, 1, Z);                   // в почве штрека, вал слева (запад)
        h.setBlock(pump.east(), Blocks.AIR.defaultBlockState());
        h.setBlock(pump, facing(PcBlocks.MINE_DRAINAGE_PUMP.get().defaultBlockState(), Direction.NORTH));
        motor(h, pump.west(), Direction.EAST, 64);
        MineNetwork[] n = new MineNetwork[1];
        double[] start = new double[1];
        h.startSequence()
                .thenWaitUntil(() -> {
                    n[0] = mine(h, new BlockPos(10, 2, Z));
                    BlockPos abs = h.absolutePos(pump);
                    h.assertTrue(binding(h).mines().scanOf(n[0]).pumps.contains(abs.asLong()), "pump found by the mine scan");
                })
                .thenWaitUntil(() -> h.assertTrue(OpsGameTests.defects(h, h.<MineDrainagePumpBlockEntity>getBlockEntity(pump)),
                        "pump primed, without installation defects"))
                .thenExecute(() -> {
                    n[0].drainage().injectM3(50);
                    start[0] = n[0].drainage().volumeM3();
                })
                .thenWaitUntil(() -> {
                    h.assertTrue(n[0].drainage().pumpedM3s() > 0, "pump delivers");
                    h.assertTrue(n[0].drainage().volumeM3() < start[0] - 0.5, "sump level falls");
                    h.assertTrue(h.getBlockState(pump).getValue(PcProperties.RUNNING), "pump running");
                    MineDrainagePumpBlockEntity be = h.getBlockEntity(pump);
                    h.assertTrue(be.headM() > 0, "head to surface");
                })
                .thenSucceed();
    }
}

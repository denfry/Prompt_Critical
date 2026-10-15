package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.content.contraptions.gantry.GantryCarriageBlock;
import com.simibubi.create.content.contraptions.gantry.GantryContraptionEntity;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.gantry.GantryShaftBlock;
import com.simibubi.create.content.kinetics.gantry.GantryShaftBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.mine.PoweredRoofSupportBlock;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.mining.MineNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Механизированная лава в мире (этап 8в M2, 08 §8.2.3): секция мехкрепи распирается сама, распёртую поршень Create не
 * двигает, разгруженную передвигает целиком; распёртые секции держат широкую кровлю; комбайн на gantry режет забой
 * шнеками, стоит, пока столбец не вынут, и нагружает вал.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LongwallGameTests {
    private static final String MID = "gametest/empty_12x12x12";
    private static final String LONG = "gametest/empty_40x10x10";

    private LongwallGameTests() {
    }

    private static SimLevelBinding binding(GameTestHelper h) {
        SimLevelBinding b = SimBindings.get(h.getLevel());
        if (b == null) throw new IllegalStateException("no simulation binding for the test level");
        return b;
    }

    private static BlockState facing(BlockState s, Direction d) {
        return s.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
    }

    private static void motor(GameTestHelper h, BlockPos rel, Direction toward, int rpm) {
        h.setBlock(rel, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, toward));
        h.runAfterDelay(1, () -> setRpm(h, rel, rpm));
    }

    private static void setRpm(GameTestHelper h, BlockPos rel, int rpm) {
        ((CreativeMotorBlockEntity) h.getBlockEntity(rel)).generatedSpeed.setValue(rpm);
    }

    private static boolean set(GameTestHelper h, BlockPos ctrl) {
        BlockState s = h.getBlockState(ctrl);
        return s.is(PcBlocks.POWERED_ROOF_SUPPORT.get()) && PoweredRoofSupportBlock.set(s);
    }

    // --- мехкрепь -------------------------------------------------------------------------------------------

    /**
     * Секция (контроллер 5,2,5, фасадом на север — клетки z = 5…6) распирается сама; поршень Create за ней её не
     * двигает. Сигнал редстоуна разгружает секцию, и поршень передвигает её на блок целиком; без сигнала она снова
     * распёрта.
     */
    @GameTest(template = MID, timeoutTicks = 600)
    public static void pistonAdvancesOnlyLoweredSupport(GameTestHelper h) {
        BlockPos ctrl = new BlockPos(5, 2, 5), part = new BlockPos(5, 1, 6);
        h.setBlock(ctrl, facing(PcBlocks.POWERED_ROOF_SUPPORT.get().defaultBlockState(), Direction.NORTH));
        BlockPos piston = new BlockPos(5, 2, 7);
        h.setBlock(piston, AllBlocks.MECHANICAL_PISTON.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.NORTH)
                .setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, true));
        h.setBlock(piston.south(), AllBlocks.PISTON_EXTENSION_POLE.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        BlockPos m = new BlockPos(6, 2, 7);
        BlockPos redstone = new BlockPos(4, 2, 5);
        int[] rpm = {64};
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(set(h, ctrl), "support sets itself"))
                .thenExecute(() -> {
                    BlockState ps = h.getBlockState(part);
                    h.assertTrue(ps.is(PcBlocks.MULTIBLOCK_PART.get()), "part cell");
                    h.assertFalse(BlockMovementChecks.isMovementAllowed(ps, h.getLevel(), h.absolutePos(part)), "set support is immovable");
                    h.assertTrue(BlockMovementChecks.isBlockAttachedTowards(ps, h.getLevel(), h.absolutePos(part), Direction.UP),
                            "cells hold together");
                    h.assertFalse(BlockMovementChecks.isBlockAttachedTowards(ps, h.getLevel(), h.absolutePos(part), Direction.SOUTH),
                            "not glued to the outside");
                    motor(h, m, Direction.WEST, rpm[0]);
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    h.assertTrue(set(h, ctrl), "set support did not move");
                    h.setBlock(redstone, Blocks.REDSTONE_BLOCK.defaultBlockState());
                    setRpm(h, m, 0);
                })
                .thenWaitUntil(() -> h.assertFalse(set(h, ctrl), "redstone lowers the support"))
                .thenExecute(() -> {
                    h.assertTrue(BlockMovementChecks.isMovementAllowed(h.getBlockState(part), h.getLevel(), h.absolutePos(part)),
                            "lowered support moves");
                    setRpm(h, m, rpm[0]);
                })
                .thenExecuteAfter(30, () -> {
                    // Знак скорости, которой поршень выдвигается, зависит от стороны вала — пробуем обратный.
                    if (h.getBlockState(ctrl).is(PcBlocks.POWERED_ROOF_SUPPORT.get())) {
                        rpm[0] = -rpm[0];
                        setRpm(h, m, 0);
                        h.runAfterDelay(2, () -> setRpm(h, m, rpm[0]));
                    }
                })
                .thenWaitUntil(() -> {
                    BlockPos moved = ctrl.north();
                    h.assertBlockPresent(PcBlocks.POWERED_ROOF_SUPPORT.get(), moved);
                    for (int y = 1; y <= 3; y++) {
                        if (y != 2) h.assertBlockPresent(PcBlocks.MULTIBLOCK_PART.get(), new BlockPos(5, y, 4));
                        h.assertBlockPresent(PcBlocks.MULTIBLOCK_PART.get(), new BlockPos(5, y, 5));
                    }
                })
                .thenExecute(() -> h.setBlock(redstone, Blocks.AIR.defaultBlockState()))
                .thenWaitUntil(() -> h.assertTrue(set(h, ctrl.north()), "support sets again at the new place"))
                .thenSucceed();
    }

    /**
     * Песчаная кровля над выработкой 8×8 (пролёт 8 больше допустимых 6): распёртые секции рядом с каждой клеткой держат
     * её; разгруженные — нет, кровля ждёт обрушения; снова распёртые — снимают ожидание.
     */
    @GameTest(template = LONG, timeoutTicks = 800)
    public static void setSupportsHoldWideRoof(GameTestHelper h) {
        for (int x = 0; x <= 11; x++)
            for (int y = 1; y <= 6; y++)
                for (int z = 0; z <= 9; z++)
                    h.setBlock(new BlockPos(x, y, z), (y == 5 ? Blocks.SANDSTONE : Blocks.STONE).defaultBlockState());
        for (int x = 2; x <= 9; x++)
            for (int y = 2; y <= 4; y++)
                for (int z = 1; z <= 8; z++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
        BlockPos[] supports = new BlockPos[6];
        int i = 0;
        for (int x : new int[] {3, 6, 9})
            for (int z : new int[] {2, 6}) {
                supports[i] = new BlockPos(x, 3, z);
                h.setBlock(supports[i++], facing(PcBlocks.POWERED_ROOF_SUPPORT.get().defaultBlockState(), Direction.NORTH));
            }
        SimLevelBinding b = binding(h);
        h.startSequence()
                .thenWaitUntil(() -> {
                    for (BlockPos s : supports) h.assertTrue(set(h, s), "support set " + s.toShortString());
                    MineNetwork n = b.mines().mineAt(h.absolutePos(new BlockPos(5, 2, 4)));
                    h.assertTrue(n != null && n.ready(), "mine traced");
                })
                .thenExecute(() -> {
                    for (int x = 2; x <= 9; x++)
                        for (int z = 1; z <= 8; z++)
                            b.mines().onBlockBroken(h.absolutePos(new BlockPos(x, 4, z)), Blocks.STONE.defaultBlockState());
                    b.mines().onBlockChanged(h.absolutePos(new BlockPos(5, 4, 4)));
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    h.assertValueEqual(b.mines().pendingRoofFalls(), 0, "set supports hold the roof");
                    for (BlockPos s : supports) h.setBlock(s.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
                })
                .thenWaitUntil(() -> h.assertTrue(b.mines().pendingRoofFalls() >= 64, "lowered supports leave the roof unsupported: "
                        + b.mines().pendingRoofFalls()))
                .thenExecute(() -> {
                    for (BlockPos s : supports) h.setBlock(s.west(), Blocks.AIR.defaultBlockState());
                })
                .thenWaitUntil(() -> h.assertValueEqual(b.mines().pendingRoofFalls(), 0, "set again, roof held"))
                .thenSucceed();
    }

    // --- комбайн --------------------------------------------------------------------------------------------

    /**
     * Лава вдоль x: забой из угля пласта на z = 3 высотой 3 (y = 2…4), комбайн на z = 4 (контроллер 20,2,4, фасад на
     * север), каретка gantry за ним на z = 5, вал gantry на z = 6 от мотора Create. Каретка собирает контрапцию с
     * комбайном; шнеки режут забой: отстающий — почвенный ряд, ведущий — верхние; первый столбец не раньше 10 с (подача
     * ≤ 6 м/мин), пока он режется, контрапция стоит, а вал несёт нагрузку резания. Уголь с анализом падает за корпус.
     */
    @GameTest(template = LONG, timeoutTicks = 1600)
    public static void shearerCutsFaceOnGantry(GameTestHelper h) {
        for (int x = 0; x < 40; x++)
            for (int z = 2; z <= 7; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE.defaultBlockState());
        for (int x = 1; x <= 38; x++)
            for (int y = 2; y <= 4; y++) h.setBlock(new BlockPos(x, y, 3), PcBlocks.COAL_SEAM.get().defaultBlockState());
        BlockState shaft = AllBlocks.GANTRY_SHAFT.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST);
        for (int x = 1; x <= 38; x++) {
            GantryShaftBlock.Part p = x == 1 ? GantryShaftBlock.Part.START : x == 38 ? GantryShaftBlock.Part.END : GantryShaftBlock.Part.MIDDLE;
            h.setBlock(new BlockPos(x, 2, 6), shaft.setValue(GantryShaftBlock.PART, p));
        }
        BlockPos ctrl = new BlockPos(20, 2, 4);
        h.setBlock(ctrl, facing(PcBlocks.LONGWALL_SHEARER.get().defaultBlockState(), Direction.NORTH));
        BlockState carriage = AllBlocks.GANTRY_CARRIAGE.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.NORTH);
        if (!GantryCarriageBlock.isValidGantryShaftAxis(carriage, shaft)) carriage = carriage.cycle(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE);
        h.setBlock(new BlockPos(20, 2, 5), carriage);
        long[] started = {-1};
        boolean[] loaded = {false};
        h.startSequence()
                .thenExecute(() -> {
                    h.assertBlockPresent(PcBlocks.MULTIBLOCK_PART.get(), new BlockPos(19, 2, 4));
                    h.assertBlockPresent(PcBlocks.MULTIBLOCK_PART.get(), new BlockPos(21, 2, 4));
                    motor(h, new BlockPos(0, 2, 6), Direction.EAST, 64);
                })
                .thenWaitUntil(() -> {
                    h.assertBlockNotPresent(PcBlocks.LONGWALL_SHEARER.get(), ctrl);
                    h.assertBlockNotPresent(PcBlocks.MULTIBLOCK_PART.get(), new BlockPos(19, 2, 4));
                    h.assertTrue(!h.getLevel().getEntitiesOfClass(GantryContraptionEntity.class,
                            new AABB(h.absolutePos(BlockPos.ZERO)).expandTowards(40, 10, 10)).isEmpty(), "shearer rides a gantry contraption");
                    started[0] = h.getTick();
                })
                .thenWaitUntil(() -> {
                    GantryShaftBlockEntity s = (GantryShaftBlockEntity) h.getBlockEntity(new BlockPos(20, 2, 6));
                    if (s.hasNetwork() && s.getOrCreateNetwork().calculateStress() > 0) loaded[0] = true;
                    int cut = 0;
                    boolean floor = false, upper = false;
                    for (int x = 1; x <= 38; x++)
                        for (int y = 2; y <= 4; y++)
                            if (h.getBlockState(new BlockPos(x, y, 3)).isAir()) {
                                cut++;
                                floor |= y == 2;
                                upper |= y > 2;
                            }
                    h.assertTrue(cut >= 3 && floor && upper, "drums cut floor and upper rows: " + cut);
                })
                .thenExecute(() -> {
                    h.assertTrue(h.getTick() - started[0] >= 200, "first column took at least 10 s: " + (h.getTick() - started[0]));
                    h.assertTrue(loaded[0], "cutting loads the gantry shaft network");
                    var items = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(BlockPos.ZERO)).expandTowards(40, 10, 10));
                    h.assertTrue(items.stream().anyMatch(e -> e.getItem().is(PcItems.RAW_COAL.get())), "raw coal dropped behind the shearer");
                    GantryContraptionEntity g = h.getLevel().getEntitiesOfClass(GantryContraptionEntity.class,
                            new AABB(h.absolutePos(BlockPos.ZERO)).expandTowards(40, 10, 10)).getFirst();
                    double moved = Math.abs(g.getAnchorVec().x - h.absolutePos(new BlockPos(20, 2, 5)).getX());
                    h.assertTrue(moved < 3, "contraption stalled while cutting, moved " + moved);
                })
                .thenSucceed();
    }
}

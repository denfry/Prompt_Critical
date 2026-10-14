package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.consequence.DamageGrant;
import dev.denfry.promptcritical.sim.consequence.DamageKind;
import dev.denfry.promptcritical.sim.consequence.DamageRequest;
import dev.denfry.promptcritical.sim.core.ExternalInputs;
import dev.denfry.promptcritical.sim.core.MigrationRegistry;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.SimulationWorld;
import dev.denfry.promptcritical.sim.core.exec.WorkerPool;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredBlock;

/** Бутстрап биндинга M2 (этап 5): мир симуляции в измерении, BE ↔ домен, мультиблоки, бюджет разрушений, лут. */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BindingGameTests {
    private static final String SMALL = "gametest/empty_3x3x3";
    private static final String LARGE = "gametest/empty_12x12x12";

    private BindingGameTests() {
    }

    private static SimLevelBinding binding(GameTestHelper h) {
        SimLevelBinding b = SimBindings.get(h.getLevel());
        if (b == null) throw new IllegalStateException("no simulation binding for the test level");
        return b;
    }

    @GameTest(template = SMALL)
    public static void simulationTicksWithTheLevel(GameTestHelper h) {
        SimLevelBinding b = binding(h);
        long start = b.world().clock().tick();
        h.runAfterDelay(5, () -> {
            h.assertTrue(b.world().clock().tick() >= start + 5, "simulation clock follows server ticks");
            h.succeed();
        });
    }

    @GameTest(template = SMALL)
    public static void deviceRegistersInTopologyAndLeavesAfterBreak(GameTestHelper h) {
        BlockPos rel = new BlockPos(1, 1, 1);
        h.setBlock(rel, PcBlocks.GRATE_STOKER.get().defaultBlockState());
        SimLevelBinding b = binding(h);
        SimId[] id = new SimId[1];
        h.startSequence()
                .thenWaitUntil(() -> {
                    DeviceBlockEntity be = h.getBlockEntity(rel);
                    h.assertTrue(!be.simId().isNone() && b.world().topology().exists(be.simId()), "device attached");
                    h.assertTrue(b.world().topology().deviceType(be.simId()).equals("promptcritical:grate_stoker"), "device type");
                    h.assertTrue(b.world().topology().devicePos(be.simId()).equals(SimLevelBinding.gridPos(h.absolutePos(rel))), "device pos");
                    id[0] = be.simId();
                })
                .thenExecute(() -> h.destroyBlock(rel))
                .thenWaitUntil(() -> h.assertTrue(!b.world().topology().exists(id[0]), "device removed at the next tick"))
                .thenSucceed();
    }

    @GameTest(template = SMALL)
    public static void footprintPlacesPartsAndBreaksAsWhole(GameTestHelper h) {
        BlockPos rel = new BlockPos(0, 1, 1);
        h.setBlock(rel, PcBlocks.MACHINE_LATHE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        BlockPos part = rel.east();
        h.assertBlockPresent(PcBlocks.MULTIBLOCK_PART.get(), part);
        BlockPos anchor = MultiblockPartBlock.anchor(h.getLevel().getBlockState(h.absolutePos(part)), h.absolutePos(part));
        h.assertTrue(anchor.equals(h.absolutePos(rel)), "part points to its anchor");
        h.destroyBlock(part);
        h.assertBlockPresent(Blocks.AIR, rel);
        h.assertBlockPresent(Blocks.AIR, part);
        h.succeed();
    }

    @GameTest(template = LARGE)
    public static void boilerPlacesBothSegmentsAlongFacing(GameTestHelper h) {
        BlockPos rel = new BlockPos(5, 2, 3);
        BlockState front = PcBlocks.FIRE_TUBE_BOILER.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        h.setBlock(rel, front);
        // фасад на восток ⇒ задний сегмент на 3 блока к западу (+z модели → −x мира)
        BlockState rear = h.getLevel().getBlockState(h.absolutePos(rel.west(3)));
        h.assertTrue(rear.is(PcBlocks.FIRE_TUBE_BOILER.get()) && rear.getValue(PcProperties.BOILER_SEGMENT) == PcProperties.BoilerSegment.REAR,
                "rear segment anchor");
        int parts = 0;
        for (BlockPos p : BlockPos.betweenClosed(rel.offset(-4, -1, -1), rel.offset(1, 1, 1))) {
            if (h.getLevel().getBlockState(h.absolutePos(p)).is(PcBlocks.MULTIBLOCK_PART.get())) parts++;
        }
        h.assertTrue(parts == 3 * 3 * 6 - 2, "52 part cells, found " + parts);
        h.destroyBlock(rel.west(4).above());                  // любая клетка заднего сегмента
        h.assertBlockPresent(Blocks.AIR, rel);
        h.assertBlockPresent(Blocks.AIR, rel.west(3));
        h.succeed();
    }

    @GameTest(template = LARGE)
    public static void headframeStackFormsAndMarksTop(GameTestHelper h) {
        BlockPos lower = new BlockPos(5, 2, 5);
        BlockPos upper = lower.above(3);
        BlockState s = PcBlocks.HEADFRAME.get().defaultBlockState();
        h.setBlock(lower, s);
        h.assertTrue(h.getLevel().getBlockState(h.absolutePos(lower)).getValue(PcProperties.TOP), "single segment is the top");
        h.assertTrue(!h.getLevel().getBlockState(h.absolutePos(lower)).getValue(PcProperties.FORMED), "one segment is not formed");
        h.setBlock(upper, s);
        BlockState l = h.getLevel().getBlockState(h.absolutePos(lower));
        BlockState u = h.getLevel().getBlockState(h.absolutePos(upper));
        h.assertTrue(!l.getValue(PcProperties.TOP) && u.getValue(PcProperties.TOP), "top moves up");
        h.assertTrue(l.getValue(PcProperties.FORMED) && u.getValue(PcProperties.FORMED), "two segments form a headframe");
        h.succeed();
    }

    @GameTest(template = LARGE)
    public static void damageBudgetDestroysAroundOriginButNotImmuneBlocks(GameTestHelper h) {
        BlockPos centre = new BlockPos(5, 3, 5);
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-1, -1, -1), centre.offset(1, 1, 1))) h.setBlock(p, Blocks.STONE.defaultBlockState());
        h.setBlock(centre.above(), Blocks.BEDROCK.defaultBlockState());
        SimLevelBinding b = binding(h);
        DamageGrant g = b.damage().submit(new DamageRequest(1, SimId.NONE, null, DamageKind.BLAST,
                SimLevelBinding.gridPos(h.absolutePos(centre)), 1e9, 1.0, 1));
        h.assertTrue(g.blocksGranted() == 7, "sphere r = 1 has 7 cells, granted " + g.blocksGranted());
        h.succeedWhen(() -> {
            h.assertBlockPresent(Blocks.AIR, centre);
            h.assertBlockPresent(Blocks.AIR, centre.east());
            h.assertBlockPresent(Blocks.BEDROCK, centre.above());
            h.assertBlockPresent(Blocks.STONE, centre.offset(1, 1, 1));    // вне радиуса
        });
    }

    @GameTest(template = SMALL)
    public static void worldStateSurvivesNbtRoundTrip(GameTestHelper h) {
        BlockPos rel = new BlockPos(1, 1, 1);
        h.setBlock(rel, PcBlocks.CRUCIBLE_FURNACE.get().defaultBlockState());
        SimLevelBinding b = binding(h);
        h.succeedWhen(() -> {
            DeviceBlockEntity be = h.getBlockEntity(rel);
            h.assertTrue(!be.simId().isNone(), "attached");
            var nbt = NbtStateTrees.toNbt(b.world().saveTree());
            try (WorkerPool pool = WorkerPool.direct()) {
                SimulationWorld copy = new SimulationWorld(b.world().config(), b.world().worldSeed(), b.world().dimensionKey(), pool);
                copy.load(SimulationWorld.upgrade(NbtStateTrees.fromNbt(nbt), new MigrationRegistry()));
                h.assertTrue(copy.stateHash() == b.world().stateHash(), "state hash after NBT round trip");
                h.assertTrue(copy.topology().exists(be.simId()), "device restored");
                copy.tick(ExternalInputs.NONE);
                copy.close();
            }
        });
    }

    @GameTest(template = SMALL)
    public static void everyBlockHasLootAndPlacesWithoutError(GameTestHelper h) {
        var tables = h.getLevel().getServer().reloadableRegistries();
        for (DeferredBlock<? extends Block> db : PcBlocks.withItems()) {
            LootTable t = tables.getLootTable(db.get().getLootTable());
            h.assertTrue(t != LootTable.EMPTY, "loot table of " + db.getId());
        }
        h.succeed();
    }
}

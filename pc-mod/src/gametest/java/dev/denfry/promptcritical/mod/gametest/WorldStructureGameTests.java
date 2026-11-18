package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.worldgen.WorldFinds;
import dev.denfry.promptcritical.mod.worldgen.WorldStructureFeature;
import dev.denfry.promptcritical.mod.worldgen.WorldStructureFeature.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Структуры мира 11 §11.4.2: асфальтовое озеро и горящий кратер собираются из своих блоков, маяк прячет бесхозный
 * источник в ящике, станция анализа различает природный и обеднённый уран.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorldStructureGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos CENTER = new BlockPos(6, 1, 6);

    private WorldStructureGameTests() {
    }

    private static int count(GameTestHelper h, Block block) {
        int n = 0;
        BlockPos c = h.absolutePos(CENTER);
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-7, -6, -7), c.offset(7, 10, 7))) if (h.getLevel().getBlockState(p).is(block)) n++;
        return n;
    }

    @GameTest(template = LARGE, timeoutTicks = 40)
    public static void pitchLakeAndGasCraterAreBuiltFromTheirBlocks(GameTestHelper h) {
        h.assertTrue(WorldStructureFeature.buildAt(h.getLevel(), Kind.PITCH_LAKE, h.absolutePos(CENTER), RandomSource.create(1)), "lake built");
        h.assertTrue(count(h, PcBlocks.NATURAL_BITUMEN.get()) >= 20, "a lake of bitumen: " + count(h, PcBlocks.NATURAL_BITUMEN.get()));
        h.assertTrue(WorldStructureFeature.buildAt(h.getLevel(), Kind.GAS_CRATER, h.absolutePos(CENTER), RandomSource.create(2)), "crater built");
        h.assertTrue(count(h, PcBlocks.GAS_SEEP_VENT.get()) >= 5, "burning vents in the crater");
        h.succeed();
    }

    @GameTest(template = LARGE, timeoutTicks = 40)
    public static void lighthouseHidesAnOrphanSource(GameTestHelper h) {
        h.assertTrue(WorldStructureFeature.buildAt(h.getLevel(), Kind.RTG_LIGHTHOUSE, h.absolutePos(CENTER), RandomSource.create(3)), "built");
        var player = FakePlayerFactory.getMinecraft(h.getLevel());
        ChestBlockEntity chest = null;
        BlockPos c = h.absolutePos(CENTER);
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-4, -2, -4), c.offset(4, 4, 4)))
            if (h.getLevel().getBlockEntity(p) instanceof ChestBlockEntity ch) chest = ch;
        h.assertTrue(chest != null, "chest at the foot of the tower");
        chest.unpackLootTable(player);
        boolean orphan = false;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack s = chest.getItem(i);
            orphan |= s.is(PcItems.SR90_HEAT_SOURCE.get()) && Boolean.TRUE.equals(s.get(PcDataComponents.ORPHAN_SOURCE.get()));
        }
        h.assertTrue(orphan, "an orphan Sr-90 heat source in the chest");
        h.succeed();
    }

    @GameTest(template = LARGE, timeoutTicks = 20)
    public static void assayStationTellsDepletedUranium(GameTestHelper h) {
        var player = FakePlayerFactory.getMinecraft(h.getLevel());
        ItemStack natural = new ItemStack(PcItems.RAW_URANINITE.get()), oklo = natural.copy();
        oklo.set(PcDataComponents.U235_PPM.get(), 6000);
        h.assertFalse(WorldFinds.assay(player, natural), "natural ore is natural");
        h.assertTrue(WorldFinds.assay(player, oklo), "ore of the natural reactor is depleted");
        h.succeed();
    }
}

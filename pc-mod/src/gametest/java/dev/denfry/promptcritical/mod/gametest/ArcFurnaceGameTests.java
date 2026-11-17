package dev.denfry.promptcritical.mod.gametest;

import com.mojang.authlib.GameProfile;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.steelworks.ArcFurnaceBlockEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** ДСП без шины: лом грузится рукой, дуга «зажигается», но без 6 кВ мощности нет и шихта не греется. */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArcFurnaceGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos EAF = new BlockPos(5, 2, 5);

    private ArcFurnaceGameTests() {
    }

    @GameTest(template = LARGE)
    public static void scrapChargesButNoBusMeansNoArc(GameTestHelper h) {
        h.setBlock(EAF, PcBlocks.ELECTRIC_ARC_FURNACE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        FakePlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "pc_eaf"));
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(((ArcFurnaceBlockEntity) h.getBlockEntity(EAF)).unit() != null, "furnace twin"))
                .thenExecute(() -> {
                    ArcFurnaceBlockEntity f = h.getBlockEntity(EAF);
                    ItemStack scrap = new ItemStack(PcItems.STEEL_SCRAP.get(), 16);
                    f.useItem(p, InteractionHand.MAIN_HAND, scrap);
                    h.assertTrue(f.furnace().massKg() > 0, "scrap charged");
                    h.assertTrue(scrap.isEmpty() || scrap.getCount() < 16, "items consumed");
                    f.use(p);
                    h.assertTrue(f.arc(), "electrodes lowered");
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    ArcFurnaceBlockEntity f = h.getBlockEntity(EAF);
                    h.assertTrue(f.furnace().powerW() == 0, "no 6 kV bus, no arc power");
                    h.assertTrue(f.furnace().tempK() < 300, "the charge stays cold");
                })
                .thenSucceed();
    }
}

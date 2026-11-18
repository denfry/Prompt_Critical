package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.electronics.CleanroomAirlockBlockEntity;
import dev.denfry.promptcritical.mod.electronics.CzochralskiPullerBlockEntity;
import dev.denfry.promptcritical.mod.electronics.ElectronicsBlocks;
import dev.denfry.promptcritical.mod.electronics.ElectronicsComponents;
import dev.denfry.promptcritical.mod.electronics.ElectronicsPanels;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Кремний (11 §11.5): шлюз видит замкнутую комнату 3×3×3 из панелей и HEPA-фильтров, а пролом в стене её размыкает;
 * установка Чохральского на валу Create под аргоном вытягивает монокристаллический слиток.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ElectronicsGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    /** Шлюз смотрит на север; комната — к югу от него, кубом 3×3×3 с центром в (5, 3, 6). */
    private static final BlockPos AIRLOCK = new BlockPos(5, 3, 4), PULLER = new BlockPos(3, 2, 3);

    private ElectronicsGameTests() {
    }

    private static void room(GameTestHelper h) {
        for (int x = 3; x <= 7; x++)
            for (int y = 1; y <= 5; y++)
                for (int z = 4; z <= 8; z++) {
                    boolean shell = x == 3 || x == 7 || y == 1 || y == 5 || z == 4 || z == 8;
                    if (!shell) continue;
                    BlockPos p = new BlockPos(x, y, z);
                    h.setBlock(p, y == 5 && (x == 5) ? PcBlocks.HEPA_FILTER.get() : PcBlocks.CLEANROOM_PANEL.get());
                }
        h.setBlock(AIRLOCK, PcBlocks.CLEANROOM_AIRLOCK.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @GameTest(template = LARGE, timeoutTicks = 300)
    public static void airlockSeesTheRoomAndItsFilters(GameTestHelper h) {
        room(h);
        h.startSequence()
                .thenWaitUntil(() -> {
                    CleanroomAirlockBlockEntity a = h.getBlockEntity(AIRLOCK);
                    h.assertTrue(a.enclosed(), "room enclosed");
                    h.assertTrue(a.roomVolume() == 27, "3×3×3 room: " + a.roomVolume());
                })
                // пролом в стене — комната больше не замкнута
                .thenExecute(() -> h.setBlock(new BlockPos(7, 3, 6), net.minecraft.world.level.block.Blocks.AIR))
                .thenWaitUntil(() -> h.assertTrue(!((CleanroomAirlockBlockEntity) h.getBlockEntity(AIRLOCK)).enclosed(), "breach opens the room"))
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 2600)
    public static void czochralskiPullsASingleCrystal(GameTestHelper h) {
        h.setBlock(PULLER, PcBlocks.CZOCHRALSKI_PULLER.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        BlockPos motor = PULLER.south();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64);
                    CzochralskiPullerBlockEntity p = h.getBlockEntity(PULLER);
                    p.panelItems().setStackInSlot(0, new ItemStack(PcItems.POLYSILICON.get()));
                    // 2 мм/мин — верх окна: слиток за ~110 мин физики
                    for (int i = 0; i < 4; i++) p.onPanelButton(net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel()),
                            ElectronicsPanels.BTN_PULL_UP);
                })
                .thenExecuteFor(2400, () -> {
                    IFluidHandler f = ((CzochralskiPullerBlockEntity) h.getBlockEntity(PULLER)).fluids(null);
                    f.fill(new FluidStack(PcFluids.ARGON.source.get(), 50), IFluidHandler.FluidAction.EXECUTE);
                })
                .thenWaitUntil(() -> {
                    ItemStack ingot = ((CzochralskiPullerBlockEntity) h.getBlockEntity(PULLER)).panelItems().getStackInSlot(1);
                    h.assertTrue(ElectronicsBlocks.isMonoIngot(ingot), "single-crystal ingot: " + ingot.get(ElectronicsComponents.INGOT.get()));
                })
                .thenSucceed();
    }
}

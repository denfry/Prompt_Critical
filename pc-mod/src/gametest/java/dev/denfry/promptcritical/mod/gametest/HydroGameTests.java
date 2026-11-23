package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.powerhouse.HydroTurbineBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.hydraulics.HydroTurbine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** ГЭС в миниатюре: пруд на 7 блоков выше турбины, затвор в воде — агрегат собирается, крутится и останавливается. */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HydroGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos TURBINE = new BlockPos(2, 2, 2), GATE = new BlockPos(8, 8, 8);

    private HydroGameTests() {
    }

    @GameTest(template = LARGE, timeoutTicks = 2400)
    public static void pondAboveTheTurbineDrivesIt(GameTestHelper h) {
        for (int x = 7; x <= 10; x++) for (int z = 7; z <= 10; z++) {
            h.setBlock(new BlockPos(x, 7, z), Blocks.STONE);
            if (!(x == GATE.getX() && z == GATE.getZ())) h.setBlock(new BlockPos(x, 8, z), Blocks.WATER);
        }
        h.setBlock(GATE, PcBlocks.DAM_GATE.get().defaultBlockState().setValue(BlockStateProperties.OPEN, true));
        h.setBlock(TURBINE, PcBlocks.HYDRO_TURBINE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        h.startSequence()
                .thenWaitUntil(() -> {
                    HydroTurbineBlockEntity t = h.getBlockEntity(TURBINE);
                    h.assertTrue(t.turbine() != null, "the turbine finds its intake");
                })
                .thenExecute(() -> {
                    HydroTurbine t = ((HydroTurbineBlockEntity) h.getBlockEntity(TURBINE)).turbine();
                    h.assertTrue(Math.abs(t.spec().headM() - 7) < 1e-9, "head: pond surface minus turbine, got " + t.spec().headM());
                })
                .thenWaitUntil(() -> {
                    HydroTurbine t = ((HydroTurbineBlockEntity) h.getBlockEntity(TURBINE)).turbine();
                    h.assertTrue(t.omega() > 0.95 * t.spec().nominalOmega(), "spins up: " + t.omega() / t.spec().nominalOmega());
                })
                .thenExecute(() -> h.assertTrue(((HydroTurbineBlockEntity) h.getBlockEntity(TURBINE)).outputRpm() > 0, "Create shaft turns"))
                .thenExecute(() -> h.setBlock(GATE, PcBlocks.DAM_GATE.get().defaultBlockState().setValue(BlockStateProperties.OPEN, false)))
                .thenWaitUntil(() -> {
                    HydroTurbine t = ((HydroTurbineBlockEntity) h.getBlockEntity(TURBINE)).turbine();
                    h.assertTrue(t.gate() < 1e-3, "lowering the intake gate closes the wicket gates");
                })
                // Затвор залило водой (или сломали) на ходу: агрегат не роняет сервер, а теряет водозабор и останавливается.
                // Проверка живёт здесь, а не в отдельном тесте: турбина ищет затвор на ±48 блоков, а сервер GameTest не
                // очищает поле между тестами и батчами, так что второй тест с затвором подставил бы турбине чужой водозабор.
                .thenExecute(() -> h.setBlock(GATE, Blocks.WATER))
                .thenWaitUntil(() -> h.assertTrue(((HydroTurbineBlockEntity) h.getBlockEntity(TURBINE)).turbine() == null,
                        "the turbine drops the lost intake on the next scan"))
                .thenExecute(() -> h.assertTrue(((HydroTurbineBlockEntity) h.getBlockEntity(TURBINE)).outputRpm() == 0, "shaft stops"))
                .thenSucceed();
    }
}

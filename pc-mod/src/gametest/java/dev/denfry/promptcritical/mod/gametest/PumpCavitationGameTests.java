package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.acoustics.MachineAcoustics;
import dev.denfry.promptcritical.mod.mine.MineDrainagePumpBlockEntity;
import dev.denfry.promptcritical.mod.mine.MinePanels;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.acoustics.VibrationSpectrum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Кавитация насоса водоотлива (04a §4a.3): насос высоко над водосборником кавитирует — шумит, бьёт в огибающей ВЧ,
 * теряет подачу и ест колесо; опущенный к воде затихает, и после минуты тихой работы засчитан «Тихий насос».
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PumpCavitationGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    /** Высота над водосборником: 7 м — далеко за допустимой высотой всасывания ЦНС. */
    private static final int HIGH_LIFT = 7;

    private PumpCavitationGameTests() {
    }

    @GameTest(template = LARGE, timeoutTicks = 2600)
    public static void liftedPumpCavitatesAndLoweringItMakesItQuiet(GameTestHelper h) {
        BlockPos pump = new BlockPos(5, 2, 5);
        h.setBlock(pump, PcBlocks.MINE_DRAINAGE_PUMP.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        BlockPos motor = pump.west();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        // полскорости: в шаблоне насос качает почти без статического напора и на полной скорости ушёл бы за край
        // характеристики, где кавитирует при любом подпоре
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(32));
        int[] lift = {HIGH_LIFT};
        double[] quietFlow = {0}, liftedFlow = {0};
        h.onEachTick(() -> {
            if (!(h.getBlockEntity(pump) instanceof MineDrainagePumpBlockEntity be) || be.simId().isNone()) return;
            // вместо обхода шахты: подошва выработок и откачка, как их задал бы MineWorld
            be.setSumpFloor(h.absolutePos(pump).getY() - lift[0]);
            be.setPumped(be.capacityM3s(OpsGameTests.binding(h).world().clock().tick()), false);
        });
        double[] loud = new double[2];
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(OpsGameTests.defects(h, h.<MineDrainagePumpBlockEntity>getBlockEntity(pump)), "pump ops unit"))
                .thenWaitUntil(() -> h.assertTrue(!h.<MineDrainagePumpBlockEntity>getBlockEntity(pump).opsStopped(), "pump turns"))
                .thenWaitUntil(() -> h.assertTrue(h.<MineDrainagePumpBlockEntity>getBlockEntity(pump).cavitationIntensity()
                        >= MineDrainagePumpBlockEntity.AUDIBLE_KAPPA, "lifted pump cavitates, kappa "
                        + h.<MineDrainagePumpBlockEntity>getBlockEntity(pump).cavitationIntensity()))
                .thenExecute(() -> {
                    MineDrainagePumpBlockEntity be = h.getBlockEntity(pump);
                    h.assertTrue(be.npshAvailableM() < be.npshRequiredM() * 1.2, "NPSH_a " + be.npshAvailableM() + " near NPSH_r " + be.npshRequiredM());
                    liftedFlow[0] = be.pumpedM3s();
                    var p = MachineAcoustics.probe(h.getLevel(), h.absolutePos(pump));
                    loud[0] = MachineAcoustics.soundPowerDb(h.getLevel(), h.absolutePos(pump), p);
                    VibrationSpectrum s = MachineAcoustics.spectrum(h.getLevel(), h.absolutePos(pump));
                    loud[1] = s.aHf();
                    int[] out = new int[MinePanels.PUMP_CHANNELS];
                    be.readPanel(out);
                    h.assertTrue(out[MinePanels.PUMP_SUCTION_KPA10] < 400, "suction gauge shows the vacuum: " + out[MinePanels.PUMP_SUCTION_KPA10] / 10.0 + " kPa");
                })
                // полминуты развитой кавитации: насос «найден»; колесо уже едят
                .thenIdle(31 * 20)
                .thenExecute(() -> {
                    MineDrainagePumpBlockEntity be = h.getBlockEntity(pump);
                    h.assertTrue(OpsGameTests.ops(h).unit(be.simId()).wear().impeller().cavitation() > 0, "impeller eroding");
                    lift[0] = 0; // насос опустили к водосборнику
                })
                .thenWaitUntil(() -> h.assertTrue(h.<MineDrainagePumpBlockEntity>getBlockEntity(pump).cavitationIntensity() == 0, "quiet at the sump"))
                .thenExecute(() -> {
                    MineDrainagePumpBlockEntity be = h.getBlockEntity(pump);
                    quietFlow[0] = be.pumpedM3s();
                    h.assertTrue(quietFlow[0] > liftedFlow[0], "flow comes back: " + quietFlow[0] + " vs " + liftedFlow[0]);
                    var p = MachineAcoustics.probe(h.getLevel(), h.absolutePos(pump));
                    double now = MachineAcoustics.soundPowerDb(h.getLevel(), h.absolutePos(pump), p);
                    h.assertTrue(loud[0] - now > 3, "cavitation was louder: " + loud[0] + " vs " + now + " dB");
                    h.assertTrue(loud[1] > MachineAcoustics.spectrum(h.getLevel(), h.absolutePos(pump)).aHf(), "shock pulses gone");
                })
                .thenWaitUntil(() -> h.assertTrue(h.<MineDrainagePumpBlockEntity>getBlockEntity(pump).quietReported(), "quiet pump reported"))
                .thenSucceed();
    }
}

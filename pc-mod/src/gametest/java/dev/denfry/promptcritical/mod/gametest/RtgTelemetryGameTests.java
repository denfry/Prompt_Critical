package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.environment.ActivityData;
import dev.denfry.promptcritical.mod.isotopes.IsotopePanels;
import dev.denfry.promptcritical.mod.isotopes.RtgBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.scada.TelemetryGatewayBlockEntity;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import dev.denfry.promptcritical.sim.telemetry.RtuPower;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * РИТЭГ и КП удалённой скважины (12 §12.5.1): шлюз телемеханики вплотную к «Бета-М» питается от него, опрашивает
 * устье и через минуту даёт «Вечную батарейку»; без источника он гаснет.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RtgTelemetryGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos RTG = new BlockPos(4, 2, 4), RTU = new BlockPos(5, 2, 4), WELL = new BlockPos(7, 2, 4);

    private RtgTelemetryGameTests() {
    }

    /** Одиночный «Бета-М» (~10 Вт), как в маячном тесте. */
    private static ItemStack betaM() {
        ItemStack src = new ItemStack(PcItems.SR90_HEAT_SOURCE.get());
        src.set(ActivityData.ACTIVITY.get(), new ActivityData(0, 0.75, 5.1, Map.of(Emitter.SR90.name(), 1.27e15, Emitter.Y90.name(), 1.27e15)));
        return src;
    }

    @GameTest(template = LARGE, timeoutTicks = 1800)
    public static void rtgPowersTheGatewayOfARemoteWell(GameTestHelper h) {
        h.setBlock(RTG, PcBlocks.RTG_THERMOELECTRIC.get());
        h.setBlock(RTU, PcBlocks.RTU_STATION.get());
        h.setBlock(WELL, PcBlocks.WELLHEAD.get());
        h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> ((RtgBlockEntity) h.getBlockEntity(RTG)).panelItems().setStackInSlot(0, betaM()))
                .thenWaitUntil(() -> {
                    TelemetryGatewayBlockEntity g = h.getBlockEntity(RTU);
                    h.assertTrue(g.offGrid(), "gateway sees the RTG");
                    h.assertTrue(g.power().on(), "gateway powered");
                    h.assertTrue(g.power().autonomousS() > 0, "gateway polls the wellhead on the RTG");
                })
                .thenWaitUntil(() -> {
                    // нагрузку РИТЭГ пересчитывает в своём такте — раз в секунду
                    int[] out = new int[IsotopePanels.R_CHANNELS];
                    ((RtgBlockEntity) h.getBlockEntity(RTG)).readPanel(out);
                    h.assertTrue(Math.abs(out[IsotopePanels.R_LOAD_DW] - RtuPower.DEMAND_W * 10) <= 1,
                            "RTG panel shows the gateway's load: " + out[IsotopePanels.R_LOAD_DW] / 10.0 + " W");
                })
                .thenWaitUntil(() -> h.assertTrue(h.<TelemetryGatewayBlockEntity>getBlockEntity(RTU).power().everlasting(),
                        "a minute on the RTG: " + h.<TelemetryGatewayBlockEntity>getBlockEntity(RTU).power().autonomousS() + " s"))
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void gatewayOnAnEmptyRtgGoesDark(GameTestHelper h) {
        h.setBlock(RTG, PcBlocks.RTG_THERMOELECTRIC.get());
        h.setBlock(RTU, PcBlocks.RTU_STATION.get());
        h.setBlock(WELL, PcBlocks.WELLHEAD.get());
        h.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    TelemetryGatewayBlockEntity g = h.getBlockEntity(RTU);
                    h.assertTrue(g.offGrid() && !g.power().on(), "no source, no power");
                    h.assertTrue(g.analogReadings().isEmpty(), "dark gateway reads nothing");
                })
                .thenSucceed();
    }
}

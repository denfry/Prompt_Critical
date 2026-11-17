package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.automation.PowerSupplyBlockEntity;
import dev.denfry.promptcritical.mod.automation.StationBatteryBlockEntity;
import dev.denfry.promptcritical.mod.automation.WiredDevice;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Погашение шины: выпрямитель без батареи гаснет, с батареей держит оперативный ток, и батарея разряжается. */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StationBatteryGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos PSU = new BlockPos(2, 2, 5), BAT = new BlockPos(2, 2, 6), LAMP = new BlockPos(3, 2, 5),
            PSU_BARE = new BlockPos(8, 2, 5);

    private StationBatteryGameTests() {
    }

    private static void place(GameTestHelper h, BlockPos pos, Block block) {
        h.setBlock(pos, block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    private static void wire(GameTestHelper h, BlockPos a, String la, BlockPos b, String lb) {
        WiredDevice da = (WiredDevice) h.getBlockEntity(a), db = (WiredDevice) h.getBlockEntity(b);
        SimBindings.get(h.getLevel()).automation().connect(da, da.wiredKind().indexOf(la), db, db.wiredKind().indexOf(lb));
    }

    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void batteryCarriesControlPowerThroughABlackout(GameTestHelper h) {
        place(h, PSU, PcBlocks.RELAY_POWER_SUPPLY.get());
        place(h, BAT, PcBlocks.STATION_BATTERY.get());
        place(h, LAMP, PcBlocks.INDICATOR_LAMP.get());
        place(h, PSU_BARE, PcBlocks.RELAY_POWER_SUPPLY.get());
        double[] soc = {0};
        h.runAfterDelay(5, () -> {
            ((PowerSupplyBlockEntity) h.getBlockEntity(PSU)).setBenchInput(1);
            ((PowerSupplyBlockEntity) h.getBlockEntity(PSU_BARE)).setBenchInput(1);
            wire(h, PSU, "+24", LAMP, "X1");
            wire(h, LAMP, "X2", PSU, "0");
        });
        h.runAfterDelay(60, () -> {
            h.assertBlockProperty(PSU, BlockStateProperties.POWERED, true);
            ((PowerSupplyBlockEntity) h.getBlockEntity(PSU)).setBenchInput(0);
            ((PowerSupplyBlockEntity) h.getBlockEntity(PSU_BARE)).setBenchInput(0);
            soc[0] = ((StationBatteryBlockEntity) h.getBlockEntity(BAT)).battery().chargeAh();
        });
        h.runAfterDelay(200, () -> {
            h.assertBlockProperty(PSU_BARE, BlockStateProperties.POWERED, false);
            h.assertBlockProperty(PSU, BlockStateProperties.POWERED, true);
            double now = ((StationBatteryBlockEntity) h.getBlockEntity(BAT)).battery().chargeAh();
            h.assertTrue(now < soc[0], "the battery discharges into the lamp: " + soc[0] + " -> " + now);
            h.succeed();
        });
    }
}

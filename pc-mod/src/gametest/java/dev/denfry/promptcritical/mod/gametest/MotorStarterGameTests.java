package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.grid.ElectricMotorBlockEntity;
import dev.denfry.promptcritical.mod.grid.GridPanels;
import dev.denfry.promptcritical.mod.grid.ShaftGeneratorBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.grid.GeneratorUnit;
import dev.denfry.promptcritical.sim.grid.MotorStarter;
import dev.denfry.promptcritical.sim.grid.MotorUnit;
import dev.denfry.promptcritical.sim.grid.VoltageClass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Пусковые модули двигателя (07 §7.6.3): место в щите, передача в сеть и частотный привод на валу Create. */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MotorStarterGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos GEN = new BlockPos(4, 1, 5), MOTOR = new BlockPos(2, 1, 5);

    private MotorStarterGameTests() {
    }

    private static BlockState north(BlockState s) {
        return s.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    }

    private static MotorUnit motor(GameTestHelper h) {
        MotorUnit u = ((ElectricMotorBlockEntity) h.getBlockEntity(MOTOR)).unit();
        h.assertTrue(u != null, "motor twin");
        return u;
    }

    /**
     * Генератор 6,3 кВ → кабель → двигатель 6 кВ с частотным приводом: вал Create идёт с заданной приводом скоростью
     * 32 об/мин при передаче 64, а не ступенью редуктора; модуль выпадает с двигателем.
     */
    @GameTest(template = LARGE, timeoutTicks = 1600)
    public static void frequencyDriveRunsTheShaftAtItsSetSpeed(GameTestHelper h) {
        h.setBlock(GEN, north(PcBlocks.SHAFT_GENERATOR.get().defaultBlockState()));
        h.setBlock(GEN.above(), north(PcBlocks.GENERATOR_EXCITER.get().defaultBlockState()));
        BlockPos crank = GEN.south();
        h.setBlock(crank, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        h.setBlock(GEN.west(), PcBlocks.POWER_CABLE.get().defaultBlockState());
        h.setBlock(MOTOR, north(PcBlocks.ELECTRIC_MOTOR.get().defaultBlockState()));
        var player = TestPlayers.of(h);
        h.startSequence()
                .thenExecute(() -> ((CreativeMotorBlockEntity) h.getBlockEntity(crank)).generatedSpeed.setValue(64))
                .thenWaitUntil(() -> {
                    h.assertTrue(((ShaftGeneratorBlockEntity) h.getBlockEntity(GEN)).unit() != null, "generator twin");
                    motor(h);
                })
                .thenExecute(() -> {
                    GeneratorUnit g = ((ShaftGeneratorBlockEntity) h.getBlockEntity(GEN)).unit();
                    g.setVoltageClass(VoltageClass.MV_6_3);
                    while (g.gearRpm() != 64) g.cycleGear();
                    g.setFieldOn(true);
                    MotorUnit m = motor(h);
                    m.setMediumVoltage(true);
                    while (m.gearRpm() != 64) m.cycleGear();
                    ElectricMotorBlockEntity be = h.getBlockEntity(MOTOR);
                    var slots = be.panelItems();
                    h.assertTrue(!slots.isItemValid(2, new ItemStack(PcItems.RELAY_THERMAL.get())), "a relay does not go into the starter place");
                    h.assertTrue(!slots.isItemValid(0, new ItemStack(PcItems.VARIABLE_FREQUENCY_DRIVE.get())), "a drive does not go into a relay place");
                    // без привода кнопки скорости ничего не задают
                    be.onPanelButton(player, GridPanels.BUTTON_DRIVE_DOWN);
                    h.assertTrue(be.driveRpm() == 64, "no drive, no speed setting");
                    slots.setStackInSlot(2, new ItemStack(PcItems.VARIABLE_FREQUENCY_DRIVE.get()));
                    h.assertTrue(be.installedStarter() == MotorStarter.VFD, "drive installed");
                    for (int i = 0; i < 32; i++) be.onPanelButton(player, GridPanels.BUTTON_DRIVE_DOWN);
                    h.assertTrue(be.driveRpm() == 32, "drive set to 32 RPM: " + be.driveRpm());
                })
                .thenWaitUntil(() -> h.assertTrue(((ShaftGeneratorBlockEntity) h.getBlockEntity(GEN)).unit().terminalVoltageV() > 6000,
                        "the generator is up"))
                .thenExecute(() -> {
                    h.assertTrue(motor(h).starter() == MotorStarter.VFD, "the grid runs the motor through the drive");
                    motor(h).setContactor(true);
                })
                .thenWaitUntil(() -> {
                    float rpm = ((ElectricMotorBlockEntity) h.getBlockEntity(MOTOR)).outputRpm();
                    h.assertTrue(Math.abs(rpm - 32) <= 1, "shaft at the drive speed: " + rpm + " RPM, motor " + motor(h).state());
                })
                .thenExecute(() -> {
                    h.destroyBlock(MOTOR);
                    boolean dropped = h.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(h.absolutePos(MOTOR)).inflate(2))
                            .stream().anyMatch(e -> e.getItem().is(PcItems.VARIABLE_FREQUENCY_DRIVE.get()));
                    h.assertTrue(dropped, "the drive drops with the motor");
                })
                .thenSucceed();
    }
}

package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.grid.ShaftGeneratorBlockEntity;
import dev.denfry.promptcritical.mod.item.CapLampData;
import dev.denfry.promptcritical.mod.mine.CapLampItem;
import dev.denfry.promptcritical.mod.mine.CapLampLightBlock;
import dev.denfry.promptcritical.mod.mine.CapLampService;
import dev.denfry.promptcritical.mod.mine.LampRackBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.grid.GeneratorUnit;
import dev.denfry.promptcritical.sim.grid.LampRackUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

/**
 * Ламповая: светильник на стеллаже заряжается от сети 0,4 кВ (генератор на валу рядом со стеллажом), светильник на
 * голове садится к концу смены и гаснет, свет фары ставится у головы и снимается, бесхозный блок света исчезает сам.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LampRoomGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos GEN = new BlockPos(4, 1, 5), RACK = new BlockPos(5, 1, 5);

    private LampRoomGameTests() {
    }

    private static BlockState north(BlockState s) {
        return s.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    }

    /** Светильник с заданным отданным зарядом, время ещё не отмечено. */
    private static ItemStack lamp(double removedAh) {
        ItemStack s = new ItemStack(PcItems.CAP_LAMP.get());
        s.set(PcDataComponents.CAP_LAMP.get(), new CapLampData(removedAh, 0, 20, 0, -1, 0));
        return s;
    }

    @GameTest(template = LARGE, timeoutTicks = 2400)
    public static void lampChargesOnTheRackFromTheGrid(GameTestHelper h) {
        h.setBlock(GEN, north(PcBlocks.SHAFT_GENERATOR.get().defaultBlockState()));
        h.setBlock(GEN.above(), north(PcBlocks.GENERATOR_EXCITER.get().defaultBlockState()));
        BlockPos motor = GEN.south();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        h.setBlock(RACK, north(PcBlocks.LAMP_RACK.get().defaultBlockState()));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
        double[] soc0 = {0};
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(((ShaftGeneratorBlockEntity) h.getBlockEntity(GEN)).unit() != null, "generator twin");
                    h.assertTrue(((LampRackBlockEntity) h.getBlockEntity(RACK)).unit() != null, "rack twin");
                })
                .thenExecute(() -> {
                    GeneratorUnit g = ((ShaftGeneratorBlockEntity) h.getBlockEntity(GEN)).unit();
                    while (g.gearRpm() != 64) g.cycleGear();
                    g.setFieldOn(true);
                    ItemStack empty = lamp(9.0);
                    soc0[0] = CapLampItem.soc(empty);
                    ((LampRackBlockEntity) h.getBlockEntity(RACK)).lamps().setStackInSlot(0, empty);
                })
                .thenWaitUntil(() -> {
                    LampRackUnit u = ((LampRackBlockEntity) h.getBlockEntity(RACK)).unit();
                    h.assertTrue(u.supplyPu() > 0.9 && u.powerW() > 5, "rectifier draws power: " + u.powerW() + " W at " + u.supplyPu());
                })
                .thenIdle(400) // 24 игровые минуты заряда током 2,5 А
                .thenExecute(() -> {
                    LampRackBlockEntity r = h.getBlockEntity(RACK);
                    ItemStack s = r.lamps().getStackInSlot(0);
                    double soc = CapLampItem.soc(s);
                    h.assertTrue(soc > soc0[0] + 0.05, "battery charges: " + soc0[0] + " -> " + soc);
                    h.assertTrue(r.unit().powerW() > 8 && r.unit().powerW() < 20, "one place draws 10–17 W: " + r.unit().powerW());
                    h.assertTrue(h.getBlockState(RACK).getValue(BlockStateProperties.LIT), "mains lamp lit");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 1200)
    public static void wornLampRunsDownAtTheEndOfTheShiftAndItsLightFollows(GameTestHelper h) {
        // носитель без сущности игрока: свет ставится у «головы» по тем же правилам
        UUID miner = UUID.randomUUID();
        BlockPos eye = h.absolutePos(new BlockPos(6, 3, 6)), feet = eye.below();
        ItemStack head = lamp(9.9); // осталось ≈ 0,7 А·ч — последние три четверти часа смены
        int[] firstLevel = {0};
        h.startSequence()
                .thenExecute(() -> CapLampService.update(head, h.getLevel(), true))
                .thenIdle(40)
                .thenExecute(() -> {
                    CapLampData d = CapLampService.update(head, h.getLevel(), true);
                    CapLampService.updateLight(h.getLevel(), miner, eye, feet, head);
                    BlockPos at = CapLampService.lightOf(miner);
                    h.assertTrue(d.fluxPermil() > 0 && at != null, "the lamp still burns and lights the head: " + d.fluxPermil());
                    h.assertTrue(h.getLevel().getBlockState(at).is(PcBlocks.CAP_LAMP_LIGHT.get()), "light block at the head");
                    firstLevel[0] = h.getLevel().getBlockState(at).getValue(CapLampLightBlock.LEVEL);
                })
                .thenIdle(600) // 36 игровых минут
                .thenExecute(() -> {
                    CapLampData d = CapLampService.update(head, h.getLevel(), true);
                    CapLampService.updateLight(h.getLevel(), miner, eye, feet, head);
                    h.assertTrue(d.fluxPermil() < 200, "run down at the end of the shift: " + d.fluxPermil() + " permil");
                    BlockPos at = CapLampService.lightOf(miner);
                    int now = at == null ? 0 : h.getLevel().getBlockState(at).getValue(CapLampLightBlock.LEVEL);
                    h.assertTrue(now < firstLevel[0], "light fades: " + firstLevel[0] + " -> " + now);
                    // сняли светильник — свет у головы исчез
                    CapLampService.updateLight(h.getLevel(), miner, eye, feet, ItemStack.EMPTY);
                    h.assertTrue(CapLampService.lightOf(miner) == null, "light removed with the lamp");
                    h.assertBlockNotPresent(PcBlocks.CAP_LAMP_LIGHT.get(), new BlockPos(6, 3, 6));
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void orphanLightBlockRemovesItself(GameTestHelper h) {
        BlockPos pos = new BlockPos(3, 3, 3);
        h.setBlock(pos, PcBlocks.CAP_LAMP_LIGHT.get().defaultBlockState());
        h.startSequence()
                .thenIdle(30)
                .thenExecute(() -> h.assertBlockNotPresent(PcBlocks.CAP_LAMP_LIGHT.get(), pos))
                .thenSucceed();
    }
}

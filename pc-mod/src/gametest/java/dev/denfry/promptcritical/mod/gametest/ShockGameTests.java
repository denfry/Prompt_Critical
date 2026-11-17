package dev.denfry.promptcritical.mod.gametest;

import com.mojang.authlib.GameProfile;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.body.ShockService;
import dev.denfry.promptcritical.mod.grid.ShaftGeneratorBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.body.ElectricShock;
import dev.denfry.promptcritical.sim.grid.GeneratorUnit;
import dev.denfry.promptcritical.sim.grid.VoltageClass;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Голая шина под напряжением генератора 6,3 кВ: заземлённого игрока убивает, в прыжке — нет. */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShockGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos GEN = new BlockPos(4, 1, 5), BUSBAR = new BlockPos(3, 1, 5);

    private ShockGameTests() {
    }

    @GameTest(template = LARGE, timeoutTicks = 2400)
    public static void liveBusbarElectrocutesAnEarthedPlayer(GameTestHelper h) {
        h.setBlock(GEN, PcBlocks.SHAFT_GENERATOR.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        h.setBlock(GEN.above(), PcBlocks.GENERATOR_EXCITER.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        BlockPos motor = GEN.south();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
        h.setBlock(BUSBAR, PcBlocks.BUSBAR.get().defaultBlockState());
        BlockPos bus = h.absolutePos(BUSBAR);
        FakePlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "pc_shock"));
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(((ShaftGeneratorBlockEntity) h.getBlockEntity(GEN)).unit() != null, "generator twin"))
                .thenExecute(() -> {
                    GeneratorUnit u = ((ShaftGeneratorBlockEntity) h.getBlockEntity(GEN)).unit();
                    u.setVoltageClass(VoltageClass.MV_6_3);
                    while (u.gearRpm() != 64) u.cycleGear();
                    u.setFieldOn(true);
                })
                .thenWaitUntil(() -> h.assertTrue(SimBindings.get(h.getLevel()).grids().busbarVoltageV(bus) > 6000,
                        "busbar energized: " + SimBindings.get(h.getLevel()).grids().busbarVoltageV(bus)))
                .thenExecute(() -> {
                    // в прыжке у шины: пути в землю нет
                    Vec3 at = Vec3.atBottomCenterOf(bus.west()).add(0.25, 0, 0);
                    p.moveTo(at.x, at.y, at.z);
                    p.setOnGround(false);
                    h.assertTrue(ShockService.check(p) == ElectricShock.Effect.NONE, "no earth path, no current");
                    p.setOnGround(true);
                    h.assertTrue(ShockService.check(p) == ElectricShock.Effect.FIBRILLATION,
                            "3.6 kV phase voltage through an earthed body is fatal at once");
                    // в шаге от шины — касания нет
                    p.moveTo(at.x - 1, at.y, at.z);
                    h.assertTrue(ShockService.check(p) == ElectricShock.Effect.NONE, "a step away is safe");
                })
                .thenSucceed();
    }
}

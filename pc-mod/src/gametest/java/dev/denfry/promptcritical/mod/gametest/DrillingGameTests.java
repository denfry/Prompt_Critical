package dev.denfry.promptcritical.mod.gametest;

import com.mojang.authlib.GameProfile;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.drilling.BopAccumulatorBlockEntity;
import dev.denfry.promptcritical.mod.drilling.DrillersConsoleBlockEntity;
import dev.denfry.promptcritical.mod.drilling.DrillingPanels;
import dev.denfry.promptcritical.mod.drilling.MudHopperBlockEntity;
import dev.denfry.promptcritical.mod.drilling.SeismicRecorderBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.drilling.BopStack;
import dev.denfry.promptcritical.sim.drilling.DrillersMethod;
import dev.denfry.promptcritical.sim.drilling.FormationFluid;
import dev.denfry.promptcritical.sim.drilling.KillSheet;
import dev.denfry.promptcritical.sim.drilling.Prospect;
import dev.denfry.promptcritical.sim.drilling.Trap;
import dev.denfry.promptcritical.sim.drilling.Well;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Буровая I2 целиком: ротор и насос на валах Create, вышка, лебёдка, превентор, манифольд, ёмкости, гидроворонка и пульт.
 * Скважина бурится в газовую залежь с АВПД под ротором, ловит приток, закрывается и глушится методом бурильщика.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DrillingGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos CONSOLE = new BlockPos(1, 2, 1), ROTARY = new BlockPos(6, 2, 6), PUMP = new BlockPos(3, 2, 9),
            HOPPER = new BlockPos(10, 2, 1), BOP = new BlockPos(8, 2, 6), ACCUMULATOR = new BlockPos(4, 2, 1),
            ROTARY_MOTOR = new BlockPos(5, 2, 6);

    private DrillingGameTests() {
    }

    private static void motor(GameTestHelper h, BlockPos at, Direction toward) {
        h.setBlock(at, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, toward));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(at)).generatedSpeed.setValue(64));
    }

    private static void buildRig(GameTestHelper h) {
        var north = BlockStateProperties.HORIZONTAL_FACING;
        h.setBlock(CONSOLE, PcBlocks.DRILLERS_CONSOLE.get().defaultBlockState().setValue(north, Direction.NORTH));
        h.setBlock(ROTARY, PcBlocks.ROTARY_TABLE.get().defaultBlockState().setValue(north, Direction.NORTH));
        motor(h, ROTARY.west(), Direction.EAST);
        h.setBlock(ROTARY.above(3), PcBlocks.DERRICK_SEGMENT.get().defaultBlockState().setValue(north, Direction.NORTH));
        h.setBlock(new BlockPos(6, 2, 9), PcBlocks.DRAWWORKS.get().defaultBlockState().setValue(north, Direction.NORTH));
        h.setBlock(PUMP, PcBlocks.MUD_PUMP.get().defaultBlockState().setValue(north, Direction.NORTH));
        // клетка входа вала — приводной конец (−1, 0, 1) модели: при фасаде на север это (2, 2, 10)
        motor(h, new BlockPos(1, 2, 10), Direction.EAST);
        h.setBlock(BOP, PcBlocks.BOP_ANNULAR.get().defaultBlockState().setValue(north, Direction.NORTH));
        h.setBlock(new BlockPos(8, 2, 3), PcBlocks.CHOKE_MANIFOLD.get().defaultBlockState().setValue(north, Direction.NORTH));
        for (int x = 1; x <= 6; x++)
            h.setBlock(new BlockPos(x, 2, 4), PcBlocks.MUD_TANK.get().defaultBlockState().setValue(north, Direction.NORTH));
        h.setBlock(HOPPER, PcBlocks.MUD_MIXING_HOPPER.get().defaultBlockState().setValue(north, Direction.NORTH));
        h.setBlock(ACCUMULATOR, PcBlocks.BOP_ACCUMULATOR.get().defaultBlockState().setValue(north, Direction.NORTH));
    }

    /**
     * Бурим в газ с АВПД (11,8 кПа/м) на растворе 1,10: приток → сигнал → превентор → SIDPP = Pₚ − ρgH → первый цикл
     * при ICP → барит через гидроворонку до KMW → второй цикл → стравить → открыть: перелива нет, фонтана не было.
     */
    @GameTest(template = LARGE, timeoutTicks = 9000)
    public static void drillIntoGasTakeKickAndKillIt(GameTestHelper h) {
        buildRig(h);
        FakePlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "pc_driller"));
        BlockPos abs = h.absolutePos(ROTARY);
        Trap gas = new Trap(abs.getX(), abs.getZ(), 80, 800, 60, 860, 15, 0.2, 50, FormationFluid.GAS, 11_800);
        double[] sidpp = new double[1];
        h.startSequence()
                .thenWaitUntil(() -> {
                    DrillersConsoleBlockEntity c = h.getBlockEntity(CONSOLE);
                    h.assertTrue(c.rigOk(), "rig assembled, missing " + c.missing());
                    h.assertTrue(c.well() != null, "well spudded");
                })
                .thenExecute(() -> {
                    DrillersConsoleBlockEntity c = h.getBlockEntity(CONSOLE);
                    c.spud(new Prospect(gas, 800), 797);
                    c.setDrilling(120e3, 100);
                })
                .thenWaitUntil(() -> {
                    Well w = ((DrillersConsoleBlockEntity) h.getBlockEntity(CONSOLE)).well();
                    h.assertTrue(w.holeDepthM() > 797.5, "making hole: " + w.holeDepthM() + " m, rop " + w.ropMs() * 3600 + " m/h, q "
                            + w.pumpRateM3s() + ", wob " + w.wobN());
                    h.assertTrue(w.kickAlarm(), "kick: depth " + w.holeDepthM() + ", gain " + w.pitGainM3() + ", influx " + w.totalInfluxM3());
                })
                .thenExecute(() -> {
                    DrillersConsoleBlockEntity c = h.getBlockEntity(CONSOLE);
                    h.assertFalse(c.well().blowout(), "caught before the blowout");
                    c.toggleBop(null);
                })
                .thenWaitUntil(() -> h.assertTrue(((DrillersConsoleBlockEntity) h.getBlockEntity(CONSOLE)).well().bopClosed(),
                        "annular closed by the accumulator within its 30 s stroke"))
                .thenIdle(60)
                .thenExecute(() -> {
                    DrillersConsoleBlockEntity c = h.getBlockEntity(CONSOLE);
                    Well w = c.well();
                    h.assertTrue(h.getBlockState(BOP).getValue(PcProperties.SHUT), "preventer shows shut");
                    h.assertTrue(w.influxM3s() < 1e-5, "influx stopped: " + w.influxM3s());
                    double mid = 800 + w.exposedPayM() / 2;
                    double expected = gas.pressureAt(mid) - 1100 * Well.G * mid;
                    sidpp[0] = w.drillPipePressurePa();
                    h.assertTrue(Math.abs(sidpp[0] - expected) < 0.08 * expected, "SIDPP " + sidpp[0] + " vs " + expected);
                    h.assertTrue(w.casingPressurePa() > sidpp[0], "SICP above SIDPP with gas in the annulus");
                    h.assertTrue(c.advanceKill(null), "kill sheet and first circulation");
                })
                .thenWaitUntil(() -> {
                    DrillersConsoleBlockEntity c = h.getBlockEntity(CONSOLE);
                    h.assertTrue(c.killProcedure().stage() == DrillersMethod.Stage.WEIGHT_UP,
                            "first circulation: strokes " + c.killProcedure().strokes() + ", influx " + c.well().hasInflux());
                })
                .thenExecute(() -> {
                    DrillersConsoleBlockEntity c = h.getBlockEntity(CONSOLE);
                    Well w = c.well();
                    h.assertFalse(w.blowout() || w.shoeFractured(), "no blowout and no losses in the first circulation");
                    KillSheet k = c.killProcedure().sheet();
                    int sacks = (int) Math.ceil(KillSheet.bariteKg(w.pitVolumeM3(), w.pitDensityKgM3(), k.killMudKgM3() + 3)
                            / MudHopperBlockEntity.SACK_KG);
                    MudHopperBlockEntity hopper = h.getBlockEntity(HOPPER);
                    hopper.useItem(p, InteractionHand.MAIN_HAND, new ItemStack(PcItems.BARITE_POWDER.get(), sacks));
                    h.assertTrue(w.pitDensityKgM3() >= k.killMudKgM3(), "pits weighted to " + w.pitDensityKgM3() + " >= " + k.killMudKgM3());
                    h.assertTrue(c.advanceKill(null), "second circulation");
                })
                .thenWaitUntil(() -> {
                    DrillersConsoleBlockEntity c = h.getBlockEntity(CONSOLE);
                    Well w = c.well();
                    h.assertTrue(c.killProcedure().stage() == DrillersMethod.Stage.DONE, "second circulation: strokes "
                            + c.killProcedure().strokes() + ", returns " + w.returnsDensityKgM3());
                    h.assertTrue(w.casingPressurePa() < DrillersMethod.TRAPPED_PA && w.drillPipePressurePa() < 0.05e6,
                            "bled off: SIDPP " + w.drillPipePressurePa() + ", SICP " + w.casingPressurePa());
                })
                .thenExecute(() -> ((DrillersConsoleBlockEntity) h.getBlockEntity(CONSOLE)).toggleBop(null))
                .thenIdle(100)
                .thenExecute(() -> {
                    Well w = ((DrillersConsoleBlockEntity) h.getBlockEntity(CONSOLE)).well();
                    h.assertFalse(w.bopClosed(), "preventer open");
                    h.assertTrue(w.dead(), "flow check: no influx, bottomhole " + w.bottomholePressurePa() + " vs pore " + w.porePressurePa());
                    h.assertFalse(w.blowout(), "never blew out");
                })
                .thenSucceed();
    }

    /**
     * 09 §9.5.10: в истощённый пласт (5 кПа/м) на растворе 1,10 — репрессия ≈ 4,6 МПа. Ротор встал — колонна прилипает
     * (стоянка ускорена до 30 ч): прихваченную ротор не вращает, расхаживание не срывает. Наполнитель через воронку
     * попадает в ёмкости.
     */
    @GameTest(template = LARGE, timeoutTicks = 8000)
    public static void stationaryStringSticksAcrossADepletedPay(GameTestHelper h) {
        buildRig(h);
        FakePlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "pc_driller"));
        BlockPos abs = h.absolutePos(ROTARY);
        Trap depleted = new Trap(abs.getX(), abs.getZ(), 80, 800, 60, 860, 15, 0.2, 200, FormationFluid.OIL, 5_000);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(((DrillersConsoleBlockEntity) h.getBlockEntity(CONSOLE)).rigOk(), "rig assembled"))
                .thenExecute(() -> {
                    DrillersConsoleBlockEntity c = h.getBlockEntity(CONSOLE);
                    c.spud(new Prospect(depleted, 800), 799);
                    c.setDrilling(120e3, 100);
                })
                .thenWaitUntil(() -> {
                    Well w = ((DrillersConsoleBlockEntity) h.getBlockEntity(CONSOLE)).well();
                    h.assertTrue(w.holeDepthM() > 808, "drilling into the pay: " + w.holeDepthM());
                })
                .thenExecute(() -> {
                    DrillersConsoleBlockEntity c = h.getBlockEntity(CONSOLE);
                    h.assertTrue(c.well().overbalancePa() > 2e6, "overbalance " + c.well().overbalancePa());
                    ((CreativeMotorBlockEntity) h.getBlockEntity(ROTARY_MOTOR)).generatedSpeed.setValue(0);
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    DrillersConsoleBlockEntity c = h.getBlockEntity(CONSOLE);
                    h.assertFalse(c.sticking().stuck(), "not stuck after a few minutes");
                    h.assertTrue(c.sticking().contactSeconds() > 0, "standing time counts");
                    c.sticking().restore(30 * 3600, false);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    DrillersConsoleBlockEntity c = h.getBlockEntity(CONSOLE);
                    h.assertTrue(c.sticking().stuck(), "stuck after 30 h: force " + c.sticking().stickingForceN());
                    int[] out = new int[DrillingPanels.C_CHANNELS];
                    c.readPanel(out);
                    h.assertTrue((out[DrillingPanels.C_FLAGS] & 1 << DrillingPanels.F_STUCK) != 0, "stuck lamp");
                    h.assertFalse(c.workPipe(null), "working the pipe does not free it");
                    ((CreativeMotorBlockEntity) h.getBlockEntity(ROTARY_MOTOR)).generatedSpeed.setValue(64);
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    DrillersConsoleBlockEntity c = h.getBlockEntity(CONSOLE);
                    h.assertTrue(c.well().ropMs() == 0 && c.well().rotaryTorqueNm() == 0, "the rotary cannot turn a stuck string");
                    MudHopperBlockEntity hopper = h.getBlockEntity(HOPPER);
                    hopper.useItem(p, InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT, 12));
                    h.assertTrue(c.well().pitLcmKgM3() > 10, "LCM in the pits: " + c.well().pitLcmKgM3());
                })
                .thenSucceed();
    }

    /**
     * 09 §9.5.7: стек из кольцевого, трубных, глухих и срезающих плашек на аккумуляторе. Трубные закрываются рычагом
     * пульта аккумулятора за ход 30 с и закрывают скважину, глухие на трубе не идут, давление в баллонах падает.
     */
    @GameTest(template = LARGE, timeoutTicks = 1200)
    public static void ramPreventersCloseFromTheAccumulator(GameTestHelper h) {
        buildRig(h);
        var north = BlockStateProperties.HORIZONTAL_FACING;
        h.setBlock(new BlockPos(8, 2, 7), PcBlocks.BOP_RAM.get().defaultBlockState().setValue(north, Direction.NORTH));
        h.setBlock(new BlockPos(8, 2, 8), PcBlocks.BOP_RAM.get().defaultBlockState().setValue(north, Direction.NORTH)
                .setValue(PcProperties.RAM_KIND, PcProperties.RamKind.BLIND));
        h.setBlock(new BlockPos(8, 2, 9), PcBlocks.BOP_RAM.get().defaultBlockState().setValue(north, Direction.NORTH)
                .setValue(PcProperties.RAM_KIND, PcProperties.RamKind.SHEAR));
        FakePlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "pc_driller"));
        double[] before = new double[1];
        h.startSequence()
                .thenWaitUntil(() -> {
                    DrillersConsoleBlockEntity c = h.getBlockEntity(CONSOLE);
                    h.assertTrue(c.rigOk() && c.well() != null, "rig assembled, missing " + c.missing());
                    h.assertTrue(c.bopStack() != null && c.bopStack().size() == 4, "stack of four");
                })
                .thenExecute(() -> {
                    DrillersConsoleBlockEntity c = h.getBlockEntity(CONSOLE);
                    BopAccumulatorBlockEntity acc = h.getBlockEntity(ACCUMULATOR);
                    before[0] = acc.accumulator().pressurePa();
                    h.assertTrue(acc.accumulator().meetsApi53(c.bopStack().requiredVolumeM3()), "eight bottles meet API 53");
                    acc.onPanelButton(p, DrillingPanels.AB_BLIND);
                    h.assertFalse(c.bopStack().get(2).closed(), "blind rams do not close on pipe");
                    h.assertTrue(c.bopStack().get(2).fault() == BopStack.Fault.RAM_MISMATCH, "RAM_MISMATCH");
                    acc.onPanelButton(p, DrillingPanels.AB_PIPE);
                    h.assertTrue(c.bopStack().get(1).closed(), "pipe rams stroking");
                })
                .thenWaitUntil(() -> {
                    DrillersConsoleBlockEntity c = h.getBlockEntity(CONSOLE);
                    h.assertTrue(c.well().bopClosed(), "well shut on the pipe rams");
                    h.assertTrue(h.getBlockState(new BlockPos(8, 2, 7)).getValue(PcProperties.SHUT), "ram block shows shut");
                })
                .thenExecute(() -> {
                    BopAccumulatorBlockEntity acc = h.getBlockEntity(ACCUMULATOR);
                    h.assertTrue(acc.accumulator().pressurePa() < before[0] - 1e6, "closing volume drawn: " + acc.accumulator().pressurePa());
                    int[] out = new int[DrillingPanels.A_CHANNELS];
                    acc.readPanel(out);
                    h.assertTrue((out[DrillingPanels.A_FLAGS] & 1 << DrillingPanels.AF_SEALED) != 0, "panel shows the well shut");
                    DrillersConsoleBlockEntity c = h.getBlockEntity(CONSOLE);
                    // срезающие: по положению замка либо режут колонну, либо упираются в замок
                    BopStack.Fault f = c.closeComponent(BopStack.Component.SHEAR_RAM, null);
                    h.assertTrue(f == BopStack.Fault.TOOL_JOINT_IN_RAM ? !c.bopStack().pipeSheared() : c.bopStack().pipeSheared(),
                            "shear rams: " + f + ", sheared " + c.bopStack().pipeSheared());
                })
                .thenSucceed();
    }

    /** Сейсмостанция без расстановки не стреляет, с четырьмя сейсмоприёмниками — пишет и затем гаснет. */
    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void seismicRecorderNeedsASpread(GameTestHelper h) {
        BlockPos rec = new BlockPos(5, 2, 5);
        h.setBlock(rec, PcBlocks.SEISMIC_RECORDER.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        FakePlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "pc_seismic"));
        h.startSequence()
                .thenExecute(() -> {
                    SeismicRecorderBlockEntity r = h.getBlockEntity(rec);
                    ItemStack charge = new ItemStack(PcItems.SEISMIC_CHARGE.get(), 2);
                    r.useItem(p, InteractionHand.MAIN_HAND, charge);
                    h.assertFalse(r.recording(), "no geophones, no shot");
                    h.assertTrue(charge.getCount() == 2, "charge kept");
                    for (BlockPos g : new BlockPos[] {new BlockPos(1, 2, 1), new BlockPos(10, 2, 1), new BlockPos(1, 2, 10), new BlockPos(10, 2, 10)})
                        h.setBlock(g, PcBlocks.GEOPHONE.get());
                    r.useItem(p, InteractionHand.MAIN_HAND, charge);
                    h.assertTrue(r.recording(), "shot fired with four geophones");
                    h.assertTrue(charge.getCount() == 1, "one charge spent");
                    h.assertTrue(h.getBlockState(rec).getValue(BlockStateProperties.LIT), "recording lamp on");
                })
                .thenIdle(SeismicRecorderBlockEntity.RECORD_TICKS + 5)
                .thenExecute(() -> {
                    h.assertFalse(((SeismicRecorderBlockEntity) h.getBlockEntity(rec)).recording(), "recording finished");
                    h.assertFalse(h.getBlockState(rec).getValue(BlockStateProperties.LIT), "lamp off");
                })
                .thenSucceed();
    }
}

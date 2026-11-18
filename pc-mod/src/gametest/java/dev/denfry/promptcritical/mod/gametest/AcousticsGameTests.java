package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.acoustics.MachineAcoustics;
import dev.denfry.promptcritical.mod.acoustics.NoiseService;
import dev.denfry.promptcritical.mod.acoustics.VibrationTransmitterBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.acoustics.BarrierMaterial;
import dev.denfry.promptcritical.sim.acoustics.NoiseExposure;
import dev.denfry.promptcritical.sim.acoustics.SoundLevel;
import dev.denfry.promptcritical.sim.acoustics.VibrationSpectrum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Вибродиагностика и шум в мире: вентилятор Create от творческого двигателя даёт спектр с лопаточной частотой на
 * скорости машины за редуктором, износ подшипника поднимает дефектные пики, вибродатчик выдаёт сигнал, замена
 * подшипника идёт только на стоящем валу и обнуляет износ; шум у слушателя — сумма машин с расхождением, бетонная стена
 * снимает около 25 дБ; доза шума копится по правилу 3 дБ, а наушники её снимают.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AcousticsGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos MOTOR = new BlockPos(2, 2, 4), FAN = new BlockPos(3, 2, 4);
    private static final int CREATE_RPM = 64;

    private AcousticsGameTests() {
    }

    private static void fanRig(GameTestHelper h) {
        h.setBlock(MOTOR, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        h.setBlock(FAN, AllBlocks.ENCASED_FAN.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(MOTOR)).generatedSpeed.setValue(CREATE_RPM));
    }

    private static void setSpeed(GameTestHelper h, int rpm) {
        ((CreativeMotorBlockEntity) h.getBlockEntity(MOTOR)).generatedSpeed.setValue(rpm);
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void fanSpectrumAndBearingWear(GameTestHelper h) {
        fanRig(h);
        BlockPos fan = h.absolutePos(FAN);
        double[] fresh = new double[1];
        h.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    MachineAcoustics.Probe p = MachineAcoustics.probe(h.getLevel(), fan);
                    h.assertTrue(p != null && p.machine(), "the fan is a machine: " + p);
                    double rpm = CREATE_RPM * MachineAcoustics.GEAR_RATIO;
                    h.assertTrue(Math.abs(p.rpm() - rpm) < 1e-6, "machine speed behind the gearbox: " + p.rpm());
                    h.assertTrue(p.powerW() > 0, "shaft power: " + p.powerW());
                    VibrationSpectrum s = MachineAcoustics.spectrum(h.getLevel(), fan);
                    VibrationSpectrum.Line bpf = s.line(VibrationSpectrum.Band.BPF);
                    h.assertTrue(bpf != null && Math.abs(bpf.hz() - MachineAcoustics.FAN_BLADES * rpm / 60) < 1e-6,
                            "blade pass line of an 8-blade fan: " + bpf);
                    h.assertTrue(s.v(VibrationSpectrum.Band.BPFO) == 0, "a new bearing shows no defect line");
                    h.assertTrue(s.overallRms() > 0, "overall level of a running machine");
                    fresh[0] = s.overallRms();
                    // износ идёт только на ходу и медленно: 1 с реального времени — доли миллионной ресурса
                    double d0 = MachineAcoustics.bearingDamage(h.getLevel(), fan);
                    MachineAcoustics.stepWear(h.getLevel(), fan);
                    h.assertTrue(d0 >= 0 && d0 < 1e-3, "fresh bearing damage: " + d0);
                    MachineAcoustics.setBearingDamage(h.getLevel(), fan, 0.999);
                    VibrationSpectrum worn = MachineAcoustics.spectrum(h.getLevel(), fan);
                    h.assertTrue(worn.v(VibrationSpectrum.Band.BPFO) > 1e-3, "outer race line at stage 4: " + worn.v(VibrationSpectrum.Band.BPFO));
                    h.assertTrue(worn.overallRms() > 3 * fresh[0], "overall: " + worn.overallRms() + " vs " + fresh[0]);
                    h.assertTrue(worn.bearingTempK() > s.bearingTempK() + 35, "a hot bearing housing");
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    double before = MachineAcoustics.bearingDamage(h.getLevel(), fan);
                    MachineAcoustics.stepWear(h.getLevel(), fan);
                    double after = MachineAcoustics.bearingDamage(h.getLevel(), fan);
                    h.assertTrue(after > before && after - before < 1e-3, "two seconds of wear: " + (after - before));
                    MachineAcoustics.setBearingDamage(h.getLevel(), fan, 0.5);
                    h.assertFalse(MachineAcoustics.canService(h.getLevel(), fan, MachineAcoustics.Service.BEARING),
                            "no bearing change on a running shaft");
                    h.assertFalse(MachineAcoustics.service(h.getLevel(), fan, MachineAcoustics.Service.BEARING), "refused");
                    h.assertTrue(MachineAcoustics.service(h.getLevel(), fan, MachineAcoustics.Service.BALANCE), "balancing on the run");
                    setSpeed(h, 0);
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    h.assertTrue(MachineAcoustics.service(h.getLevel(), fan, MachineAcoustics.Service.BEARING), "bearing change when stopped");
                    h.assertTrue(MachineAcoustics.bearingDamage(h.getLevel(), fan) == 0, "a new bearing");
                    h.assertTrue(MachineAcoustics.service(h.getLevel(), fan, MachineAcoustics.Service.ALIGN), "alignment when stopped");
                    h.assertTrue(Math.abs(MachineAcoustics.misalignmentMm(h.getLevel(), fan) - 0.05) < 1e-9, "aligned to 0.05 mm");
                    h.assertTrue(MachineAcoustics.spectrum(h.getLevel(), fan).overallRms() == 0, "a stopped shaft is still");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void transmitterSignalsTheFanLevel(GameTestHelper h) {
        fanRig(h);
        BlockPos sensor = FAN.north();
        h.setBlock(sensor, PcBlocks.INSTRUMENT_VIBRATION.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        h.startSequence()
                .thenIdle(50)
                .thenExecute(() -> {
                    VibrationTransmitterBlockEntity t = (VibrationTransmitterBlockEntity) h.getBlockEntity(sensor);
                    VibrationSpectrum s = MachineAcoustics.spectrum(h.getLevel(), h.absolutePos(FAN));
                    h.assertTrue(t.signal() == VibrationTransmitterBlockEntity.signalFor(s) && t.signal() >= 1,
                            "signal " + t.signal() + " for " + s.overallRms());
                    MachineAcoustics.setBearingDamage(h.getLevel(), h.absolutePos(FAN), 0.999);
                })
                .thenIdle(30)
                .thenExecute(() -> {
                    VibrationTransmitterBlockEntity t = (VibrationTransmitterBlockEntity) h.getBlockEntity(sensor);
                    VibrationSpectrum s = MachineAcoustics.spectrum(h.getLevel(), h.absolutePos(FAN));
                    h.assertTrue(t.signal() == VibrationTransmitterBlockEntity.signalFor(s) && t.signal() > 1,
                            "worn bearing raises the signal: " + t.signal());
                    int power = h.getLevel().getBestNeighborSignal(h.absolutePos(sensor.north()));
                    h.assertTrue(power == t.signal(), "redstone next to the sensor: " + power);
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void noiseAddsUpAndConcreteBlocksIt(GameTestHelper h) {
        fanRig(h);
        Vec3 ear = Vec3.atCenterOf(h.absolutePos(new BlockPos(3, 2, 8)));
        double[] open = new double[1];
        h.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    var sources = NoiseService.sources(h.getLevel(), ear);
                    h.assertTrue(sources.size() == 2, "motor and fan are sources: " + sources);
                    double[] each = new double[sources.size()];
                    for (int i = 0; i < each.length; i++)
                        each[i] = SoundLevel.atDistance(sources.get(i).lwDb(), Vec3.atCenterOf(sources.get(i).pos()).distanceTo(ear), 0);
                    open[0] = NoiseService.levelAt(h.getLevel(), ear);
                    h.assertTrue(Math.abs(open[0] - SoundLevel.sum(each)) < 1e-6, "open air: " + open[0] + " vs " + SoundLevel.sum(each));
                    // листва почти прозрачна для звука, бетон — нет
                    h.assertTrue(NoiseService.material(Blocks.OAK_LEAVES.defaultBlockState()) == BarrierMaterial.PLANT, "leaves");
                    h.assertTrue(NoiseService.material(Blocks.WHITE_CONCRETE.defaultBlockState()) == BarrierMaterial.CONCRETE, "concrete");
                    for (int x = 0; x <= 6; x++)
                        for (int y = 1; y <= 4; y++) h.setBlock(new BlockPos(x, y, 6), Blocks.WHITE_CONCRETE);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    double walled = NoiseService.levelAt(h.getLevel(), ear);
                    double drop = open[0] - walled;
                    h.assertTrue(drop > 24 && drop < 32, "a concrete wall takes off about 25 dB: " + drop);
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 40)
    public static void noiseDoseAndEarMuffs(GameTestHelper h) {
        FakePlayer p = FakePlayerFactory.getMinecraft(h.getLevel());
        p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        p.removeAllEffects();
        NoiseService.setDose(p, 0);
        // 15 минут при 100 дБ(А) — дневная доза
        NoiseService.expose(p, 100, 900);
        h.assertTrue(Math.abs(NoiseService.dose(p) - 1) < 1e-9, "dose: " + NoiseService.dose(p));
        NoiseService.expose(p, 100, 60);
        h.assertTrue(NoiseService.dose(p) > 1 && p.hasEffect(MobEffects.DIG_SLOWDOWN), "over the dose: fatigue");
        // в наушниках 100 дБ(А) у уха — 75: не копится
        NoiseService.setDose(p, 0);
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(PcItems.HEARING_PROTECTION.get()));
        h.assertTrue(NoiseService.protectedEars(p), "ear muffs on the head");
        NoiseService.expose(p, 100, 900);
        h.assertTrue(NoiseService.dose(p) == 0, "no dose under ear muffs: " + NoiseService.dose(p));
        h.assertTrue(NoiseExposure.atEar(100, true) == 100 - NoiseExposure.PROTECTOR_DB, "protector rating");
        p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        p.removeAllEffects();
        h.succeed();
    }
}

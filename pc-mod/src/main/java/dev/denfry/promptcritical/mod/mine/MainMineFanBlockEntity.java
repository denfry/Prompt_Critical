package dev.denfry.promptcritical.mod.mine;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.boiler.BoilerGeometry;
import dev.denfry.promptcritical.mod.boiler.ShaftConsumer;
import dev.denfry.promptcritical.mod.ops.OpsActions;
import dev.denfry.promptcritical.mod.ops.OpsMachine;
import dev.denfry.promptcritical.mod.ops.OpsText;
import dev.denfry.promptcritical.mod.ops.OpsWorld;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import dev.denfry.promptcritical.sim.ops.Baseline;
import dev.denfry.promptcritical.sim.ops.CheckInput;
import dev.denfry.promptcritical.sim.ops.OpsSubsystem;
import dev.denfry.promptcritical.sim.ops.UnitKind;
import dev.denfry.promptcritical.sim.mining.FanSpec;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Вентилятор главного проветривания (08 §8.2.7) на валу Create: редуктор переводит {@link #NOMINAL_RPM} вала в n₀
 * характеристики {@link MineScanner#MAIN_FAN}; подобие Q ∝ n, h ∝ n² считает решатель вентиляции. Мощность
 * N = hQ/η_f из последнего решения сети {@link MineWorld} возвращает сюда — её и видит сеть Create. Заплавленный
 * подшипник ({@link OpsMachine}) клинит вал: огромный момент перегружает сеть Create.
 */
public final class MainMineFanBlockEntity extends DeviceBlockEntity implements ShaftConsumer, PanelHost, OpsMachine,
        dev.denfry.promptcritical.mod.client.whole.WholeModelHost {
    /** Номинальная скорость вала, об/мин [BALANCE: как у машин литейки]. */
    public static final double NOMINAL_RPM = 64;
    /** Передаточное число редуктора: n₀ крыльчатки / номинал вала. */
    public static final double GEAR = MineScanner.MAIN_FAN.ratedRpm() / NOMINAL_RPM;

    /** Заклинивший вал: во столько раз больше номинальной мощности — перегрузка сети Create. */
    public static final double SEIZED_FACTOR = 100;

    /** Guibal: ось вала (y), плоскость кривошипа (z) и цилиндр машины (x от − до +), px модели. */
    private static final double AXLE_Y = 40, CRANK_Z = 50, CYL_X0 = -74, CYL_X1 = -46;
    /** Guibal: середина верха эвазе, px модели. */
    private static final double EVASE_X = 96, EVASE_Y = 266, EVASE_Z = 8;

    private final ShaftInput shaft = new ShaftInput();
    /** Клиент, Guibal: угол кривошипа, рад, и его скорость, рад/тик. */
    private float clientCrank, omega;
    private double powerW, pressurePa, flowM3s;
    private boolean seized;

    public MainMineFanBlockEntity(BlockPos pos, BlockState state) {
        super(MineBlockEntities.MAIN_MINE_FAN.get(), pos, state);
    }

    @Override
    @org.jetbrains.annotations.Nullable
    public String wholeModelId() {
        return dev.denfry.promptcritical.mod.client.whole.WholeModelHost.idOf(getBlockState());
    }

    @Override
    public net.minecraft.core.Direction wholeModelFacing() {
        return facing();
    }

    @Override
    public BlockPos wholeModelLightPos() {
        // глаз снаружи кожуха, перед северной стеной
        return MainMineFanBlock.eyePos(getBlockState(), worldPosition).relative(facing());
    }

    /** Вентилятор Guibal реального масштаба. */
    boolean real() {
        return wholeModelId() != null;
    }

    private net.minecraft.core.Direction facing() {
        return MainMineFanBlock.class.cast(getBlockState().getBlock()).facing(getBlockState());
    }

    /** Угол кривошипа Guibal для кадра, рад. */
    public float renderCrank(float partialTick) {
        return clientCrank + omega * partialTick;
    }

    /**
     * Клиент, Guibal: машина вращает ось прямо на кривошипе, со скоростью вала Create во входной клетке. Клуб пара у
     * продувочных кранов цилиндра на каждой мёртвой точке, у верха эвазе — струя пыльного воздуха.
     */
    void clientTick() {
        float rpm = level != null && level.getBlockEntity(MainMineFanBlock.portPos(getBlockState(), worldPosition))
                instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity k ? Math.abs(k.getSpeed()) : 0;
        omega = (float) (rpm * 2 * Math.PI / 60 / 20);
        float before = clientCrank;
        clientCrank = (float) ((clientCrank + omega) % (2 * Math.PI));
        if (omega <= 0) return;
        dev.denfry.promptcritical.mod.fx.MachineFx fx = dev.denfry.promptcritical.mod.fx.MachineFx.of(this);
        if (fx == null) return;
        if ((int) (before / Math.PI) != (int) (clientCrank / Math.PI)) {
            // выхлоп у того конца цилиндра, куда поршень пришёл
            double x = clientCrank < Math.PI ? CYL_X0 + 4 : CYL_X1 - 4;
            Vec3 at = fx.at(x, AXLE_Y - 8, CRANK_Z);
            fx.puff(at, new Vec3(0, 0.08, 0), 2);
            fx.sound(at, dev.denfry.promptcritical.mod.sound.PcSfx.STEAM_ENGINE_CHUFF.get(), 0.7f, 0.8f + Math.min(0.3f, rpm / 200f));
        }
        if (fx.chance(3 * Math.min(1, rpm / NOMINAL_RPM))) fx.dust(fx.at(EVASE_X, EVASE_Y, EVASE_Z), 1.5, 1);
    }

    /** Скорость крыльчатки, об/мин; 0 — вала нет или сеть перегружена. */
    public double fanRpm(long tick) {
        return shaft.rpm(tick) * GEAR;
    }

    /** Мощность на валу из решения сети вентиляции, Вт. */
    public void setPowerW(double w) {
        powerW = Math.max(0, w);
    }

    /** Давление (депрессия) и подача вентилятора из того же решения сети, Па и м³/с. */
    public void setAir(double pa, double m3s) {
        pressurePa = Double.isFinite(pa) ? pa : 0;
        flowM3s = Double.isFinite(m3s) ? m3s : 0;
    }

    @Override
    public void sampleShaft(double rpm, boolean healthy, long tick) {
        shaft.sample(rpm, healthy, tick);
        OpsSubsystem ops = OpsWorld.ops(level);
        OpsSubsystem.Unit u = OpsWorld.ensure(level, simId(), UnitKind.MAIN_MINE_FAN, 0, false);
        if (ops == null || u == null) return;
        boolean running = shaft.rpm(tick) > 0;
        u.setRunning(running, false, Double.NaN);
        seized = u.seized();
        ops.observeTrial(simId(), 0.05, running, powerW / ratedShaftW(), false, opsCurrent());
    }

    @Override
    public double shaftPowerW() {
        return seized ? SEIZED_FACTOR * ratedShaftW() : powerW;
    }

    /** Наибольшая мощность на характеристике h₀(1 − (Q/Q₀)²): при Q = Q₀/√3, N = 2h₀Q₀/(3√3·η). */
    @Override
    public double ratedShaftW() {
        FanSpec f = MineScanner.MAIN_FAN;
        return 2 * f.h0Pa() * f.q0M3s() / (3 * Math.sqrt(3) * f.efficiency());
    }

    @Override
    public double nominalShaftRpm() {
        return NOMINAL_RPM;
    }

    @Override
    public PanelLayout panelLayout() {
        return MinePanels.FAN;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        SimLevelBinding b = level instanceof ServerLevel s ? SimBindings.get(s) : null;
        if (b == null) return;
        long tick = b.world().clock().tick();
        double rpm = shaft.rpm(tick);
        out[MinePanels.SHAFT_RPM10] = (int) Math.round(rpm * 10);
        out[MinePanels.FAN_RPM] = (int) Math.round(fanRpm(tick));
        out[MinePanels.FAN_KW10] = (int) Math.round(powerW / 100);
        out[MinePanels.FAN_PA] = (int) Math.round(Math.max(0, pressurePa));
        out[MinePanels.FAN_Q10] = (int) Math.round(Math.max(0, flowM3s) * 10);
        out[MinePanels.FLAGS] = rpm > 0 ? 1 << MinePanels.F_RUNNING : 0;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == MinePanels.BUTTON_BUMP) bump(player, this, shaft.signedRpm(currentTick()));
    }

    private long currentTick() {
        SimLevelBinding b = level instanceof ServerLevel s ? SimBindings.get(s) : null;
        return b == null ? 0 : b.world().clock().tick();
    }

    /** Толчок: проверка вращения по знаку скорости вала Create (13 §13.6.6). */
    public static void bump(net.minecraft.world.entity.player.Player player, OpsMachine m, double signedRpm) {
        if (signedRpm == 0) {
            player.displayClientMessage(OpsText.msg("rotation_need"), true);
            return;
        }
        boolean reversed = signedRpm < 0;
        player.displayClientMessage(OpsText.msg("rotation", OpsText.msg(reversed ? "rotation_reverse" : "rotation_forward")), false);
        OpsActions.perform(player, m, new CheckInput.RotationCheck(reversed));
    }

    // --- эксплуатация ---

    @Override
    public UnitKind opsKind() {
        return UnitKind.MAIN_MINE_FAN;
    }

    @Override
    public boolean opsStopped() {
        return shaft.rpm(currentTick()) <= 0;
    }

    @Override
    public Vec3 opsBearingPos() {
        if (real()) return BoilerGeometry.world(worldPosition, facing(), 8, AXLE_Y, CRANK_Z - 6); // подшипник у кривошипа
        return BoilerGeometry.world(worldPosition, facing(), 8, 8, 25); // редуктор
    }

    @Override
    public Baseline opsCurrent() {
        long tick = currentTick();
        return new Baseline(Double.NaN, Double.NaN, Double.NaN, Double.NaN, fanRpm(tick) * 2 * Math.PI / 60, Double.NaN);
    }
}

package dev.denfry.promptcritical.mod.mine;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.boiler.ShaftConsumer;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import dev.denfry.promptcritical.sim.mining.FanSpec;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Вентилятор главного проветривания (08 §8.2.7) на валу Create: редуктор переводит {@link #NOMINAL_RPM} вала в n₀
 * характеристики {@link MineScanner#MAIN_FAN}; подобие Q ∝ n, h ∝ n² считает решатель вентиляции. Мощность
 * N = hQ/η_f из последнего решения сети {@link MineWorld} возвращает сюда — её и видит сеть Create.
 */
public final class MainMineFanBlockEntity extends DeviceBlockEntity implements ShaftConsumer, PanelHost {
    /** Номинальная скорость вала, об/мин [BALANCE: как у машин литейки]. */
    public static final double NOMINAL_RPM = 64;
    /** Передаточное число редуктора: n₀ крыльчатки / номинал вала. */
    public static final double GEAR = MineScanner.MAIN_FAN.ratedRpm() / NOMINAL_RPM;

    private final ShaftInput shaft = new ShaftInput();
    private double powerW;

    public MainMineFanBlockEntity(BlockPos pos, BlockState state) {
        super(MineBlockEntities.MAIN_MINE_FAN.get(), pos, state);
    }

    /** Скорость крыльчатки, об/мин; 0 — вала нет или сеть перегружена. */
    public double fanRpm(long tick) {
        return shaft.rpm(tick) * GEAR;
    }

    /** Мощность на валу из решения сети вентиляции, Вт. */
    public void setPowerW(double w) {
        powerW = Math.max(0, w);
    }

    @Override
    public void sampleShaft(double rpm, boolean healthy, long tick) {
        shaft.sample(rpm, healthy, tick);
    }

    @Override
    public double shaftPowerW() {
        return powerW;
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
        out[MinePanels.FLAGS] = rpm > 0 ? 1 << MinePanels.F_RUNNING : 0;
    }
}

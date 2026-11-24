package dev.denfry.promptcritical.mod.acoustics;

import dev.denfry.promptcritical.sim.acoustics.VibrationSpectrum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Вибродатчик: общий уровень машины под ним раз в секунду и сигнал редстоуна 0–15 ({@link VibrationTransmitterBlock}). */
public final class VibrationTransmitterBlockEntity extends BlockEntity {
    /** Преобразователь 4–20 мА, Вт: без питания сигнал ноль. Малая нагрузка 0,4 кВ: розетка, щит или ИБП рядом. */
    public static final double POWER_W = 5;
    private int signal;
    private double overall;

    public VibrationTransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(Acoustics.VIBRATION_TRANSMITTER.get(), pos, state);
    }

    /** Сигнал по общему уровню: 15·v/v_CD, с округлением; ненулевой при любом вращении. */
    public static int signalFor(VibrationSpectrum s) {
        double v = s.overallRms();
        if (!(v > 0)) return 0;
        return (int) Math.clamp(Math.round(15 * v / s.machineClass().cd), 1, 15);
    }

    void tick() {
        if (!(level instanceof ServerLevel sl) || (sl.getGameTime() + worldPosition.asLong()) % 20 != 0) return;
        VibrationSpectrum s = dev.denfry.promptcritical.mod.grid.MachinePower.powered(sl, worldPosition, POWER_W)
                ? MachineAcoustics.spectrum(sl, VibrationTransmitterBlock.mountPos(getBlockState(), worldPosition)) : null;
        overall = s == null ? 0 : s.overallRms();
        int next = s == null ? 0 : signalFor(s);
        if (next == signal) return;
        signal = next;
        setChanged();
        sl.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    public int signal() {
        return signal;
    }

    /** Последний замер общего уровня, м/с СКЗ. */
    public double overallRms() {
        return overall;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("signal", signal);
        tag.putDouble("overall", overall);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        signal = tag.getInt("signal");
        overall = tag.getDouble("overall");
    }
}

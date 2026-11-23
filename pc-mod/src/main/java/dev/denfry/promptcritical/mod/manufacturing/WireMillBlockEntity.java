package dev.denfry.promptcritical.mod.manufacturing;

import dev.denfry.promptcritical.mod.foundry.MachineInventory;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.manufacturing.Drawing;
import dev.denfry.promptcritical.sim.manufacturing.Forging;
import dev.denfry.promptcritical.sim.manufacturing.Wire;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Волочильный стан {@code wire_mill} на валу Create (18 §18.6.7, механизированная ступень): тянет пруток проход за
 * проходом через ряд волок {@link DrawPlateBlockEntity#DIES_MM} до заданного диаметра, всегда со смазкой (мыльный
 * ящик). Тяга барабана — η·P/v. Стан сам встаёт, если следующий проход оборвёт проволоку по наклёпу (нужен отжиг) или
 * не хватит тяги; проход занимает время протяжки новой длины со скоростью {@link #DRAW_SPEED_MS}.
 */
public final class WireMillBlockEntity extends PoweredSmithyDevice {
    /** Мощность при номинале: 15 кВт при 64 об/мин [BALANCE]. */
    public static final double RATED_W = 15_000, NOMINAL_RPM = 64;
    /** Скорость волочения на барабане, м/с [сверить: однократные станы 0,5–2 м/с]. */
    public static final double DRAW_SPEED_MS = 1.0;
    public static final int STOP_NONE = 0, STOP_DONE = 1, STOP_ANNEAL = 2, STOP_FORCE = 3;
    private static final MachineInventory.Role[] ROLES = {MachineInventory.Role.INPUT};

    private int targetDie = 13;
    private boolean on;
    private int stop;
    private double busyS;
    private int passes;
    private int lastForceN;

    public WireMillBlockEntity(BlockPos pos, BlockState state) {
        super(SmithyBlockEntities.WIRE_MILL.get(), pos, state, ROLES, (slot, s) -> Smithy.drawable(s));
    }

    @Override
    protected double ratedW() {
        return RATED_W;
    }

    @Override
    protected double nominalRpm() {
        return NOMINAL_RPM;
    }

    public double targetM() {
        return DrawPlateBlockEntity.DIES_MM[targetDie] / 1000;
    }

    public void setTarget(int die, boolean on) {
        targetDie = Math.max(0, Math.min(DrawPlateBlockEntity.DIES_MM.length - 1, die));
        this.on = on;
        if (on) stop = STOP_NONE;
        setChanged();
    }

    public int stop() {
        return stop;
    }

    public int passes() {
        return passes;
    }

    @Nullable
    private Wire wire() {
        ItemStack s = inv.getStackInSlot(0);
        return level == null || s.getCount() != 1 ? null : Smithy.readWire(s, level.getGameTime());
    }

    /** Следующая волока: самая крупная меньше диаметра, но не меньше заданной; -1 — тянуть некуда. */
    private int nextDie(Wire w) {
        for (int i = 0; i <= targetDie; i++)
            if (DrawPlateBlockEntity.DIES_MM[i] / 1000 < w.diameterM() - 1e-9) return i;
        return -1;
    }

    @Override
    protected boolean working() {
        return on && stop == STOP_NONE && wire() != null;
    }

    @Override
    protected void second(double shaftW) {
        if (busyS > 0) {
            busyS = Math.max(0, busyS - 1);
            return;
        }
        Wire w = wire();
        if (!on || shaftW <= 0 || w == null || stop != STOP_NONE) return;
        int die = nextDie(w);
        if (die < 0) {
            halt(STOP_DONE);
            return;
        }
        double dieM = DrawPlateBlockEntity.DIES_MM[die] / 1000;
        if (w.strain() + 2 * Math.log(w.diameterM() / dieM) > w.metal().maxStrain) {
            // проход оборвал бы проволоку: стан встаёт под отжиг
            halt(STOP_ANNEAL);
            return;
        }
        double force = Forging.POWER_DRIVE_EFFICIENCY * shaftW / DRAW_SPEED_MS;
        Drawing.Pass p = Drawing.pass(w, dieM, Drawing.MU_LUBRICATED, force);
        lastForceN = (int) Math.round(p.forceN());
        switch (p.outcome()) {
            case OK -> {
                inv.setStackInSlot(0, Smithy.wireStack(p.wire()));
                passes++;
                busyS = Math.ceil(p.wire().lengthM() / DRAW_SPEED_MS);
                if (level instanceof ServerLevel sl)
                    sl.playSound(null, worldPosition, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.4f, 0.6f);
            }
            case TOO_STRONG -> halt(STOP_FORCE);
            case BROKE -> {
                inv.setStackInSlot(0, Smithy.wireStack(p.wire()));
                halt(STOP_ANNEAL);
            }
            case NO_REDUCTION -> halt(STOP_DONE);
        }
        setChanged();
    }

    private void halt(int why) {
        stop = why;
        on = false;
        setChanged();
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case SmithyPanels.BUTTON_DIE_SMALLER -> setTarget(targetDie + 1, on);
            case SmithyPanels.BUTTON_DIE_LARGER -> setTarget(targetDie - 1, on);
            case SmithyPanels.W_BUTTON_RUN -> setTarget(targetDie, !on);
            default -> { }
        }
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return Smithy.drawable(stack);
    }

    @Override
    public PanelLayout panelLayout() {
        return SmithyPanels.WIRE_MILL;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        double w0 = shaftW();
        out[SmithyPanels.W_RPM10] = rpm10();
        out[SmithyPanels.W_FORCE_KN10] = (int) Math.round(Forging.POWER_DRIVE_EFFICIENCY * w0 / DRAW_SPEED_MS / 100);
        out[SmithyPanels.W_TARGET_UM] = (int) Math.round(DrawPlateBlockEntity.DIES_MM[targetDie] * 1000);
        out[SmithyPanels.W_STOP] = stop;
        out[SmithyPanels.W_PASSES] = passes;
        out[SmithyPanels.W_LAST_N] = lastForceN;
        int f = on ? 1 << SmithyPanels.WF_ON : 0;
        if (w0 > 0) f |= 1 << SmithyPanels.WF_SHAFT;
        Wire w = wire();
        if (w != null) {
            f |= 1 << SmithyPanels.WF_WIRE;
            out[SmithyPanels.W_WIRE_UM] = (int) Math.round(w.diameterM() * 1e6);
            out[SmithyPanels.W_LENGTH_CM] = (int) Math.round(w.lengthM() * 100);
            out[SmithyPanels.W_STRAIN_PCT] = (int) Math.round(100 * w.strain() / w.metal().maxStrain);
            out[SmithyPanels.W_METAL] = w.metal().ordinal();
        }
        out[SmithyPanels.FLAGS] = f;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) return;
        tag.putInt("target", targetDie);
        tag.putBoolean("on", on);
        tag.putInt("stop", stop);
        tag.putDouble("busy", busyS);
        tag.putInt("passes", passes);
        tag.putInt("force", lastForceN);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) return;
        targetDie = tag.contains("target") ? Math.max(0, Math.min(DrawPlateBlockEntity.DIES_MM.length - 1, tag.getInt("target"))) : 13;
        on = tag.getBoolean("on");
        stop = Math.max(0, Math.min(STOP_FORCE, tag.getInt("stop")));
        busyS = Math.max(0, tag.getDouble("busy"));
        passes = Math.max(0, tag.getInt("passes"));
        lastForceN = Math.max(0, tag.getInt("force"));
    }
}

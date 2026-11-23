package dev.denfry.promptcritical.mod.manufacturing;

import dev.denfry.promptcritical.mod.foundry.MachineInventory;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.manufacturing.CoreStack;
import dev.denfry.promptcritical.sim.manufacturing.Lamination;
import dev.denfry.promptcritical.sim.manufacturing.Stator;
import dev.denfry.promptcritical.sim.manufacturing.Wire;
import dev.denfry.promptcritical.sim.manufacturing.WireInsulation;
import dev.denfry.promptcritical.sim.manufacturing.Winding;
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
 * Намоточный станок {@code winding_machine} на валу Create (18 §18.6.8–18.6.9, §18.7): шихтует пакет статора из пачек
 * пластин на длину {@link Winding#STACK_LENGTH_M} и мотает его обмоточным проводом. Без провода выдаёт голый пакет
 * {@code stator_core}; пакет во входе мотается сразу. Провод — один стек одинаковой проволоки в изоляции, его
 * расходуется на 3·w·l_витка ({@link Winding#wireNeededM}).
 */
public final class WindingMachineBlockEntity extends PoweredSmithyDevice {
    /** Мощность при номинале: 3 кВт при 64 об/мин [BALANCE]. */
    public static final double RATED_W = 3_000, NOMINAL_RPM = 64;
    /** Шихтовка и опрессовка пакета, с, и витков в секунду при номинальных оборотах [BALANCE]. */
    public static final double STACK_S = 60, TURNS_PER_S = 2;
    public static final int STOP_NONE = 0, STOP_DONE = 1, STOP_LAMINATIONS = 2, STOP_WIRE = 3, STOP_OUTPUT = 4;
    /** Слоты: 0–3 — пачки пластин (или пакет в 0), 4 — провод, 5 — выход. */
    public static final int LAM_SLOTS = 4, WIRE = 4, OUT = 5;
    private static final MachineInventory.Role[] ROLES = {MachineInventory.Role.INPUT, MachineInventory.Role.INPUT,
        MachineInventory.Role.INPUT, MachineInventory.Role.INPUT, MachineInventory.Role.INPUT, MachineInventory.Role.OUTPUT};

    private boolean on;
    private int stop;
    /** Ход работы: секунды шихтовки, затем витки. */
    private double stackS, turns;
    /** Пакет в станке под обмоткой; null — ещё не собран или не взят. */
    @Nullable
    private CoreStack held;

    public WindingMachineBlockEntity(BlockPos pos, BlockState state) {
        super(SmithyBlockEntities.WINDING_MACHINE.get(), pos, state, ROLES, (slot, s) -> slot < LAM_SLOTS ? isCoreInput(s)
                : slot == WIRE && isMagnetWire(s));
    }

    public static boolean isCoreInput(ItemStack s) {
        return s.has(ManufacturingComponents.LAMINATION.get()) || s.has(ManufacturingComponents.STATOR_CORE.get());
    }

    /** Медная проволока в изоляции. */
    public static boolean isMagnetWire(ItemStack s) {
        WireData w = s.get(ManufacturingComponents.WIRE.get());
        return w != null && w.wireInsulation() != WireInsulation.NONE && w.wire().metal() == Wire.Metal.COPPER;
    }

    @Override
    protected double ratedW() {
        return RATED_W;
    }

    @Override
    protected double nominalRpm() {
        return NOMINAL_RPM;
    }

    public void setOn(boolean on) {
        this.on = on;
        if (on) stop = STOP_NONE;
        setChanged();
    }

    public int stop() {
        return stop;
    }

    /** Пакет, который станок соберёт или уже держит; null — пачек мало. */
    @Nullable
    private CoreStack core() {
        if (held != null) return held;
        CoreStack c = Electrical.core(inv.getStackInSlot(0));
        if (c != null) return c;
        // пачки одного листа и обработки шихтуются вместе; заусенец пакета — средний по пачкам
        ElectricalData.Bundle key = null;
        int bundles = 0;
        double burrSum = 0;
        for (int i = 0; i < LAM_SLOTS; i++) {
            ElectricalData.Bundle b = inv.getStackInSlot(i).get(ManufacturingComponents.LAMINATION.get());
            if (b == null) continue;
            if (key == null) key = b.key();
            else if (!key.equals(b.key())) return null;
            int n = inv.getStackInSlot(i).getCount();
            bundles += n;
            burrSum += n * b.lamination().burrM();
        }
        if (key == null) return null;
        Lamination base = key.lamination();
        Lamination l = new Lamination(base.sheet(), base.shape(), burrSum / bundles, base.edgesRelieved(), base.coatedAfterPunch());
        int n = CoreStack.countFor(l, Winding.STACK_LENGTH_M);
        return bundles * ElectricalData.LAMINATIONS_PER_BUNDLE >= n ? new CoreStack(l, n) : null;
    }

    /** Снять {@code n} пачек со входа. */
    private void takeBundles(int n) {
        for (int i = 0; i < LAM_SLOTS && n > 0; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.has(ManufacturingComponents.LAMINATION.get())) continue;
            int k = Math.min(n, s.getCount());
            s.shrink(k);
            n -= k;
        }
    }

    private static int bundlesFor(CoreStack c) {
        return (c.count() + ElectricalData.LAMINATIONS_PER_BUNDLE - 1) / ElectricalData.LAMINATIONS_PER_BUNDLE;
    }

    /** Проволоки во входе хватает на обмотку пакета {@code c}. */
    private boolean wireEnough(CoreStack c) {
        ItemStack w = inv.getStackInSlot(WIRE);
        WireData d = w.get(ManufacturingComponents.WIRE.get());
        return d != null && isMagnetWire(w) && w.getCount() * d.wire().lengthM() >= Winding.wireNeededM(c);
    }

    @Override
    protected boolean working() {
        return on && stop == STOP_NONE && core() != null;
    }

    @Override
    protected void second(double shaftW) {
        if (!on || stop != STOP_NONE || shaftW <= 0) return;
        CoreStack c = core();
        if (c == null) {
            halt(STOP_LAMINATIONS);
            return;
        }
        if (!inv.getStackInSlot(OUT).isEmpty()) {
            halt(STOP_OUTPUT);
            return;
        }
        double rate = Math.min(1, shaftW / RATED_W);
        if (held == null && inv.getStackInSlot(0).has(ManufacturingComponents.STATOR_CORE.get())) {
            // готовый пакет берётся в станок, только если есть чем мотать
            if (!wireEnough(c)) {
                halt(STOP_WIRE);
                return;
            }
            inv.getStackInSlot(0).shrink(1);
            held = c;
        }
        if (held == null) {
            stackS += rate;
            if (stackS < STACK_S) return;
            stackS = 0;
            takeBundles(bundlesFor(c));
            if (!wireEnough(c)) {
                // провода нет или мало: голый пакет на выход
                inv.setStackInSlot(OUT, Electrical.coreStack(c));
                halt(STOP_WIRE);
                return;
            }
            held = c;
            setChanged();
            return;
        }
        if (!wireEnough(c)) {
            halt(STOP_WIRE);
            return;
        }
        turns += TURNS_PER_S * rate;
        if (turns < 3.0 * Winding.turnsPerPhase(c.lamination().shape())) return;
        turns = 0;
        ItemStack wire = inv.getStackInSlot(WIRE);
        WireData d = wire.get(ManufacturingComponents.WIRE.get());
        Stator st = Winding.wind(c, d.wire(), d.wireInsulation(), true);
        wire.shrink((int) Math.ceil(Winding.wireNeededM(c) / d.wire().lengthM() - 1e-9));
        held = null;
        inv.setStackInSlot(OUT, Electrical.statorStack(st));
        if (level instanceof ServerLevel sl)
            sl.playSound(null, worldPosition, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.5f, 1.2f);
        halt(STOP_DONE);
    }

    private void halt(int why) {
        stop = why;
        on = false;
        setChanged();
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == ElectricalPanels.BUTTON_RUN) setOn(!on);
    }

    @Override
    public ItemStack takeHeldItem() {
        if (held == null) return ItemStack.EMPTY;
        ItemStack s = Electrical.coreStack(held);
        held = null;
        turns = 0;
        setChanged();
        return s;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return isCoreInput(stack) || isMagnetWire(stack);
    }

    @Override
    public PanelLayout panelLayout() {
        return ElectricalPanels.WINDING_MACHINE;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[ElectricalPanels.M_RPM10] = rpm10();
        out[ElectricalPanels.M_STOP] = stop;
        int f = on ? 1 << ElectricalPanels.MF_ON : 0;
        if (shaftW() > 0) f |= 1 << ElectricalPanels.MF_SHAFT;
        CoreStack c = core();
        if (c != null) {
            f |= 1 << ElectricalPanels.MF_CORE;
            out[ElectricalPanels.M_LAMINATIONS] = c.count();
            out[ElectricalPanels.M_WIRE_NEED_M] = (int) Math.round(Winding.wireNeededM(c));
            boolean stacked = held != null;
            out[ElectricalPanels.M_PROGRESS_PCT] = (int) Math.round(100 * (stacked
                    ? turns / (3.0 * Winding.turnsPerPhase(c.lamination().shape())) : stackS / STACK_S));
            if (stacked) f |= 1 << ElectricalPanels.MF_STACKED;
            ItemStack w = inv.getStackInSlot(WIRE);
            WireData d = w.get(ManufacturingComponents.WIRE.get());
            if (d != null && isMagnetWire(w)) {
                out[ElectricalPanels.M_WIRE_HAVE_M] = (int) Math.round(w.getCount() * d.wire().lengthM());
                out[ElectricalPanels.M_FILL_PCT] = (int) Math.round(100 * Winding.fillFactor(c.lamination().shape(),
                        d.wire().diameterM(), d.wireInsulation()));
            }
        }
        out[ElectricalPanels.FLAGS] = f;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) return;
        tag.putBoolean("on", on);
        tag.putInt("stop", stop);
        tag.putDouble("stack", stackS);
        tag.putDouble("turns", turns);
        if (held != null) ElectricalData.Core.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, ElectricalData.Core.of(held))
                .ifSuccess(t -> tag.put("held", t));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) return;
        on = tag.getBoolean("on");
        stop = Math.max(0, Math.min(STOP_OUTPUT, tag.getInt("stop")));
        double s = tag.getDouble("stack"), t = tag.getDouble("turns");
        stackS = Double.isFinite(s) && s > 0 ? s : 0;
        turns = Double.isFinite(t) && t > 0 ? t : 0;
        held = tag.contains("held") ? ElectricalData.Core.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("held"))
                .result().map(ElectricalData.Core::stack).orElse(null) : null;
    }
}

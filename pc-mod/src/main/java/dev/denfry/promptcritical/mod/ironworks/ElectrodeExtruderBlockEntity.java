package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlockEntity;
import dev.denfry.promptcritical.mod.foundry.MachineInventory;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import dev.denfry.promptcritical.sim.metallurgy.CyclicMachine;
import dev.denfry.promptcritical.sim.metallurgy.ElectrodeExtruder;
import dev.denfry.promptcritical.sim.metallurgy.ElectrodeState;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * Электродный пресс {@code electrode_extruder} (08 §8.4.5) на валу Create: за цикл — 3 предмета наполнителя
 * ({@code pitch_coke}) и пек ({@code coal_tar_pitch}) по доле связующего со щита (окно 25–30 %); масса греется паром
 * до 160 °C (моё: температура рубашки постоянна). Электрод ⌀ 0,3 м — {@code green_electrode} с {@code electrode_state}.
 */
public final class ElectrodeExtruderBlockEntity extends KineticMachineBlockEntity<CyclicMachine> implements PanelHost {
    public static final int FILLER_ITEMS = 3;
    public static final double PASTE_K = 433.15, DIAMETER_M = 0.3;
    private static final MachineInventory.Role[] ROLES = {MachineInventory.Role.INPUT, MachineInventory.Role.INPUT,
            MachineInventory.Role.OUTPUT};

    private final MachineInventory inv = new MachineInventory(ROLES,
            (slot, s) -> slot == 0 ? s.is(PcItems.PITCH_COKE.get()) : s.is(PcItems.COAL_TAR_PITCH.get()), this::setChanged);
    /** Пек в бункере, кг (предметы пека тают в массу). */
    private double pitchKg;
    private int binderPct = 27;

    public ElectrodeExtruderBlockEntity(BlockPos pos, BlockState state) {
        super(IronworksBlockEntities.ELECTRODE_EXTRUDER.get(), pos, state);
    }

    @Override
    @Nullable
    protected CyclicMachine unit(SimLevelBinding b) {
        return Ironworks.shaftMachine(b, simId());
    }

    @Override
    protected ShaftInput shaft(CyclicMachine u) {
        return u.shaft;
    }

    @Override
    protected double powerW(CyclicMachine u) {
        return u.powerW();
    }

    @Override
    protected double ratedW() {
        return 22_000;
    }

    @Override
    protected double nominalRpm() {
        return CyclicMachine.NOMINAL_RPM;
    }

    private double pitchNeedKg() {
        double filler = FILLER_ITEMS * Ironworks.bulkKg();
        return filler * binderPct / (100.0 - binderPct);
    }

    @Override
    protected void serverTick(SimLevelBinding b, CyclicMachine u) {
        ItemStack pitch = inv.getStackInSlot(1);
        if (pitchKg < pitchNeedKg() && !pitch.isEmpty()) {
            pitch.shrink(1);
            pitchKg += Ironworks.bulkKg();
        }
        boolean ready = inv.getStackInSlot(0).getCount() >= FILLER_ITEMS && pitchKg >= pitchNeedKg();
        u.setLoaded(ready);
        if (ready && u.takeCycle()) {
            double filler = FILLER_ITEMS * Ironworks.bulkKg(), p = pitchNeedKg();
            ElectrodeState e = ElectrodeExtruder.extrude(filler, p, PASTE_K, DIAMETER_M);
            if (e != null) {
                ItemStack out = new ItemStack(PcItems.GREEN_ELECTRODE.get());
                out.set(IronworksComponents.ELECTRODE_STATE.get(), IronworksComponents.Electrode.of(e));
                if (inv.outputFits(out)) {
                    inv.getStackInSlot(0).shrink(FILLER_ITEMS);
                    pitchKg -= p;
                    inv.output(out);
                }
            }
        }
        setStateFlag(PcProperties.RUNNING, u.running());
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return inv.external();
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.is(PcItems.PITCH_COKE.get()) || stack.is(PcItems.COAL_TAR_PITCH.get());
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        player.setItemInHand(hand, inv.insertFromPlayer(stack));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        if (inv.giveOutputs(player)) return InteractionResult.SUCCESS;
        player.displayClientMessage(IronworksText.msg("extruder", binderPct, IronworksText.num(pitchKg)), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public PanelLayout panelLayout() {
        return IronworksPanels.EXTRUDER;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return inv;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[IronworksPanels.SHAFT_RPM10] = shaftRpm10();
        out[IronworksPanels.E_BINDER_PCT] = binderPct;
        out[IronworksPanels.E_PITCH_KG] = (int) Math.round(pitchKg);
        CyclicMachine u = unit();
        if (u != null) out[IronworksPanels.E_PROGRESS_PERMIL] = (int) Math.round(u.progress() * 1000);
        if (u != null && u.running()) out[IronworksPanels.FLAGS] = 1 << IronworksPanels.F_RUNNING;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == IronworksPanels.BTN_DOWN) binderPct = Math.max(15, binderPct - 1);
        else if (id == IronworksPanels.BTN_UP) binderPct = Math.min(40, binderPct + 1);
        setChanged();
    }

    public void setBinderPct(int pct) {
        binderPct = Math.max(15, Math.min(40, pct));
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) return;
        tag.put("inventory", inv.save(registries));
        tag.putDouble("pitch_kg", pitchKg);
        tag.putInt("binder_pct", binderPct);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) return;
        if (tag.contains("inventory")) inv.load(registries, tag.getCompound("inventory"));
        pitchKg = tag.getDouble("pitch_kg");
        if (tag.contains("binder_pct")) binderPct = tag.getInt("binder_pct");
    }
}

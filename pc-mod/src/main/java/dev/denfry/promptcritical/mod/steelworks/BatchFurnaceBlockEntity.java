package dev.denfry.promptcritical.mod.steelworks;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.foundry.MachineInventory;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.ironworks.GasBuffer;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.ironworks.IronworksDeviceBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.metallurgy.BatchFurnace;
import dev.denfry.promptcritical.sim.metallurgy.BatchFurnaceUnit;
import dev.denfry.promptcritical.sim.metallurgy.ProcessGas;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Камерная газовая печь прокатного и термического цеха ({@link BatchFurnaceUnit}): газ — трубой, садка — из входа
 * (воронка, рука, рольганг) по местам печи; места — предметы по одному, температуры — в домене. Наследник решает,
 * когда и куда выдавать заготовку.
 */
public abstract class BatchFurnaceBlockEntity extends IronworksDeviceBlockEntity<BatchFurnaceUnit> implements PanelHost, PieceReceiver {
    protected static final int OUT_SLOTS = 4;
    protected final GasBuffer gas = new GasBuffer(8_000, GasBuffer::isProcessGas, this::setChanged);
    protected final MachineInventory inv = new MachineInventory(roles(), (slot, s) -> slot == 0 && accepts(s), this::setChanged);
    protected final ItemStackHandler places;

    protected BatchFurnaceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int capacity) {
        super(type, pos, state, BatchFurnaceUnit.class);
        places = new ItemStackHandler(capacity) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }
        };
    }

    private static MachineInventory.Role[] roles() {
        MachineInventory.Role[] r = new MachineInventory.Role[1 + OUT_SLOTS];
        Arrays.fill(r, MachineInventory.Role.OUTPUT);
        r[0] = MachineInventory.Role.INPUT;
        return r;
    }

    /** Масса места садки, кг, и толщина, м. */
    protected abstract double massKg(ItemStack s);

    protected abstract double thicknessM(ItemStack s);

    /** Выдать то, что пора, и вести своё состояние (дверь). */
    protected abstract void discharge(BatchFurnaceUnit u, long now);

    @Override
    protected void serverTick(SimLevelBinding b, BatchFurnaceUnit u) {
        double dtp = Ironworks.tickPhysS(b);
        ProcessGas g = gas.gas();
        if (g != null) u.setGasSupply(g, gas.draw(u.gasDemandNm3S(g) * dtp) / dtp);
        else u.setGasSupply(null, 0);
        ItemStack in = inv.getStackInSlot(0);
        if (!in.isEmpty() && accepts(in) && load(u, in.copyWithCount(1))) in.shrink(1);
        discharge(u, level.getGameTime());
        setStateFlag(BlockStateProperties.LIT, u.gasSupplyLit());
    }

    /** Посадить предмет на свободное место печи. */
    protected boolean load(BatchFurnaceUnit u, ItemStack piece) {
        long now = level.getGameTime();
        for (int i = 0; i < places.getSlots(); i++) {
            if (!places.getStackInSlot(i).isEmpty() || u.furnace().occupied(i)) continue;
            if (!u.furnace().load(i, massKg(piece), thicknessM(piece), Steelworks.tempK(piece, now))) return false;
            places.setStackInSlot(i, piece);
            return true;
        }
        return false;
    }

    /** Снять место {@code i}: предмет с температурой печи. */
    protected ItemStack take(BatchFurnaceUnit u, int i, long now) {
        ItemStack s = places.getStackInSlot(i);
        double t = u.furnace().unload(i);
        places.setStackInSlot(i, ItemStack.EMPTY);
        SteelworksComponents.SteelStock st = Steelworks.stock(s);
        if (st != null && !Double.isNaN(t)) s.set(SteelworksComponents.STEEL_STOCK.get(), st.withTemp(t, now));
        return s;
    }

    /** Положить в выходы, что не влезло — рядом. */
    protected void toOutput(ItemStack s) {
        ItemStack rest = inv.output(s);
        if (!rest.isEmpty()) Ironworks.spill(level, worldPosition.above(2), rest);
    }

    @Override
    public boolean acceptPiece(ItemStack piece, Direction travel) {
        BatchFurnaceUnit u = unit();
        return u != null && accepts(piece) && load(u, piece.copyWithCount(1));
    }

    @Override
    protected void clientTick() {
        if (!stateFlag(BlockStateProperties.LIT)) return;
        MachineFx fx = fx();
        if (fx.chance(2)) fx.smoke(fx.at(8, 30, 8), false, 1.0);
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return inv.external();
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return gas.input();
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        player.setItemInHand(hand, inv.insertFromPlayer(stack));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        if (inv.giveOutputs(player)) return InteractionResult.SUCCESS;
        BatchFurnaceUnit u = unit();
        if (u != null) player.displayClientMessage(SteelworksText.msg("furnace", SteelworksText.num(u.furnace().tempK() - 273.15),
                SteelworksText.num(u.setpointK() - 273.15), u.furnace().loaded(), u.furnace().capacity()), true);
        return InteractionResult.SUCCESS;
    }

    // --- щит ---

    @Override
    public IItemHandlerModifiable panelItems() {
        return inv;
    }

    @Override
    public abstract PanelLayout panelLayout();

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        BatchFurnaceUnit u = unit();
        if (u == null) return;
        BatchFurnace f = u.furnace();
        out[SteelworksPanels.R_TEMP_C] = (int) Math.round(f.tempK() - 273.15);
        out[SteelworksPanels.R_SET_C] = (int) Math.round(u.setpointK() - 273.15);
        out[SteelworksPanels.R_RAMP_KH] = (int) Math.round(u.rampKH());
        out[SteelworksPanels.R_LOADED] = f.loaded();
        out[SteelworksPanels.R_GAS_MB] = gas.amount();
        double lo = Double.MAX_VALUE, hi = 0;
        for (int i = 0; i < f.capacity(); i++) {
            if (!f.occupied(i)) continue;
            lo = Math.min(lo, f.pieceTempK(i));
            hi = Math.max(hi, f.pieceTempK(i));
        }
        out[SteelworksPanels.R_COLD_C] = hi > 0 ? (int) Math.round(lo - 273.15) : 0;
        out[SteelworksPanels.R_HOT_C] = hi > 0 ? (int) Math.round(hi - 273.15) : 0;
        int flags = u.gasSupplyLit() ? 1 << SteelworksPanels.RF_FIRING : 0;
        out[SteelworksPanels.R_FLAGS] = flags | panelFlags();
    }

    protected int panelFlags() {
        return 0;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        BatchFurnaceUnit u = unit();
        if (u == null) return;
        double set = u.setpointK(), ramp = u.rampKH();
        switch (id) {
            case SteelworksPanels.BTN_SET_DOWN -> set -= 10;
            case SteelworksPanels.BTN_SET_UP -> set += 10;
            case SteelworksPanels.BTN_SET_DOWN_BIG -> set -= 100;
            case SteelworksPanels.BTN_SET_UP_BIG -> set += 100;
            case SteelworksPanels.BTN_RAMP_DOWN -> ramp -= ramp > 100 ? 50 : 10;
            case SteelworksPanels.BTN_RAMP_UP -> ramp += ramp >= 100 ? 50 : 10;
            default -> {
                button(u, id);
                return;
            }
        }
        u.setSetpoint(set, ramp);
    }

    protected void button(BatchFurnaceUnit u, int id) {
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("gas", gas.save(registries));
        tag.put("inv", inv.save(registries));
        tag.put("places", places.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("gas")) gas.load(registries, tag.getCompound("gas"));
        if (tag.contains("inv")) inv.load(registries, tag.getCompound("inv"));
        if (tag.contains("places")) {
            int n = places.getSlots();
            places.deserializeNBT(registries, tag.getCompound("places"));
            if (places.getSlots() != n) places.setSize(n);
        }
    }
}

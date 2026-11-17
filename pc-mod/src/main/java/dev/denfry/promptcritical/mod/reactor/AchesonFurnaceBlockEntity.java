package dev.denfry.promptcritical.mod.reactor;

import dev.denfry.promptcritical.mod.grid.GridDevice;
import dev.denfry.promptcritical.mod.grid.GridDeviceBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.IronworksComponents;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.grid.ConsumerUnit;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.metallurgy.ElectrodeState;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Печь Ачесона {@code acheson_furnace}: электрическая графитация обожжённых электродов (2500–3000 °C, ток через
 * засыпку коксовой мелочи). Нагрузка сети 6 кВ на {@link #DEMAND_KW} кВт, как посёлок-потребитель: без напряжения
 * печь стоит. Энергия на электрод — {@link #GRAPHITIZE_J} [BALANCE]; электроды с трещинами не берёт — из них не
 * выйдет графит ядерной чистоты.
 */
public final class AchesonFurnaceBlockEntity extends GridDeviceBlockEntity<ConsumerUnit> implements PanelHost {
    public static final int DEMAND_KW = 500;
    /** Энергия графитации одного электрода, Дж [BALANCE]: 50 с при полной мощности. */
    public static final double GRAPHITIZE_J = 25e6;
    private static final int OUT = 4;

    private final ItemStackHandler inv = new ItemStackHandler(1 + OUT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 ? isBaked(stack) : stack.is(PcItems.GRAPHITE_ELECTRODE.get());
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    /** Склад для воронок: вход — только в слот электрода, выход — только из выходов. */
    private final IItemHandler external = new IItemHandler() {
        @Override
        public int getSlots() {
            return inv.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inv.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot == 0 ? inv.insertItem(0, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == 0 ? ItemStack.EMPTY : inv.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return inv.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && inv.isItemValid(0, stack);
        }
    };
    private double energyJ;
    private boolean demandSet;

    public AchesonFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ReactorBlockEntities.ACHESON_FURNACE.get(), pos, state);
    }

    /** Обожжённый электрод без трещин. */
    public static boolean isBaked(ItemStack s) {
        IronworksComponents.Electrode e = s.get(IronworksComponents.ELECTRODE_STATE.get());
        return s.is(PcItems.GREEN_ELECTRODE.get()) && e != null && e.stage() == ElectrodeState.BAKED && !e.cracked();
    }

    @Override
    protected ConsumerUnit unitOf(GridSubsystem grid) {
        return grid.consumer(simId());
    }

    @Override
    public List<List<GridDevice.Face>> terminals() {
        return List.of(GridDevice.allFaces(worldPosition));
    }

    @Override
    protected void serverTick(ConsumerUnit u) {
        if (!demandSet) {
            u.setDemandKw(DEMAND_KW);
            demandSet = true;
        }
        boolean running = u.energized() && isBaked(inv.getStackInSlot(0)) && hasRoom();
        if (running) {
            energyJ += u.powerW() * 0.05;
            if (energyJ >= GRAPHITIZE_J) {
                energyJ = 0;
                inv.getStackInSlot(0).shrink(1);
                output(new ItemStack(PcItems.GRAPHITE_ELECTRODE.get()));
            }
            if (ticks % 10 == 0 && level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.SMOKE, worldPosition.getX() + 0.5, worldPosition.getY() + 0.9,
                        worldPosition.getZ() + 0.5, 2, 0.3, 0.05, 0.3, 0.005);
            }
        }
        if (ticks % 10 == 0) setFlag(BlockStateProperties.LIT, running);
    }

    private boolean hasRoom() {
        for (int i = 1; i <= OUT; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty() || s.getCount() < s.getMaxStackSize()) return true;
        }
        return false;
    }

    private void output(ItemStack s) {
        for (int i = 1; i <= OUT && !s.isEmpty(); i++) s = inv.insertItem(i, s, false);
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return external;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return isBaked(stack);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        player.setItemInHand(hand, inv.insertItem(0, stack, false));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        for (int i = 1; i <= OUT; i++) {
            ItemStack s = inv.extractItem(i, 64, false);
            if (!s.isEmpty() && !player.addItem(s)) player.drop(s, false);
        }
        return InteractionResult.SUCCESS;
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return ReactorPanels.ACHESON;
    }

    @Override
    public Component panelTitle() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public BlockPos panelPos() {
        return worldPosition;
    }

    @Override
    public boolean panelValid(Player player) {
        return PanelHost.near(this, player);
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return inv;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        ConsumerUnit u = unit();
        if (u == null) return;
        out[ReactorPanels.A_POWER_KW] = (int) Math.round(u.powerW() / 1e3);
        out[ReactorPanels.A_PROGRESS_PERMIL] = (int) Math.round(1000 * energyJ / GRAPHITIZE_J);
        int f = 0;
        if (u.energized()) f |= 1 << ReactorPanels.AF_ENERGIZED;
        if (u.switchedOn()) f |= 1 << ReactorPanels.AF_SWITCH;
        if (u.energized() && isBaked(inv.getStackInSlot(0))) f |= 1 << ReactorPanels.AF_RUNNING;
        out[ReactorPanels.A_FLAGS] = f;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        ConsumerUnit u = unit();
        if (u != null && id == ReactorPanels.BTN_SWITCH) u.setSwitchedOn(!u.switchedOn());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inv", inv.serializeNBT(registries));
        tag.putDouble("energy_j", energyJ);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("inv")) inv.deserializeNBT(registries, tag.getCompound("inv"));
        energyJ = tag.getDouble("energy_j");
    }
}

package dev.denfry.promptcritical.mod.electronics;

import dev.denfry.promptcritical.mod.grid.GridDevice;
import dev.denfry.promptcritical.mod.grid.GridDeviceBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.electronics.SiliconChain;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.ConsumerUnit;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Рудно-термическая печь {@code submerged_arc_furnace} (11 §11.5.2): кварц и кокс под электродами, дуга под шихтой.
 * Нагрузка сети {@value #DEMAND_KW} кВт; на кусок технического кремния 4 кг уходит {@link SiliconChain#safKwhPerKg()}
 * кВт·ч/кг — реакция, нагрев и потери, около 12 кВт·ч/кг, как у реальных печей. Графитовый электрод сгорает за
 * {@value #BATCHES_PER_ELECTRODE} партий (≈ 0,1 кг электрода на кг кремния у печей с графитовыми электродами).
 */
public final class SubmergedArcFurnaceBlockEntity extends GridDeviceBlockEntity<ConsumerUnit> implements PanelHost {
    public static final int DEMAND_KW = 3000, BATCHES_PER_ELECTRODE = 16;
    /** Энергия партии, Дж. */
    public static final double BATCH_J = SiliconChain.safKwhPerKg() * ElectronicsComponents.MG_SI_KG_PER_ITEM * SiliconChain.J_PER_KWH;
    private static final int QUARTZ = 0, COKE = 1, ELECTRODE = 2, OUT = 3;

    private final ItemStackHandler inv = new ItemStackHandler(4) {
        @Override
        public boolean isItemValid(int slot, ItemStack s) {
            return switch (slot) {
                case QUARTZ -> s.is(Items.QUARTZ);
                case COKE -> s.is(PcItems.COKE.get());
                case ELECTRODE -> s.is(PcItems.GRAPHITE_ELECTRODE.get());
                default -> s.is(PcItems.METALLURGICAL_SILICON.get());
            };
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IItemHandler external = new ElectronicsBlocks.SlotsIo(inv, 3, OUT);
    private double energyJ;
    private int electrodeUses;
    private boolean demandSet, running;

    public SubmergedArcFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ElectronicsBlocks.SUBMERGED_ARC_FURNACE.get(), pos, state);
    }

    @Override
    protected ConsumerUnit unitOf(GridSubsystem grid) {
        return grid.consumer(simId());
    }

    @Override
    public List<List<GridDevice.Face>> terminals() {
        return List.of(GridDevice.allFaces(worldPosition));
    }

    private boolean charged() {
        return inv.getStackInSlot(QUARTZ).getCount() >= ElectronicsComponents.QUARTZ_PER_ITEM
                && inv.getStackInSlot(COKE).getCount() >= ElectronicsComponents.COKE_PER_ITEM && !inv.getStackInSlot(ELECTRODE).isEmpty()
                && inv.insertItem(OUT, new ItemStack(PcItems.METALLURGICAL_SILICON.get()), true).isEmpty();
    }

    @Override
    protected void serverTick(ConsumerUnit u) {
        if (!demandSet) {
            u.setDemandKw(DEMAND_KW);
            demandSet = true;
        }
        running = u.energized() && charged();
        if (running) {
            energyJ += u.powerW() * 0.05;
            if (energyJ >= BATCH_J) {
                energyJ = 0;
                inv.getStackInSlot(QUARTZ).shrink(ElectronicsComponents.QUARTZ_PER_ITEM);
                inv.getStackInSlot(COKE).shrink(ElectronicsComponents.COKE_PER_ITEM);
                if (++electrodeUses >= BATCHES_PER_ELECTRODE) {
                    electrodeUses = 0;
                    inv.getStackInSlot(ELECTRODE).shrink(1);
                }
                inv.insertItem(OUT, new ItemStack(PcItems.METALLURGICAL_SILICON.get()), false);
            }
            if (ticks % 8 == 0 && level instanceof ServerLevel sl)
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1, worldPosition.getZ() + 0.5,
                        2, 0.25, 0.05, 0.25, 0.01);
        }
        if (ticks % 10 == 0) setFlag(BlockStateProperties.LIT, running);
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return external;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        for (int i = 0; i < 3; i++) if (inv.isItemValid(i, stack)) return true;
        return false;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        ItemStack left = stack;
        for (int i = 0; i < 3 && !left.isEmpty(); i++) left = inv.insertItem(i, left, false);
        player.setItemInHand(hand, left);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        ItemStack s = inv.extractItem(OUT, 64, false);
        if (!s.isEmpty() && !player.addItem(s)) player.drop(s, false);
        return InteractionResult.SUCCESS;
    }

    @Override
    public PanelLayout panelLayout() {
        return ElectronicsPanels.SAF;
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
        out[ElectronicsPanels.E_A] = (int) Math.round(SiliconChain.safKwhPerKg() * 10);
        out[ElectronicsPanels.E_B] = 100 - electrodeUses * 100 / BATCHES_PER_ELECTRODE;
        out[ElectronicsPanels.E_PROGRESS] = (int) Math.round(1000 * energyJ / BATCH_J);
        if (u == null) return;
        out[ElectronicsPanels.E_POWER_KW] = (int) Math.round(u.powerW() / 1e3);
        int f = 0;
        if (u.energized()) f |= 1 << ElectronicsPanels.EF_ENERGIZED;
        if (u.switchedOn()) f |= 1 << ElectronicsPanels.EF_SWITCH;
        if (running) f |= 1 << ElectronicsPanels.EF_RUNNING;
        if (!charged()) f |= 1 << ElectronicsPanels.EF_SHORT;
        out[ElectronicsPanels.E_FLAGS] = f;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        ConsumerUnit u = unit();
        if (u != null && id == ElectronicsPanels.BTN_SWITCH) u.setSwitchedOn(!u.switchedOn());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inv", inv.serializeNBT(registries));
        tag.putDouble("energy_j", energyJ);
        tag.putInt("electrode", electrodeUses);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("inv")) inv.deserializeNBT(registries, tag.getCompound("inv"));
        energyJ = tag.getDouble("energy_j");
        electrodeUses = tag.getInt("electrode");
    }
}

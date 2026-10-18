package dev.denfry.promptcritical.mod.foundry;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import dev.denfry.promptcritical.sim.metallurgy.CastingGeometry;
import dev.denfry.promptcritical.sim.metallurgy.CyclicMachine;
import dev.denfry.promptcritical.sim.metallurgy.FoundrySubsystem;
import dev.denfry.promptcritical.sim.metallurgy.SandMoldUnit;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * Формовочная машина {@code molding_station} (08 §8.9): встряхиванием на валу Create уплотняет {@code foundry_sand}
 * с водой вокруг {@code casting_pattern} — за цикл одна заформованная {@code sand_mold}. Модель не расходуется.
 * Вода — 5 мБ на предмет смеси (влажность 5 % на 100 кг); без воды форма выходит сухой (2 %).
 */
public final class MoldingStationBlockEntity extends KineticMachineBlockEntity<CyclicMachine> implements PanelHost {
    public static final int WATER_PER_SAND_MB = 5;
    public static final float DRY_MOISTURE = 0.02f;
    private static final MachineInventory.Role[] ROLES = {MachineInventory.Role.INPUT, MachineInventory.Role.SERVICE,
            MachineInventory.Role.OUTPUT};

    private final MachineInventory inv = new MachineInventory(ROLES,
            (slot, s) -> slot == 0 ? s.is(PcItems.FOUNDRY_SAND.get()) : s.is(PcItems.CASTING_PATTERN.get()), this::setChanged);
    private final FluidTank water = new FluidTank(4_000,
            f -> f.is(Fluids.WATER) || f.is(PcFluids.RAW_WATER.source.get())) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    public MoldingStationBlockEntity(BlockPos pos, BlockState state) {
        super(FoundryBlockEntities.MOLDING_STATION.get(), pos, state);
    }

    @Override
    @Nullable
    protected CyclicMachine unit(SimLevelBinding b) {
        return b.world().subsystem(FoundrySubsystem.class).unit(b.world().topology(), simId()) instanceof CyclicMachine u ? u : null;
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
        return 7_500;
    }

    @Override
    protected double nominalRpm() {
        return CyclicMachine.NOMINAL_RPM;
    }

    @Nullable
    private FoundryComponents.Pattern pattern() {
        ItemStack p = inv.getStackInSlot(1);
        return p.isEmpty() ? null : CastingPatternItem.pattern(p);
    }

    @Override
    protected void serverTick(SimLevelBinding b, CyclicMachine u) {
        FoundryComponents.Pattern p = pattern();
        int need = p == null ? 0 : CastingGeometry.of(p.kind()).sandItems();
        ItemStack mold = new ItemStack(PcBlocks.SAND_MOLD.get());
        boolean loaded = p != null && inv.getStackInSlot(0).getCount() >= need && inv.outputFits(mold);
        u.setLoaded(loaded);
        if (loaded && u.takeCycle()) {
            inv.getStackInSlot(0).shrink(need);
            int mb = need * WATER_PER_SAND_MB;
            float moisture = water.getFluidAmount() >= mb ? (float) SandMoldUnit.DEFAULT_MOISTURE : DRY_MOISTURE;
            if (water.getFluidAmount() >= mb) water.drain(mb, IFluidHandler.FluidAction.EXECUTE);
            mold.set(FoundryComponents.SAND_MOLD.get(), new FoundryComponents.Mold(p.kind(), p.riser(), moisture));
            inv.output(mold);
            setChanged();
        }
        setStateFlag(PcProperties.RUNNING, u.running());
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return inv.external();
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return water;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.is(PcItems.FOUNDRY_SAND.get()) || stack.is(PcItems.CASTING_PATTERN.get()) || stack.is(Items.WATER_BUCKET);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (stack.is(Items.WATER_BUCKET)) {
            if (water.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.SIMULATE) < 1000)
                return ItemInteractionResult.CONSUME;
            water.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
            if (!player.getAbilities().instabuild) player.setItemInHand(hand, new ItemStack(Items.BUCKET));
            return ItemInteractionResult.SUCCESS;
        }
        player.setItemInHand(hand, inv.insertFromPlayer(stack));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        inv.giveOutputs(player);
        FoundryComponents.Pattern p = pattern();
        player.displayClientMessage(p == null ? FoundryText.msg("molding_no_pattern")
                : FoundryText.msg("molding", FoundryText.pattern(p), inv.getStackInSlot(0).getCount(),
                        CastingGeometry.of(p.kind()).sandItems(), water.getFluidAmount()), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public PanelLayout panelLayout() {
        return FoundryPanels.MOLDING;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return inv;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[FoundryPanels.SHAFT_RPM10] = shaftRpm10();
        FoundryComponents.Pattern p = pattern();
        out[FoundryPanels.M_KIND] = p == null ? -1 : p.kind().ordinal();
        out[FoundryPanels.M_NEED] = p == null ? 0 : CastingGeometry.of(p.kind()).sandItems();
        out[FoundryPanels.M_SAND] = inv.getStackInSlot(0).getCount();
        out[FoundryPanels.M_WATER_MB] = water.getFluidAmount();
        CyclicMachine u = unit();
        if (u == null) return;
        out[FoundryPanels.FLAGS] = u.running() ? 1 << FoundryPanels.F_RUNNING : 0;
        out[FoundryPanels.M_PROGRESS_PERMIL] = (int) Math.round(u.progress() * 1000);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!clientPacket) {
            tag.put("inventory", inv.save(registries));
            tag.put("water", water.writeToNBT(registries, new CompoundTag()));
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) return;
        if (tag.contains("inventory")) inv.load(registries, tag.getCompound("inventory"));
        if (tag.contains("water")) water.readFromNBT(registries, tag.getCompound("water"));
    }
}

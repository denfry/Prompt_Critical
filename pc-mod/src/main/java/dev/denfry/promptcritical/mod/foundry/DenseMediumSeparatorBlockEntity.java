package dev.denfry.promptcritical.mod.foundry;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.item.CoalAssayData;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import dev.denfry.promptcritical.sim.mining.CoalAssay;
import dev.denfry.promptcritical.sim.mining.DenseMediumSeparator;
import dev.denfry.promptcritical.sim.mining.DenseMediumSubsystem;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * Тяжелосредный сепаратор {@code dense_medium_separator} (08 §8.3, I1): рядовой уголь и магнетит
 * ({@code iron_ore_concentrate}) на входе, концентрат и порода с {@code coal_assay} на выходе; вода контура — трубой
 * или ведром. Плотность разделения — плотность суспензии, её задаёт доза магнетита в воде контура
 * ({@link DenseMediumSeparator}).
 */
public final class DenseMediumSeparatorBlockEntity extends KineticMachineBlockEntity<DenseMediumSeparator> implements PanelHost {
    static final int FEED = 0, MAGNETITE = 1;
    private static final MachineInventory.Role[] ROLES = {MachineInventory.Role.INPUT, MachineInventory.Role.INPUT,
            MachineInventory.Role.OUTPUT, MachineInventory.Role.OUTPUT};

    private final MachineInventory inv = new MachineInventory(ROLES, DenseMediumSeparatorBlockEntity::fits, this::setChanged);
    private final IFluidHandler water = new Water();

    public DenseMediumSeparatorBlockEntity(BlockPos pos, BlockState state) {
        super(FoundryBlockEntities.DENSE_MEDIUM_SEPARATOR.get(), pos, state);
    }

    private static boolean fits(int slot, ItemStack s) {
        return slot == FEED ? s.is(FoundryTags.JIG_FEED) : slot == MAGNETITE && s.is(PcItems.IRON_ORE_CONCENTRATE.get());
    }

    @Override
    @Nullable
    protected DenseMediumSeparator unit(SimLevelBinding b) {
        return b.world().subsystem(DenseMediumSubsystem.class).unit(b.world().topology(), simId());
    }

    @Override
    protected ShaftInput shaft(DenseMediumSeparator u) {
        return u.shaft;
    }

    @Override
    protected double powerW(DenseMediumSeparator u) {
        return u.powerW();
    }

    @Override
    protected double ratedW() {
        return DenseMediumSeparator.RATED_W;
    }

    @Override
    protected double nominalRpm() {
        return DenseMediumSeparator.NOMINAL_RPM;
    }

    @Override
    protected void serverTick(SimLevelBinding b, DenseMediumSeparator u) {
        double kg = ServerConfig.METALLURGY_BULK_ITEM_MASS_KG.get();
        ItemStack in = inv.getStackInSlot(FEED);
        if (!in.isEmpty() && u.feed(CoalJigBlockEntity.assay(in), kg)) in.shrink(1);
        // магнетит дозируется сам, пока суспензия принимает целый предмет (не гуще 2,0 г/см³ и в объёме контура)
        ItemStack mag = inv.getStackInSlot(MAGNETITE);
        if (!mag.isEmpty() && u.addMagnetite(kg)) mag.shrink(1);
        emit(u, DenseMediumSeparator.Product.CLEAN, PcItems.WASHED_COAL.get(), kg);
        emit(u, DenseMediumSeparator.Product.REFUSE, PcItems.COAL_REFUSE.get(), kg);
        setStateFlag(PcProperties.RUNNING, u.running());
    }

    /** Суспензия с всплывшим углём бежит по сливному жёлобу у восточного торца барабана; шум барабана и насоса. */
    @Override
    protected void clientTick() {
        MachineFx fx = fx();
        if (fx == null || !runningState()) return;
        var r = fx.random();
        // жёлоб всплывшего float_flow: x 28,4…31,6, y 15,5, z 2,4…13,6 (модель арт-реестра)
        if (fx.chance(4)) fx.splash(fx.at(28.5 + r.nextDouble() * 3, 15.6, 3 + r.nextDouble() * 10), 0.2, 1);
        if (fx.chance(0.5)) fx.sound(fx.at(16, 16, 16), SoundEvents.WATER_AMBIENT, 0.2f, 0.8f);
    }

    private void emit(DenseMediumSeparator u, DenseMediumSeparator.Product p, Item item, double kg) {
        while (u.productKg(p) >= kg) {
            ItemStack out = new ItemStack(item);
            if (!inv.outputFits(out)) return;
            CoalAssay a = u.take(p, kg);
            out.set(PcDataComponents.COAL_ASSAY.get(), CoalAssayData.of(a));
            inv.output(out);
        }
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
        return fits(FEED, stack) || fits(MAGNETITE, stack) || stack.is(Items.WATER_BUCKET);
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
        DenseMediumSeparator u = unit();
        if (u != null) player.displayClientMessage(FoundryText.msg("dms", FoundryText.num(u.feedKg()),
                FoundryText.num(u.mediumL()), FoundryText.num(u.mediumDensity())), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public PanelLayout panelLayout() {
        return FoundryPanels.DENSE_MEDIUM;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return inv;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        out[FoundryPanels.SHAFT_RPM10] = shaftRpm10();
        DenseMediumSeparator u = unit();
        if (u == null) return;
        out[FoundryPanels.FLAGS] = u.running() ? 1 << FoundryPanels.F_RUNNING : 0;
        out[FoundryPanels.D_FEED_KG] = (int) Math.round(u.feedKg());
        out[FoundryPanels.D_MEDIUM_L] = (int) Math.round(u.mediumL());
        out[FoundryPanels.D_RHO100] = (int) Math.round(u.mediumDensity() * 100);
        out[FoundryPanels.D_MAGNETITE_KG] = (int) Math.round(u.magnetiteKg());
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!clientPacket) tag.put("inventory", inv.save(registries));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (!clientPacket && tag.contains("inventory")) inv.load(registries, tag.getCompound("inventory"));
    }

    /** Вода контура суспензии: только залив, вода и {@code raw_water}; запас — в домене. */
    private final class Water implements IFluidHandler {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return (int) DenseMediumSeparator.MEDIUM_CAPACITY_L;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return stack.is(Fluids.WATER) || stack.is(PcFluids.RAW_WATER.source.get());
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            DenseMediumSeparator u = unit();
            if (u == null || resource.isEmpty() || !isFluidValid(0, resource)) return 0;
            int room = (int) Math.floor(DenseMediumSeparator.MEDIUM_CAPACITY_L - u.mediumL());
            int add = Math.max(0, Math.min(room, resource.getAmount()));
            if (add > 0 && action.execute()) u.addWater(add);
            return add;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }
}

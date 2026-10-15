package dev.denfry.promptcritical.mod.foundry;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.item.CoalAssayData;
import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.mechanics.ShaftInput;
import dev.denfry.promptcritical.sim.mining.CoalAssay;
import dev.denfry.promptcritical.sim.mining.CoalJig;
import dev.denfry.promptcritical.sim.mining.CoalPrepSubsystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Отсадочная машина {@code coal_jig} (08 §8.3): рядовой уголь на входе, три продукта с {@code coal_assay} на выходе,
 * подпиточная вода — трубой или ведром (в домене, {@link CoalJig}). Ключ Create меняет плотность разделения.
 */
public final class CoalJigBlockEntity extends KineticMachineBlockEntity<CoalJig> implements KineticMachineBlock.Wrenchable {
    /** Уголь без анализа — уголь Г из T8.1. */
    static final CoalAssayData DEFAULT_ASSAY = new CoalAssayData(37, 21, 6, 20);
    private static final MachineInventory.Role[] ROLES = {MachineInventory.Role.INPUT, MachineInventory.Role.OUTPUT,
            MachineInventory.Role.OUTPUT, MachineInventory.Role.OUTPUT};

    private final MachineInventory inv = new MachineInventory(ROLES, (slot, s) -> s.is(FoundryTags.JIG_FEED), this::setChanged);
    private final IFluidHandler water = new Water();

    public CoalJigBlockEntity(BlockPos pos, BlockState state) {
        super(FoundryBlockEntities.COAL_JIG.get(), pos, state);
    }

    @Override
    @Nullable
    protected CoalJig unit(SimLevelBinding b) {
        return b.world().subsystem(CoalPrepSubsystem.class).unit(b.world().topology(), simId());
    }

    @Override
    protected ShaftInput shaft(CoalJig u) {
        return u.shaft;
    }

    @Override
    protected double powerW(CoalJig u) {
        return u.powerW();
    }

    @Override
    protected double ratedW() {
        return CoalJig.RATED_W;
    }

    @Override
    protected double nominalRpm() {
        return CoalJig.NOMINAL_RPM;
    }

    @Override
    protected void serverTick(SimLevelBinding b, CoalJig u) {
        double kg = ServerConfig.METALLURGY_BULK_ITEM_MASS_KG.get();
        ItemStack in = inv.getStackInSlot(0);
        if (!in.isEmpty() && u.feed(assay(in), kg)) in.shrink(1);
        emit(u, CoalJig.Product.CLEAN, PcItems.WASHED_COAL.get(), kg);
        emit(u, CoalJig.Product.MIDDLINGS, PcItems.COAL_MIDDLINGS.get(), kg);
        emit(u, CoalJig.Product.REFUSE, PcItems.COAL_REFUSE.get(), kg);
        setStateFlag(PcProperties.RUNNING, u.running());
    }

    static CoalAssay assay(ItemStack stack) {
        return stack.getOrDefault(PcDataComponents.COAL_ASSAY.get(), DEFAULT_ASSAY).toAssay();
    }

    private void emit(CoalJig u, CoalJig.Product p, Item item, double kg) {
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
        return stack.is(FoundryTags.JIG_FEED) || stack.is(Items.WATER_BUCKET);
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
        CoalJig u = unit();
        if (u != null) player.displayClientMessage(FoundryText.msg("jig", FoundryText.num(u.feedKg()),
                FoundryText.num(u.waterL()), FoundryText.num(u.rhoClean()), FoundryText.num(u.rhoRefuse())), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onWrenched(UseOnContext ctx) {
        if (ctx.getLevel().isClientSide) return InteractionResult.SUCCESS;
        CoalJig u = unit();
        if (u == null) return InteractionResult.PASS;
        double rho = u.cycleRhoClean();
        if (ctx.getPlayer() != null) ctx.getPlayer().displayClientMessage(FoundryText.msg("jig_cut", FoundryText.num(rho)), true);
        return InteractionResult.SUCCESS;
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

    /** Подпиточная вода: только залив, вода и {@code raw_water}; запас — в домене. */
    private final class Water implements IFluidHandler {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            CoalJig u = unit();
            return u == null || u.waterL() < 1 ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, (int) u.waterL());
        }

        @Override
        public int getTankCapacity(int tank) {
            return (int) CoalJig.WATER_CAPACITY_L;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return stack.is(Fluids.WATER) || stack.is(PcFluids.RAW_WATER.source.get());
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            CoalJig u = unit();
            if (u == null || resource.isEmpty() || !isFluidValid(0, resource)) return 0;
            int room = (int) Math.floor(CoalJig.WATER_CAPACITY_L - u.waterL());
            int add = Math.min(room, resource.getAmount());
            if (add > 0 && action.execute()) u.addWater(add);
            return Math.max(0, add);
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

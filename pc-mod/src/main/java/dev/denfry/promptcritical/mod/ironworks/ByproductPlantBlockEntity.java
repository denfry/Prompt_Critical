package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.MachineInventory;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcItems;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * Аппарат улавливания {@code byproduct_plant} (08 §8.4.4), касается газосборника батареи. Вид — по свойству
 * {@code kind}: холодильник ({@code cooler}, ≈ 60 % смолы) и электрофильтр ({@code precipitator}, остаток) —
 * {@code coal_tar} в бак; сатуратор — NH₃ + H₂SO₄ (жидкость тега {@code c:sulfuric_acid}) → {@code ammonium_sulfate},
 * 15,5 кг NH₃ + 44,7 кг H₂SO₄ → 60,2 кг; скруббер — бензол поглотительным маслом ({@code c:wash_oil}, потери
 * 0,1 л/кг [BALANCE]) → {@code crude_benzol}. Чего не уловили — уходит с газом.
 */
public final class ByproductPlantBlockEntity extends IronworksBlockEntity implements PanelHost {
    static final double TAR_DENSITY = 1.17, BENZOL_DENSITY = 0.88, ACID_DENSITY = 1.84, ACID_PER_NH3 = 44.7 / 15.5,
            SULFATE_PER_NH3 = 60.2 / 15.5, WASH_OIL_L_PER_KG = 0.1;

    private final GasBuffer reagent = new GasBuffer(8_000, this::reagentValid, this::setChanged);
    private final GasBuffer product = new GasBuffer(8_000, s -> true, this::setChanged);
    private final MachineInventory out = new MachineInventory(new MachineInventory.Role[] {MachineInventory.Role.OUTPUT},
            (slot, s) -> false, this::setChanged);
    private double sulfateKg, capturedKg;

    public ByproductPlantBlockEntity(BlockPos pos, BlockState state) {
        super(IronworksBlockEntities.BYPRODUCT_PLANT.get(), pos, state);
    }

    public PcProperties.ByproductKind kind() {
        BlockState s = getBlockState();
        return s.hasProperty(PcProperties.BYPRODUCT_KIND) ? s.getValue(PcProperties.BYPRODUCT_KIND) : PcProperties.ByproductKind.COOLER;
    }

    private boolean reagentValid(FluidStack s) {
        return switch (kind()) {
            case SATURATOR -> s.is(IronworksTags.SULFURIC_ACID);
            case SCRUBBER -> s.is(IronworksTags.WASH_OIL);
            default -> false;
        };
    }

    /** Уловить {@code kg} своего продукта из газа батареи (за период SLOW). */
    void capture(double kg) {
        if (!(kg > 0)) return;
        switch (kind()) {
            case COOLER, PRECIPITATOR -> capturedKg += product.put(GasBuffer.fluid("coal_tar"), kg / TAR_DENSITY) * TAR_DENSITY;
            case SATURATOR -> {
                double used = reagent.draw(kg * ACID_PER_NH3 / ACID_DENSITY) * ACID_DENSITY / ACID_PER_NH3;
                sulfateKg += used * SULFATE_PER_NH3;
                capturedKg += used;
                double item = Ironworks.bulkKg();
                while (sulfateKg >= item && out.outputFits(new ItemStack(PcItems.AMMONIUM_SULFATE.get()))) {
                    out.output(new ItemStack(PcItems.AMMONIUM_SULFATE.get()));
                    sulfateKg -= item;
                }
            }
            case SCRUBBER -> {
                double need = kg * WASH_OIL_L_PER_KG;
                if (reagent.amount() < need) return;
                reagent.draw(need);
                capturedKg += product.put(GasBuffer.fluid("crude_benzol"), kg / BENZOL_DENSITY) * BENZOL_DENSITY;
            }
        }
        setChanged();
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return out.external();
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return new Split(reagent.input(), product.output());
    }

    @Override
    public InteractionResult use(Player player) {
        if (out.giveOutputs(player)) return InteractionResult.SUCCESS;
        player.displayClientMessage(IronworksText.msg("byproduct", IronworksText.msg("byproduct." + kind().getSerializedName()),
                IronworksText.num(capturedKg), product.amount(), reagent.amount()), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public PanelLayout panelLayout() {
        return IronworksPanels.BYPRODUCT;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return out;
    }

    @Override
    public void readPanel(int[] values) {
        Arrays.fill(values, 0);
        values[IronworksPanels.P_KIND] = kind().ordinal();
        values[IronworksPanels.P_CAPTURED_KG] = (int) Math.round(capturedKg);
        values[IronworksPanels.P_PRODUCT_MB] = product.amount();
        values[IronworksPanels.P_REAGENT_MB] = reagent.amount();
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id != IronworksPanels.BTN_KIND || product.amount() > 0 || reagent.amount() > 0) return;
        PcProperties.ByproductKind[] all = PcProperties.ByproductKind.values();
        BlockState s = getBlockState();
        level.setBlock(worldPosition, s.setValue(PcProperties.BYPRODUCT_KIND, all[(kind().ordinal() + 1) % all.length]), Block.UPDATE_ALL);
    }

    public double capturedKg() {
        return capturedKg;
    }

    public GasBuffer product() {
        return product;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("reagent", reagent.save(registries));
        tag.put("product", product.save(registries));
        tag.put("out", out.save(registries));
        tag.putDouble("sulfate_kg", sulfateKg);
        tag.putDouble("captured_kg", capturedKg);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("reagent")) reagent.load(registries, tag.getCompound("reagent"));
        if (tag.contains("product")) product.load(registries, tag.getCompound("product"));
        if (tag.contains("out")) out.load(registries, tag.getCompound("out"));
        sulfateKg = tag.getDouble("sulfate_kg");
        capturedKg = tag.getDouble("captured_kg");
    }

    /** Налив — в бак реагента, слив — из бака продукта. */
    record Split(IFluidHandler in, IFluidHandler outlet) implements IFluidHandler {
        @Override
        public int getTanks() {
            return 2;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return (tank == 0 ? in : outlet).getFluidInTank(0);
        }

        @Override
        public int getTankCapacity(int tank) {
            return (tank == 0 ? in : outlet).getTankCapacity(0);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 0 && in.isFluidValid(0, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return in.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return outlet.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return outlet.drain(maxDrain, action);
        }
    }
}

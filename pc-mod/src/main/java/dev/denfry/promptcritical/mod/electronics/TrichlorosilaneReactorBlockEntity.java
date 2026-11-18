package dev.denfry.promptcritical.mod.electronics;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.gas.PortHandler;
import dev.denfry.promptcritical.mod.ironworks.IronworksBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.electronics.SiliconChain;
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
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Реактор кипящего слоя {@code trichlorosilane_reactor} (11 §11.5.2): молотый технический кремний в потоке HCl при
 * 300 °C. Реакция экзотермична (−141 кДж/моль), слой держит температуру сам — сеть не нужна. Конденсатор и
 * ректификация встроены: снизу выходит очищенный жидкий SiHCl₃, с остальных граней — водород, SiCl₄ уходит в
 * отходы. Партия — кусок кремния 4 кг за {@value #BATCH_TICKS} тиков.
 */
public final class TrichlorosilaneReactorBlockEntity extends IronworksBlockEntity implements PanelHost {
    public static final int BATCH_TICKS = 400;
    static final double KG = ElectronicsComponents.MG_SI_KG_PER_ITEM, HCL_NM3 = SiliconChain.hclNm3PerKgSi() * KG,
            TCS_L = SiliconChain.tcsLitresPerKgSi() * KG, H2_NM3 = SiliconChain.hydrogenNm3PerKgSi() * KG;

    private final FeedTank hcl = new FeedTank(PcFluids.HYDROGEN_CHLORIDE.source.get(), 100);
    private final ProductTank tcs = new ProductTank(PcFluids.TRICHLOROSILANE.source.get(), 100);
    private final ProductTank h2 = new ProductTank(PcFluids.HYDROGEN.source.get(), 100);
    private final IFluidHandler bottom = new PortHandler(tcs, hcl), sides = new PortHandler(h2, hcl);
    private final ItemStackHandler inv = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(PcItems.METALLURGICAL_SILICON.get());
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private int progress;
    private boolean running;

    public TrichlorosilaneReactorBlockEntity(BlockPos pos, BlockState state) {
        super(ElectronicsBlocks.TRICHLOROSILANE_REACTOR.get(), pos, state);
    }

    private boolean fed() {
        return !inv.getStackInSlot(0).isEmpty() && hcl.amount() >= HCL_NM3 && tcs.room() >= TCS_L && h2.room() >= H2_NM3;
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        running = fed();
        if (!running) return;
        if (++progress >= BATCH_TICKS) {
            progress = 0;
            inv.getStackInSlot(0).shrink(1);
            hcl.take(HCL_NM3);
            tcs.add(TCS_L);
            h2.add(H2_NM3);
            setChanged();
        }
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return side == Direction.DOWN ? bottom : sides;
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return inv;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return inv.isItemValid(0, stack);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        player.setItemInHand(hand, inv.insertItem(0, stack, false));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        return InteractionResult.PASS;
    }

    @Override
    public PanelLayout panelLayout() {
        return ElectronicsPanels.TCS;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return inv;
    }

    @Override
    public void readPanel(int[] o) {
        Arrays.fill(o, 0);
        o[ElectronicsPanels.E_PROGRESS] = progress * 1000 / BATCH_TICKS;
        o[ElectronicsPanels.E_A] = (int) Math.round(HCL_NM3 * 10);
        o[ElectronicsPanels.E_B] = (int) Math.round(TCS_L * 10);
        o[ElectronicsPanels.E_C] = (int) Math.round(H2_NM3 * 10);
        int f = 0;
        if (running) f |= 1 << ElectronicsPanels.EF_RUNNING;
        else f |= 1 << ElectronicsPanels.EF_SHORT;
        o[ElectronicsPanels.E_FLAGS] = f;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inv", inv.serializeNBT(registries));
        tag.putInt("progress", progress);
        hcl.save(tag, "hcl");
        tcs.save(tag, "tcs");
        h2.save(tag, "h2");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("inv")) inv.deserializeNBT(registries, tag.getCompound("inv"));
        progress = tag.getInt("progress");
        hcl.load(tag, "hcl");
        tcs.load(tag, "tcs");
        h2.load(tag, "h2");
    }
}

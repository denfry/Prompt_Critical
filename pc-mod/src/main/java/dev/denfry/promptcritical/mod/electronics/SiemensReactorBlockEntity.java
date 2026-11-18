package dev.denfry.promptcritical.mod.electronics;

import dev.denfry.promptcritical.mod.gas.PortHandler;
import dev.denfry.promptcritical.mod.grid.GridDevice;
import dev.denfry.promptcritical.mod.grid.GridDeviceBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.electronics.SiliconChain;
import dev.denfry.promptcritical.sim.grid.ConsumerUnit;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
 * Реактор Сименса {@code siemens_reactor} (11 §11.5.2): кремниевые стержни под колоколом греются током до 1100 °C,
 * трихлорсилан с водородом осаждает на них кремний 9N, HCl уходит трубой обратно в реактор трихлорсилана. Рецикл
 * непрореагировавшего SiHCl₃ — внутри установки: снаружи расходуется только то, что стало кремнием. Нагрузка сети
 * {@value #DEMAND_KW} кВт, {@link SiliconChain#SIEMENS_KWH_PER_KG} кВт·ч на кг — стержни излучают на холодный
 * колокол, это и есть главный расход энергии всей цепочки кремния.
 */
public final class SiemensReactorBlockEntity extends GridDeviceBlockEntity<ConsumerUnit> implements PanelHost {
    public static final int DEMAND_KW = 2000;
    public static final double BATCH_J = SiliconChain.SIEMENS_KWH_PER_KG * ElectronicsComponents.POLY_KG_PER_ITEM * SiliconChain.J_PER_KWH;
    static final double TCS_L = SiliconChain.tcsConsumedLitresPerKgPoly() * ElectronicsComponents.POLY_KG_PER_ITEM,
            H2_NM3 = SiliconChain.hydrogenNm3PerKgPoly() * ElectronicsComponents.POLY_KG_PER_ITEM,
            HCL_NM3 = SiliconChain.hclOutNm3PerKgPoly() * ElectronicsComponents.POLY_KG_PER_ITEM;

    private final FeedTank tcs = new FeedTank(PcFluids.TRICHLOROSILANE.source.get(), 100);
    private final FeedTank h2 = new FeedTank(PcFluids.HYDROGEN.source.get(), 100);
    private final ProductTank hcl = new ProductTank(PcFluids.HYDROGEN_CHLORIDE.source.get(), 100);
    private final IFluidHandler ports = new PortHandler(hcl, tcs, h2);
    private final ItemStackHandler out = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(PcItems.POLYSILICON.get());
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IItemHandler external = new ElectronicsBlocks.SlotsIo(out, 0, 0);
    private double energyJ;
    private boolean demandSet, running;

    public SiemensReactorBlockEntity(BlockPos pos, BlockState state) {
        super(ElectronicsBlocks.SIEMENS_REACTOR.get(), pos, state);
    }

    @Override
    protected ConsumerUnit unitOf(GridSubsystem grid) {
        return grid.consumer(simId());
    }

    @Override
    public List<List<GridDevice.Face>> terminals() {
        return List.of(GridDevice.allFaces(worldPosition));
    }

    private boolean fed() {
        return tcs.amount() >= TCS_L && h2.amount() >= H2_NM3 && hcl.room() >= HCL_NM3
                && out.insertItem(0, new ItemStack(PcItems.POLYSILICON.get()), true).isEmpty();
    }

    @Override
    protected void serverTick(ConsumerUnit u) {
        if (!demandSet) {
            u.setDemandKw(DEMAND_KW);
            demandSet = true;
        }
        running = u.energized() && fed();
        if (running) {
            energyJ += u.powerW() * 0.05;
            if (energyJ >= BATCH_J) {
                energyJ = 0;
                tcs.take(TCS_L);
                h2.take(H2_NM3);
                hcl.add(HCL_NM3);
                out.insertItem(0, new ItemStack(PcItems.POLYSILICON.get()), false);
            }
        }
        if (ticks % 10 == 0) setFlag(dev.denfry.promptcritical.mod.block.PcProperties.RUNNING, running);
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return ports;
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return external;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return false;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult use(Player player) {
        ItemStack s = out.extractItem(0, 64, false);
        if (!s.isEmpty() && !player.addItem(s)) player.drop(s, false);
        return InteractionResult.SUCCESS;
    }

    @Override
    public PanelLayout panelLayout() {
        return ElectronicsPanels.SIEMENS;
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
        return out;
    }

    @Override
    public void readPanel(int[] o) {
        Arrays.fill(o, 0);
        o[ElectronicsPanels.E_PROGRESS] = (int) Math.round(1000 * energyJ / BATCH_J);
        o[ElectronicsPanels.E_A] = (int) Math.round(TCS_L * 10);
        o[ElectronicsPanels.E_B] = (int) Math.round(H2_NM3 * 10);
        o[ElectronicsPanels.E_C] = (int) Math.round(HCL_NM3 * 10);
        ConsumerUnit u = unit();
        if (u == null) return;
        o[ElectronicsPanels.E_POWER_KW] = (int) Math.round(u.powerW() / 1e3);
        int f = 0;
        if (u.energized()) f |= 1 << ElectronicsPanels.EF_ENERGIZED;
        if (u.switchedOn()) f |= 1 << ElectronicsPanels.EF_SWITCH;
        if (running) f |= 1 << ElectronicsPanels.EF_RUNNING;
        if (!fed()) f |= 1 << ElectronicsPanels.EF_SHORT;
        o[ElectronicsPanels.E_FLAGS] = f;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        ConsumerUnit u = unit();
        if (u != null && id == ElectronicsPanels.BTN_SWITCH) u.setSwitchedOn(!u.switchedOn());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("out", out.serializeNBT(registries));
        tag.putDouble("energy_j", energyJ);
        tcs.save(tag, "tcs");
        h2.save(tag, "h2");
        hcl.save(tag, "hcl");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("out")) out.deserializeNBT(registries, tag.getCompound("out"));
        energyJ = tag.getDouble("energy_j");
        tcs.load(tag, "tcs");
        h2.load(tag, "h2");
        hcl.load(tag, "hcl");
    }
}

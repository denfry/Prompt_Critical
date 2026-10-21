package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.foundry.MachineInventory;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.metallurgy.BakingFurnace;
import dev.denfry.promptcritical.sim.metallurgy.BakingFurnaceUnit;
import dev.denfry.promptcritical.sim.metallurgy.ElectrodeState;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * Обжиговая печь электродов {@code baking_furnace} 3×2×3 (08 §8.4.5): садка из сырых электродов (вход щита или
 * воронка), газ — трубой, режим — уставка и скорость нагрева на щите; «Выгрузить» — садку в выходы (горячая
 * выгрузка трескает электроды). Обжиг 24 ч физ. выше 800 °C [BALANCE].
 */
public final class BakingFurnaceBlockEntity extends IronworksDeviceBlockEntity<BakingFurnaceUnit> implements PanelHost {
    private static final int OUT_SLOTS = 4;
    private final GasBuffer gas = new GasBuffer(8_000, GasBuffer::isProcessGas, this::setChanged);
    private final MachineInventory inv = new MachineInventory(roles(), (slot, s) -> slot == 0 && isGreen(s), this::setChanged);

    public BakingFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(IronworksBlockEntities.BAKING_FURNACE.get(), pos, state, BakingFurnaceUnit.class);
    }

    private static MachineInventory.Role[] roles() {
        MachineInventory.Role[] r = new MachineInventory.Role[1 + OUT_SLOTS];
        Arrays.fill(r, MachineInventory.Role.OUTPUT);
        r[0] = MachineInventory.Role.INPUT;
        return r;
    }

    static boolean isGreen(ItemStack s) {
        IronworksComponents.Electrode e = s.get(IronworksComponents.ELECTRODE_STATE.get());
        return s.is(PcItems.GREEN_ELECTRODE.get()) && e != null && e.stage() == ElectrodeState.GREEN;
    }

    @Override
    protected void serverTick(SimLevelBinding b, BakingFurnaceUnit u) {
        double dtp = Ironworks.tickPhysS(b);
        ProcessGas g = gas.gas();
        if (g != null) u.setGasSupply(g, gas.draw(u.gasDemandNm3S(g) * dtp) / dtp);
        else u.setGasSupply(null, 0);
        ItemStack in = inv.getStackInSlot(0);
        if (!in.isEmpty() && isGreen(in) && u.furnace().load(in.get(IronworksComponents.ELECTRODE_STATE.get()).toState())) in.shrink(1);
        setStateFlag(BlockStateProperties.LIT, u.gasSupplyLit());
    }

    /** Выгрузить садку в выходы; что не влезло — остаётся лежать рядом. */
    public void unload() {
        BakingFurnaceUnit u = unit();
        if (u == null) return;
        for (ElectrodeState e : u.furnace().unload()) {
            ItemStack s = new ItemStack(PcItems.GREEN_ELECTRODE.get());
            s.set(IronworksComponents.ELECTRODE_STATE.get(), IronworksComponents.Electrode.of(e));
            ItemStack rest = inv.output(s);
            if (!rest.isEmpty()) Ironworks.spill(level, worldPosition.above(2), rest);
        }
    }

    @Override
    protected void clientTick() {
        if (!stateFlag(BlockStateProperties.LIT)) return;
        MachineFx fx = fx();
        if (fx.chance(2)) fx.smoke(fx.at(8, 24, 8), false, 1.0);
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
    public boolean accepts(ItemStack stack) {
        return isGreen(stack);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        player.setItemInHand(hand, inv.insertFromPlayer(stack));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        if (inv.giveOutputs(player)) return InteractionResult.SUCCESS;
        BakingFurnaceUnit u = unit();
        if (u != null) player.displayClientMessage(IronworksText.msg("baking", IronworksText.num(u.furnace().tempK() - 273.15),
                u.furnace().loaded(), IronworksText.num(u.furnace().soakSeconds() / 3600)), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public PanelLayout panelLayout() {
        return IronworksPanels.BAKING;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return inv;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        BakingFurnaceUnit u = unit();
        if (u == null) return;
        BakingFurnace f = u.furnace();
        out[IronworksPanels.K_TEMP_C] = (int) Math.round(f.tempK() - 273.15);
        out[IronworksPanels.K_SET_C] = (int) Math.round(u.setpointK() - 273.15);
        out[IronworksPanels.K_RAMP_KH10] = (int) Math.round(u.rampKH() * 10);
        out[IronworksPanels.K_RATE_KH10] = (int) Math.round(f.heatingRateKPerH() * 10);
        out[IronworksPanels.K_LOADED] = f.loaded();
        out[IronworksPanels.K_SOAK_H10] = (int) Math.round(f.soakSeconds() / 360);
        out[IronworksPanels.K_GAS_MB] = gas.amount();
        int flags = 0;
        if (u.gasSupplyLit()) flags |= 1 << IronworksPanels.F_RUNNING;
        if (f.crackedRun()) flags |= 1 << IronworksPanels.F_ALARM;
        out[IronworksPanels.FLAGS] = flags;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        BakingFurnaceUnit u = unit();
        if (u == null) return;
        double set = u.setpointK(), ramp = u.rampKH();
        switch (id) {
            case IronworksPanels.BTN_SET_DOWN -> set -= 50;
            case IronworksPanels.BTN_SET_UP -> set += 50;
            case IronworksPanels.BTN_RAMP_DOWN -> ramp -= ramp > 10 ? 5 : 1;
            case IronworksPanels.BTN_RAMP_UP -> ramp += ramp >= 10 ? 5 : 1;
            case IronworksPanels.BTN_UNLOAD -> {
                unload();
                return;
            }
            default -> {
                return;
            }
        }
        u.setSetpoint(set, ramp);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("gas", gas.save(registries));
        tag.put("inv", inv.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("gas")) gas.load(registries, tag.getCompound("gas"));
        if (tag.contains("inv")) inv.load(registries, tag.getCompound("inv"));
    }
}

package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.metallurgy.HotBlastStove;
import dev.denfry.promptcritical.sim.metallurgy.HotBlastStoveUnit;
import dev.denfry.promptcritical.sim.metallurgy.ProcessGas;
import dev.denfry.promptcritical.sim.metallurgy.StoveMode;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Воздухонагреватель Каупера {@code hot_blast_stove} 3×9×3 (08 §8.5.2): «на газе» жжёт горючий газ из трубы по
 * клапану и греет насадку; «на дутье» отдаёт тепло дутью домны. Режим переключает домна (автоцикл) или щит.
 */
public final class HotBlastStoveBlockEntity extends IronworksDeviceBlockEntity<HotBlastStoveUnit> implements PanelHost {
    private final GasBuffer gas = new GasBuffer(16_000, GasBuffer::isProcessGas, this::setChanged);

    public HotBlastStoveBlockEntity(BlockPos pos, BlockState state) {
        super(IronworksBlockEntities.HOT_BLAST_STOVE.get(), pos, state, HotBlastStoveUnit.class);
    }

    @Override
    protected void serverTick(SimLevelBinding b, HotBlastStoveUnit u) {
        double dtp = Ironworks.tickPhysS(b);
        ProcessGas g = gas.gas();
        if (g != null) u.setGasSupply(g, gas.draw(u.gasDemandNm3S() * dtp) / dtp);
        else u.setGasSupply(null, 0);
    }

    @Override
    protected void clientTick() {
        MachineFx fx = fx();
        if (fx.chance(0.5)) fx.wisp(fx.at(8, 136, 8), 0.4);
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return gas.input();
    }

    @Override
    public InteractionResult use(Player player) {
        HotBlastStoveUnit u = unit();
        if (u != null) player.displayClientMessage(IronworksText.msg("stove", IronworksText.msg("stove_mode." + mode(u)),
                IronworksText.num(u.stove().domeK() - 273.15), IronworksText.num(u.gasSupplyNm3S() * 3600)), true);
        return InteractionResult.SUCCESS;
    }

    private static String mode(HotBlastStoveUnit u) {
        return u.stove().mode().name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public PanelLayout panelLayout() {
        return IronworksPanels.STOVE;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        HotBlastStoveUnit u = unit();
        if (u == null) return;
        HotBlastStove s = u.stove();
        out[IronworksPanels.H_DOME_C] = (int) Math.round(s.domeK() - 273.15);
        out[IronworksPanels.H_BOTTOM_C] = (int) Math.round(s.bottomK() - 273.15);
        out[IronworksPanels.H_BLAST_C] = s.mode() == StoveMode.ON_BLAST ? (int) Math.round(s.hotBlastK() - 273.15) : 0;
        out[IronworksPanels.H_MODE] = s.mode().ordinal();
        out[IronworksPanels.H_GAS_NM3H] = (int) Math.round(u.gasSupplyNm3S() * 3600);
        out[IronworksPanels.H_VALVE_NM3H] = (int) Math.round(u.valveNm3S() * 3600);
        out[IronworksPanels.H_GAS_MB] = gas.amount();
        int flags = 0;
        if (u.gasSupplyNm3S() > 0) flags |= 1 << IronworksPanels.F_RUNNING;
        if (s.domeOverTemp() || s.flameFailure()) flags |= 1 << IronworksPanels.F_ALARM;
        out[IronworksPanels.FLAGS] = flags;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        HotBlastStoveUnit u = unit();
        if (u == null) return;
        switch (id) {
            case IronworksPanels.BTN_DOWN -> u.setValve(u.valveNm3S() - 0.1);
            case IronworksPanels.BTN_UP -> u.setValve(u.valveNm3S() + 0.1);
            case IronworksPanels.BTN_MODE -> {
                StoveMode[] all = StoveMode.values();
                u.stove().setMode(all[(u.stove().mode().ordinal() + 1) % all.length]);
            }
            default -> {
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("gas", gas.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("gas")) gas.load(registries, tag.getCompound("gas"));
    }
}

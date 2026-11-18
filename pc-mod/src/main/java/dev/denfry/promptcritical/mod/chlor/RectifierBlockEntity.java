package dev.denfry.promptcritical.mod.chlor;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.grid.DcLoad;
import dev.denfry.promptcritical.mod.grid.GridDeviceBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.RectifierUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Выпрямительный агрегат электролиза ({@link RectifierUnit}) 2×2×1: ввод 6,3 кВ кабелем к любой грани, кроме
 * лицевой; постоянный ток — шинопроводом к ближайшей серии электролизёров ({@link DcLoad}: хлорной или фторной) в
 * {@value #REACH} блоках. Берёт из
 * сети мощность серии при заданном токе, делённую на КПД; при просадке напряжения снижает ток.
 */
public class RectifierBlockEntity extends GridDeviceBlockEntity<RectifierUnit> implements PanelHost {
    public static final int REACH = 6;

    private boolean noCells;
    @org.jetbrains.annotations.Nullable
    private BlockPos cellsPos;

    public RectifierBlockEntity(BlockPos pos, BlockState state) {
        super(ChlorBlockEntities.RECTIFIER.get(), pos, state);
    }

    @Override
    protected RectifierUnit unitOf(GridSubsystem grid) {
        return grid.rectifier(simId());
    }

    @Override
    public List<List<Face>> terminals() {
        List<Face> out = new ArrayList<>();
        for (Direction d : Direction.values()) if (d != facing()) out.add(new Face(worldPosition, d));
        return List.of(out);
    }

    @Override
    protected void serverTick(RectifierUnit u) {
        // серию ищем раз в секунду; между поисками — по запомненной позиции
        DcLoad cells = cellsPos != null && level.getBlockEntity(cellsPos) instanceof DcLoad c ? c : null;
        if (cells == null && ticks % 20 == 0) {
            cells = DcLoad.near(level, worldPosition, REACH);
            cellsPos = cells instanceof net.minecraft.world.level.block.entity.BlockEntity be ? be.getBlockPos() : null;
        }
        noCells = cells == null;
        double demand = cells == null ? 0 : cells.dcDemandW();
        u.setDcDemandW(demand);
        double fraction = u.currentFraction();
        // мощность выше паспортной — ток снижается пропорционально
        double cap = demand > RectifierUnit.RATED_DC_W ? RectifierUnit.RATED_DC_W / demand : 1;
        if (cells != null) cells.supply(cells.dcSetpointA() * fraction * cap);
        if (ticks % 10 == 0) {
            setFlag(PcProperties.RUNNING, u.powerW() > 1e3);
            setFlag(PcProperties.ALARM, u.switchedOn() && demand > 0 && fraction == 0);
        }
    }

    @Override
    public InteractionResult use(Player player) {
        RectifierUnit u = unit();
        if (u == null) return InteractionResult.PASS;
        u.setSwitchedOn(!u.switchedOn());
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.7f, 0.6f);
        player.displayClientMessage(ChlorText.msg(u.switchedOn() ? "rectifier.on" : "rectifier.off"), true);
        setChanged();
        return InteractionResult.SUCCESS;
    }

    @Override
    public PanelLayout panelLayout() {
        return ChlorPanels.RECTIFIER;
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
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        RectifierUnit u = unit();
        if (u == null) return;
        out[ChlorPanels.R_SUPPLY_X100] = (int) Math.round(u.supplyPu() * 100);
        out[ChlorPanels.R_KW] = (int) Math.round(u.powerW() / 1e3);
        out[ChlorPanels.R_DC_KW] = (int) Math.round(u.dcDemandW() * u.currentFraction() / 1e3);
        out[ChlorPanels.R_FLAGS] = (u.switchedOn() ? 1 << ChlorPanels.RF_ON : 0) | (noCells ? 1 << ChlorPanels.RF_NO_CELLS : 0)
                | (u.switchedOn() && u.dcDemandW() > 0 && u.currentFraction() < 1 ? 1 << ChlorPanels.RF_LOW : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == ChlorPanels.RB_BREAKER) use(player);
    }
}

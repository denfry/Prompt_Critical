package dev.denfry.promptcritical.mod.powerhouse;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.environment.RiverService;
import dev.denfry.promptcritical.mod.environment.WaterBodies;
import dev.denfry.promptcritical.mod.grid.GridDeviceBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.PumpedStorageUnit;
import dev.denfry.promptcritical.sim.hydraulics.PumpTurbine;
import dev.denfry.promptcritical.sim.hydraulics.PumpedStorage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Обратимый агрегат ГАЭС ({@link PumpedStorageUnit}) 3×2×3 у нижнего бассейна; кабель 6,3 кВ — к любой грани
 * верхнего блока, кроме фасада. Верхний бассейн — затвор водозабора ({@code dam_gate}) в воде не ниже
 * {@value #MIN_HEAD_M} м над агрегатом в пределах {@value #SEARCH} блоков, как у гидротурбины: статический напор — от
 * поверхности воды у затвора, длина водовода — расстояние до затвора, площадь бассейна — зеркало воды у затвора
 * ({@link WaterBodies#area}, до {@value #MAX_BASIN} м²). Уровень бассейна живёт в модели: вода в мире не меняется,
 * как у приливной станции. Агрегат подбирается так, чтобы бассейн срабатывался турбиной за 8 ч (как у Загорской ГАЭС),
 * но не больше {@value #MAX_POWER_W} Вт; рабочая сработка {@value #DRAWDOWN_M} м; водовод — 4 м/с при расчётном
 * расходе. Нижний бассейн — река или озеро у агрегата, его уровень не меняется.
 */
public class PumpedStorageBlockEntity extends GridDeviceBlockEntity<PumpedStorageUnit> implements PanelHost {
    static final int SEARCH = 64, RESCAN = 200, MAX_BASIN = 40_000;
    static final double MIN_HEAD_M = 10, DRAWDOWN_M = 5, MAX_POWER_W = 1e6, MIN_POWER_W = 20e3, HOURS = 8, PENSTOCK_V = 4;

    private int area;

    public PumpedStorageBlockEntity(BlockPos pos, BlockState state) {
        super(PowerhouseBlockEntities.PUMPED_STORAGE.get(), pos, state);
    }

    @Override
    protected PumpedStorageUnit unitOf(GridSubsystem grid) {
        return grid.pumpedStorage(simId());
    }

    @Override
    public List<List<Face>> terminals() {
        List<Face> out = new ArrayList<>();
        for (Direction d : Direction.values()) if (d != facing() && d != Direction.DOWN) out.add(new Face(worldPosition, d));
        return List.of(out);
    }

    @Override
    protected void serverTick(PumpedStorageUnit u) {
        if (ticks % RESCAN == 1) u.configure(spec((ServerLevel) level));
        if (ticks % 5 == 0) setFlag(PcProperties.RUNNING, u.plant() != null && u.plant().state() != PumpedStorage.State.STANDSTILL);
        if (ticks % 100 == 0) setChanged();
    }

    /** Паспорт по миру; {@code null} — нет верхнего бассейна. */
    @Nullable
    private PumpedStorage.Spec spec(ServerLevel sl) {
        BlockPos best = null;
        double bestHead = 0;
        for (BlockPos p : RiverService.gatesNear(sl, worldPosition, SEARCH)) {
            if (!sl.isLoaded(p) || !sl.getBlockState(p).is(PcBlocks.DAM_GATE.get())) continue;
            BlockPos s = WaterBodies.surface(sl, p);
            if (s == null) continue;
            double head = s.getY() + 1 - worldPosition.getY();
            if (head >= MIN_HEAD_M && head > bestHead) {
                bestHead = head;
                best = p.immutable();
            }
        }
        if (best == null) {
            area = 0;
            return null;
        }
        BlockPos surface = WaterBodies.surface(sl, best);
        area = WaterBodies.area(sl, surface, MAX_BASIN, null);
        if (area <= 0) return null;
        double volume = area * DRAWDOWN_M;
        double power = 0.9 * PumpTurbine.RHO * PumpTurbine.G * bestHead * volume / (HOURS * 3600);
        power = Math.max(MIN_POWER_W, Math.min(MAX_POWER_W, power));
        PumpTurbine unit = PumpTurbine.scaled(bestHead, power);
        double dia = Math.sqrt(4 * unit.turbineFlowM3s() / (Math.PI * PENSTOCK_V));
        double length = Math.max(bestHead, Math.sqrt(best.distSqr(worldPosition)));
        return new PumpedStorage.Spec(unit, bestHead - DRAWDOWN_M, DRAWDOWN_M, area, 0, length, dia);
    }

    @Override
    public InteractionResult use(Player player) {
        PumpedStorageUnit u = unit();
        if (u == null || u.plant() == null) {
            player.displayClientMessage(PowerhouseText.msg("psh.no_basin"), true);
            return InteractionResult.SUCCESS;
        }
        setMode(u, PumpedStorage.Mode.values()[(u.plant().mode().ordinal() + 1) % PumpedStorage.Mode.values().length], player);
        return InteractionResult.SUCCESS;
    }

    private void setMode(PumpedStorageUnit u, PumpedStorage.Mode m, Player player) {
        u.plant().setMode(m);
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, 0.8f);
        player.displayClientMessage(PowerhouseText.msg("psh.mode." + m.name().toLowerCase(java.util.Locale.ROOT)), true);
        setChanged();
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return StoragePanels.PSH;
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
        PumpedStorageUnit u = unit();
        if (u == null) return;
        out[StoragePanels.HZ100] = (int) Math.round(u.frequencyHz() * 100);
        out[StoragePanels.AREA] = area;
        PumpedStorage p = u.plant();
        if (p == null) return;
        double w = u.powerW();
        out[StoragePanels.KW10] = (int) Math.round(Math.abs(w) / 100);
        out[StoragePanels.FILL_PERMIL] = (int) Math.round(p.fill() * 1000);
        out[StoragePanels.HEAD_DM] = (int) Math.round(p.grossHeadM() * 10);
        out[StoragePanels.RATED_KW] = (int) Math.round(p.spec().unit().turbinePowerW() / 1e3);
        out[StoragePanels.SETPOINT] = (int) Math.round(p.turbineSetpoint() * 100);
        out[StoragePanels.MODE] = p.mode().ordinal();
        out[StoragePanels.KWH_IN] = (int) Math.round(p.energyInJ() / 3.6e6);
        out[StoragePanels.KWH_OUT] = (int) Math.round(p.energyOutJ() / 3.6e6);
        out[StoragePanels.STORED_KWH] = (int) Math.round(p.fill() * p.spec().storageJ() * 0.88 / 3.6e6);
        out[StoragePanels.FLAGS] = 1 << StoragePanels.F_READY | switch (p.state()) {
            case PUMPING -> 1 << StoragePanels.F_PUMP;
            case GENERATING -> 1 << StoragePanels.F_GEN;
            case STANDSTILL -> 0;
        };
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        PumpedStorageUnit u = unit();
        if (u == null || u.plant() == null) return;
        if (id < PumpedStorage.Mode.values().length) setMode(u, PumpedStorage.Mode.values()[id], player);
        else if (id == StoragePanels.BUTTON_LESS) u.plant().setTurbineSetpoint(u.plant().turbineSetpoint() - 0.1);
        else if (id == StoragePanels.BUTTON_MORE) u.plant().setTurbineSetpoint(u.plant().turbineSetpoint() + 0.1);
    }
}

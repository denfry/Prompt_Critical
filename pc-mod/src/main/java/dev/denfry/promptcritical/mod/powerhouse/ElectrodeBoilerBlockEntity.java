package dev.denfry.promptcritical.mod.powerhouse;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.boiler.FireTubeBoilerBlockEntity;
import dev.denfry.promptcritical.mod.grid.GridDeviceBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.grid.ElectrodeBoilerUnit;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.plant.SteamPlant;
import dev.denfry.promptcritical.sim.thermal.ElectrodeBoiler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Электродный котёл 6,3 кВ ({@link ElectrodeBoilerUnit}): ставится вплотную к котлу котельной (жаротрубному или
 * барабану, экрану, щиту водотрубного) — на общий паровой коллектор; его тепло каждый тик уходит в воду этого котла
 * ({@link SteamPlant#setElectricHeatW}). Кабель 6,3 кВ — к любой грани, кроме фасада. Нет котла рядом, котёл взорван
 * или стоит на предохранительном клапане — пар некуда отдать, электроды осушаются. Shift + ПКМ — следующий режим.
 */
public class ElectrodeBoilerBlockEntity extends GridDeviceBlockEntity<ElectrodeBoilerUnit> implements PanelHost {
    private SimId boiler = SimId.NONE;

    public ElectrodeBoilerBlockEntity(BlockPos pos, BlockState state) {
        super(PowerhouseBlockEntities.ELECTRODE_BOILER.get(), pos, state);
    }

    @Override
    protected ElectrodeBoilerUnit unitOf(GridSubsystem grid) {
        return grid.electrodeBoiler(simId());
    }

    @Override
    public List<List<Face>> terminals() {
        List<Face> out = new ArrayList<>();
        for (Direction d : Direction.values()) if (d != facing()) out.add(new Face(worldPosition, d));
        return List.of(out);
    }

    @Override
    protected void serverTick(ElectrodeBoilerUnit u) {
        ServerLevel sl = (ServerLevel) level;
        if (ticks % 20 == 1) boiler = findBoiler();
        SimLevelBinding b = SimBindings.get(sl);
        SteamPlant plant = b == null ? null : b.world().subsystem(SteamPlant.class);
        boolean sink = plant != null && !boiler.isNone() && plant.setElectricHeatW(boiler, u.heatW());
        u.setSteamSink(sink);
        if (ticks % 5 == 0) setFlag(PcProperties.RUNNING, u.powerW() > 1e3);
        if (u.powerW() > 50e3 && ticks % 10 == 0)
            sl.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1, worldPosition.getZ() + 0.5,
                    1 + (int) (u.powerW() / 300e3), 0.15, 0.05, 0.15, 0.02);
        if (ticks % 100 == 0) setChanged();
    }

    /** Котёл, которого электродный котёл касается гранью; {@link SimId#NONE} — нет. */
    private SimId findBoiler() {
        for (Direction d : Direction.values()) {
            BlockPos n = worldPosition.relative(d);
            FireTubeBoilerBlockEntity fire = FireTubeBoilerBlockEntity.at(level, n);
            if (fire != null && !fire.simId().isNone()) return fire.simId();
            BlockState s = level.getBlockState(n);
            if (s.is(PcBlocks.STEAM_DRUM.get()) || s.is(PcBlocks.BOILER_WALL.get()) || s.is(PcBlocks.WATER_TUBE_BOILER.get())) {
                WaterTubeBoilerBlockEntity wt = WaterTubeBoilerBlockEntity.of(level, n);
                if (wt != null && wt.shape() != null && !wt.simId().isNone()) return wt.simId();
            }
        }
        return SimId.NONE;
    }

    @Override
    public InteractionResult use(Player player) {
        ElectrodeBoilerUnit u = unit();
        if (u == null) return InteractionResult.PASS;
        setMode(u, ElectrodeBoiler.Mode.values()[(u.boiler().mode().ordinal() + 1) % ElectrodeBoiler.Mode.values().length], player);
        return InteractionResult.SUCCESS;
    }

    private void setMode(ElectrodeBoilerUnit u, ElectrodeBoiler.Mode m, Player player) {
        u.boiler().setMode(m);
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, 0.8f);
        player.displayClientMessage(PowerhouseText.msg("electrode.mode." + m.name().toLowerCase(java.util.Locale.ROOT)), true);
        setChanged();
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return StoragePanels.ELECTRODE;
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
        ElectrodeBoilerUnit u = unit();
        if (u == null) return;
        out[StoragePanels.HZ100] = (int) Math.round(u.frequencyHz() * 100);
        out[StoragePanels.KW10] = (int) Math.round(u.powerW() / 100);
        out[StoragePanels.FILL_PERMIL] = (int) Math.round(u.boiler().level() * 1000);
        out[StoragePanels.SETPOINT] = (int) Math.round(u.boiler().manualSetpoint() * 100);
        out[StoragePanels.MODE] = u.boiler().mode().ordinal();
        out[StoragePanels.RATED_KW] = (int) Math.round(ElectrodeBoilerUnit.RATED_W / 1e3);
        out[StoragePanels.FLAGS] = (u.steamSink() ? 1 << StoragePanels.F_READY : 0) | (u.powerW() > 1e3 ? 1 << StoragePanels.F_GEN : 0)
                | (u.frequencyHz() > 0 ? 1 << StoragePanels.F_PUMP : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        ElectrodeBoilerUnit u = unit();
        if (u == null) return;
        if (id < ElectrodeBoiler.Mode.values().length) setMode(u, ElectrodeBoiler.Mode.values()[id], player);
        else if (id == StoragePanels.BUTTON_LESS) u.boiler().setManualSetpoint(u.boiler().manualSetpoint() - 0.1);
        else if (id == StoragePanels.BUTTON_MORE) u.boiler().setManualSetpoint(u.boiler().manualSetpoint() + 0.1);
    }
}

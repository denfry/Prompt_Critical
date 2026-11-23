package dev.denfry.promptcritical.mod.reactor;

import dev.denfry.promptcritical.mod.binding.DeviceHost;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.ironworks.IronworksBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.core.DeviceMode;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.reactor.GraphitePile;
import dev.denfry.promptcritical.sim.reactor.PileLayout;
import dev.denfry.promptcritical.sim.reactor.PileUnit;
import dev.denfry.promptcritical.sim.reactor.ReactorSubsystem;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Пульт графитового котла {@code reactor_controller}: устройство мира с двойником {@link PileUnit}. Раз в секунду
 * обходит кладку, прижатую к пульту ({@link PileScanner}), и отдаёт её модели; щит — приборы CP-1 (счётчик BF₃,
 * ионизационная камера, период), привод регулирующих, взвод и сброс аварийных стержней. Защита и блокировки — в
 * модели, щит лишь подаёт команды.
 */
public final class PileControllerBlockEntity extends IronworksBlockEntity implements dev.denfry.promptcritical.mod.environment.SelfEmittingSource, DeviceHost, PanelHost {
    private static final int SCAN_PERIOD = 20, FIRE_PERIOD = 40;

    private SimId simId = SimId.NONE;
    private boolean chunkUnloading, tooBig;
    private List<BlockPos> top = List.of();
    private int ticks;

    public PileControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ReactorBlockEntities.REACTOR_CONTROLLER.get(), pos, state);
    }

    @Nullable
    public PileUnit unit() {
        return Reactor.unit(Ironworks.binding(level), simId);
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        PileUnit u = Reactor.unit(b, simId);
        if (u == null) return;
        if (ticks++ % SCAN_PERIOD == 0) rescan(u);
        GraphitePile p = u.pile();
        if (ticks % FIRE_PERIOD == 0 && !top.isEmpty()) consequences(p);
        if (ticks % 20 == 0) {
            radiate(p);
            irradiate(p);
        }
    }

    private double[] fluxBuf = new double[0];

    /**
     * Облучение раз в секунду (72 с физики, 12 §12.1): поток узлов из нодального решения — каналам облучения, деления
     * узлов — кассетам блочков. Плотность мощности для γ-нагрева капсулы — средняя по топливным узлам.
     */
    private void irradiate(GraphitePile p) {
        if (!(level instanceof ServerLevel sl)) return;
        PileLayout l = p.layout();
        int n = l.nodeCount();
        if (n == 0) return;
        if (fluxBuf.length < n) fluxBuf = new double[n];
        double[] flux = p.nodeFluxes(fluxBuf);
        long now = sl.getGameTime();
        double dt = 20 * dev.denfry.promptcritical.mod.environment.ActivityData.GAME_S_PER_TICK;
        double q = l.fuelNodes() > 0 ? p.powerW() / (l.fuelNodes() * GraphitePile.NODE_CM3) : 0;
        for (int i = 0; i < n; i++) {
            PileLayout.NodeKind k = l.kind(i);
            if (k != PileLayout.NodeKind.FUEL && k != PileLayout.NodeKind.GRAPHITE) continue;
            BlockPos pos = new BlockPos(l.x(i), l.y(i), l.z(i));
            if (!sl.isLoaded(pos)) continue;
            var be = sl.getBlockEntity(pos);
            if (be instanceof FuelChannelBlockEntity f) f.irradiate(p.nodeFissionRate(flux, i), dt, now);
            else if (be instanceof dev.denfry.promptcritical.mod.isotopes.IrradiationChannelBlockEntity c)
                c.push(flux[i], p.graphiteTempK(), q, now);
        }
    }

    /** Обойти кладку и отдать её модели (одинаковая подпись — без пересчёта). */
    public void rescan(PileUnit u) {
        PileScanner.Scan s = PileScanner.scan(level, worldPosition);
        tooBig = s.truncated();
        top = s.top();
        u.form(s.layout());
    }

    /**
     * Излучение котла: точечный источник в центре загрузки (центр тяжести урана по каналам) — γ и нейтроны деления
     * при работе, γ остаточного тепловыделения после останова. Графит кладки и стены между зоной и игроком ослабляют
     * его в {@link dev.denfry.promptcritical.mod.environment.RadiationService}.
     */
    private void radiate(GraphitePile p) {
        if (!(level instanceof ServerLevel sl)) return;
        var l = p.layout();
        double w = 0, x = 0, y = 0, z = 0;
        for (int i = 0; i < l.nodeCount(); i++) {
            int load = l.load(i);
            if (load <= 0) continue;
            w += load;
            x += load * (l.x(i) + 0.5);
            y += load * (l.y(i) + 0.5);
            z += load * (l.z(i) + 0.5);
        }
        if (w <= 0) return;
        dev.denfry.promptcritical.mod.environment.RadiationService.source(sl, new net.minecraft.world.phys.Vec3(x / w, y / w, z / w),
                dev.denfry.promptcritical.sim.radiation.FissionSource.reactor(p.powerW(), p.decayHeatW()));
    }

    /** Разрушенные блочки дымят над кладкой; горящий графит поджигает воздух над верхом. */
    private void consequences(GraphitePile p) {
        if (!(level instanceof ServerLevel sl)) return;
        BlockPos at = top.get(sl.random.nextInt(top.size())).above();
        if (p.cladFailed() || p.graphiteFire() || p.wignerRunaway()) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, at.getX() + 0.5, at.getY() + 0.2, at.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.01);
        }
        if (p.graphiteFire() && p.graphiteTempK() > dev.denfry.promptcritical.sim.reactor.PileConfig.defaults().graphiteFireK()
                && sl.isEmptyBlock(at)) {
            BlockState fire = BaseFireBlock.getState(sl, at);
            if (fire.canSurvive(sl, at)) sl.setBlockAndUpdate(at, fire);
        }
    }

    @Override
    public InteractionResult use(Player player) {
        PileUnit u = unit();
        if (u != null) {
            GraphitePile p = u.pile();
            player.displayClientMessage(ReactorPanels.MSG_CHANNEL.get(p.layout().totalLoad(),
                    p.layout().fuelNodes() * PileLayout.FULL_LOAD), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public IItemHandler items(@Nullable net.minecraft.core.Direction side) {
        return null;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return false;
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return ReactorPanels.PILE;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        PileUnit u = unit();
        if (u == null) return;
        GraphitePile p = u.pile();
        PileLayout l = p.layout();
        double powerW = p.powerW();
        out[ReactorPanels.P_POWER_LOG100] = ReactorPanels.log100(powerW);
        // шкала мощности: 0 — 1 мВт, 90 — 1 МВт (по 10 на декаду)
        out[ReactorPanels.P_POWER_DIAL] = powerW > 0 ? (int) Math.round(Math.max(0, Math.min(90, 10 * (Math.log10(powerW) + 3)))) : 0;
        double cps = p.detectorCount() > 0 ? p.countRate(0) : 0;
        out[ReactorPanels.P_CPS_LOG100] = p.detectorCount() > 0 ? ReactorPanels.log100(Math.max(cps, 0.01)) : ReactorPanels.NONE;
        out[ReactorPanels.P_CPS_DIAL] = cps > 0 ? (int) Math.round(Math.max(0, Math.min(60, 10 * Math.log10(Math.max(cps, 1))))) : 0;
        double t = p.periodS();
        out[ReactorPanels.P_PERIOD10] = Double.isFinite(t) ? (int) Math.round(Math.max(-99_999, Math.min(99_999, t * 10)))
                : ReactorPanels.PERIOD_INF;
        out[ReactorPanels.P_GRAPHITE_C10] = (int) Math.round((p.graphiteTempK() - 273.15) * 10);
        out[ReactorPanels.P_FUEL_C10] = (int) Math.round((p.fuelTempK() - 273.15) * 10);
        out[ReactorPanels.P_CONTROL_PCT] = pct(p.controlInsertion());
        out[ReactorPanels.P_ZIP_PCT] = pct(p.zipInsertion());
        out[ReactorPanels.P_TRIP] = p.trip().ordinal();
        out[ReactorPanels.P_NODES] = l.nodeCount();
        int fuelBlocks = 0;
        for (int i = 0; i < l.nodeCount(); i++) if (l.kind(i) == PileLayout.NodeKind.FUEL) fuelBlocks++;
        out[ReactorPanels.P_FUEL_NODES] = fuelBlocks;
        out[ReactorPanels.P_LOAD] = l.totalLoad();
        out[ReactorPanels.P_CONTROL_RODS] = p.controlCount();
        out[ReactorPanels.P_ZIP_RODS] = p.zipCount();
        out[ReactorPanels.P_DETECTORS] = l.detectorCount();
        out[ReactorPanels.P_SOURCES] = l.sources();
        out[ReactorPanels.P_DRIVE] = p.controlDrive();
        int f = 0;
        if (p.tripped()) f |= 1 << ReactorPanels.PF_TRIPPED;
        if (p.zipCocked()) f |= 1 << ReactorPanels.PF_COCKED;
        if (p.winding()) f |= 1 << ReactorPanels.PF_WINDING;
        if (p.controlCount() > 0 && (p.tripped() || !p.zipCocked())) f |= 1 << ReactorPanels.PF_INTERLOCK;
        if (p.cladFailed()) f |= 1 << ReactorPanels.PF_CLAD;
        if (p.graphiteFire()) f |= 1 << ReactorPanels.PF_FIRE;
        if (tooBig) f |= 1 << ReactorPanels.PF_TOO_BIG;
        if (l.nodeCount() > 0) f |= 1 << ReactorPanels.PF_FORMED;
        if (p.wignerRunaway()) f |= 1 << ReactorPanels.PF_WIGNER_RUNAWAY;
        if (p.dampersClosed()) f |= 1 << ReactorPanels.PF_DAMPERS;
        out[ReactorPanels.P_FLAGS] = f;
        out[ReactorPanels.P_WIGNER_JG10] = (int) Math.round(p.wignerStoredJg() * 10);
        out[ReactorPanels.P_WIGNER_KW10] = (int) Math.round(Math.min(1e8, p.wignerPowerW() / 100));
    }

    private static int pct(double x) {
        return Double.isNaN(x) ? 0 : (int) Math.round(x * 100);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        PileUnit u = unit();
        if (u == null) return;
        GraphitePile p = u.pile();
        switch (id) {
            case ReactorPanels.BTN_DOWN -> p.driveControlRods(-1);
            case ReactorPanels.BTN_STOP -> p.driveControlRods(0);
            case ReactorPanels.BTN_UP -> {
                p.driveControlRods(1);
                if (p.tripped() || !p.zipCocked()) player.displayClientMessage(ReactorPanels.MSG_INTERLOCK.get(), true);
            }
            case ReactorPanels.BTN_WIND -> {
                if (!p.windZip() && p.tripped()) player.displayClientMessage(ReactorPanels.MSG_WIND_REFUSED.get(), true);
            }
            case ReactorPanels.BTN_SCRAM -> u.scram();
            case ReactorPanels.BTN_RESET -> {
                if (!p.resetTrip()) player.displayClientMessage(ReactorPanels.MSG_RESET_REFUSED.get(), true);
            }
            case ReactorPanels.BTN_DAMPERS -> p.setDampersClosed(!p.dampersClosed());
            default -> {
            }
        }
    }

    // --- устройство мира ---

    @Override
    public SimId simId() {
        return simId;
    }

    @Override
    public void bindSimId(SimId id) {
        if (!id.equals(simId)) {
            simId = id;
            setChanged();
        }
    }

    @Override
    public ServerLevel level() {
        return (ServerLevel) level;
    }

    @Override
    public BlockPos pos() {
        return worldPosition;
    }

    @Override
    public String deviceType() {
        return BuiltInRegistries.BLOCK.getKey(getBlockState().getBlock()).toString();
    }

    @Override
    public DeviceMode mode() {
        SimLevelBinding b = Ironworks.binding(level);
        if (b == null || simId.isNone() || !b.world().topology().exists(simId)) return DeviceMode.LOCAL;
        return b.world().topology().deviceMode(simId);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        chunkUnloading = false;
        ticks = 0;
        SimLevelBinding b = Ironworks.binding(level);
        if (b != null) b.attach(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        chunkUnloading = true;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        SimLevelBinding b = Ironworks.binding(level);
        if (b != null) b.detach(this, !chunkUnloading);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("sim_id", simId.raw());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        simId = new SimId(tag.getLong("sim_id"));
    }

    /** Тип устройства пульта в подсистеме реакторов. */
    static boolean isPileController(String deviceType) {
        return ReactorSubsystem.PILE_CONTROLLER.equals(deviceType);
    }
}

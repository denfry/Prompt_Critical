package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.grid.FeInterface;
import dev.denfry.promptcritical.sim.grid.FeInterfaceUnit;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * {@code grid_fe_interface} (07 §7.7.1, R18): шкаф преобразователя между шиной сети и FE-сетью другого мода. Кабель —
 * к любой грани, кроме фасада; FE — через capability {@link IEnergyStorage} с любой грани. В режиме импорта шкаф сам
 * отдаёт FE соседям из буфера, в экспорте принимает FE от соседей; курс — {@link FeEnergy#jPerFe()} (паритет R56).
 * Три варианта по номиналу: 100 кВт на 0,4 кВ, 1 и 10 МВт на 6,3 кВ.
 */
public class FeInterfaceBlockEntity extends GridDeviceBlockEntity<FeInterfaceUnit> implements PanelHost {
    public static final int SMALL = 0, STANDARD = 1, LARGE = 2;
    /** Перечитывать ключи конфигурации раз в столько тиков. */
    static final int RESPEC = 100;
    /** Шаг уставки на щите, доля номинала. */
    static final double SET_STEP = 0.1;

    private final int variant;
    private final Storage storage = new Storage();

    public FeInterfaceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int variant) {
        super(type, pos, state);
        this.variant = variant;
    }

    public static FeInterfaceBlockEntity small(BlockPos pos, BlockState state) {
        return new FeInterfaceBlockEntity(GridBlockEntities.FE_INTERFACE_SMALL.get(), pos, state, SMALL);
    }

    public static FeInterfaceBlockEntity standard(BlockPos pos, BlockState state) {
        return new FeInterfaceBlockEntity(GridBlockEntities.FE_INTERFACE.get(), pos, state, STANDARD);
    }

    public static FeInterfaceBlockEntity large(BlockPos pos, BlockState state) {
        return new FeInterfaceBlockEntity(GridBlockEntities.FE_INTERFACE_LARGE.get(), pos, state, LARGE);
    }

    @Override
    protected FeInterfaceUnit unitOf(GridSubsystem grid) {
        FeInterfaceUnit u = grid.feInterface(simId());
        if (u.iface() == null) u.configure(FeEnergy.spec(variant));
        return u;
    }

    @Override
    public List<List<Face>> terminals() {
        List<Face> out = new ArrayList<>();
        for (Direction d : Direction.values()) if (d != facing()) out.add(new Face(worldPosition, d));
        return List.of(out);
    }

    /** FE-склад шкафа для capability; на клиенте и без двойника — пустой. */
    public IEnergyStorage energy() {
        return storage;
    }

    @Override
    protected void serverTick(FeInterfaceUnit u) {
        if (ticks % RESPEC == 0 || u.iface() == null) u.configure(FeEnergy.spec(variant));
        FeInterface f = u.iface();
        if (f.mode() != FeInterface.Mode.EXPORT) pushToNeighbours(f);
        if (ticks % 10 == 0) setFlag(PcProperties.RUNNING, f.connected() && Math.abs(f.gridW()) > 1);
    }

    /** Импорт: раздать FE соседям из буфера, не быстрее номинала. */
    private void pushToNeighbours(FeInterface f) {
        double j = FeEnergy.jPerFe();
        int perTick = (int) Math.min(Integer.MAX_VALUE, f.spec().ratedW() * f.spec().efficiency() / (j * 20));
        for (Direction d : Direction.values()) {
            if (perTick <= 0) return;
            IEnergyStorage other = level.getCapability(Capabilities.EnergyStorage.BLOCK, worldPosition.relative(d), d.getOpposite());
            if (other == null || other == storage || !other.canReceive()) continue;
            int have = (int) Math.min(perTick, f.extract(Double.MAX_VALUE, true) / j);
            if (have <= 0) return;
            int took = other.receiveEnergy(have, false);
            if (took > 0) {
                f.extract(took * j, false);
                perTick -= took;
            }
        }
    }

    private void setMode(FeInterface.Mode m) {
        FeInterfaceUnit u = unit();
        if (u == null || u.iface() == null) return;
        u.iface().setMode(m);
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, 0.8f);
        setChanged();
    }

    private void nudge(double share) {
        FeInterfaceUnit u = unit();
        if (u == null || u.iface() == null) return;
        FeInterface f = u.iface();
        f.setSetpointW(f.setpointW() + share * f.spec().ratedW());
        setChanged();
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case FePanels.BUTTON_IMPORT -> setMode(FeInterface.Mode.IMPORT);
            case FePanels.BUTTON_EXPORT -> setMode(FeInterface.Mode.EXPORT);
            case FePanels.BUTTON_AUTO -> setMode(FeInterface.Mode.AUTO);
            case FePanels.BUTTON_DOWN -> nudge(-SET_STEP);
            case FePanels.BUTTON_UP -> nudge(SET_STEP);
            default -> { }
        }
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return FePanels.FE_INTERFACE;
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
        double j = FeEnergy.jPerFe();
        out[FePanels.J_PER_FE] = (int) Math.round(j * 10);
        out[FePanels.RATED_KW] = (int) Math.round(FeEnergy.ratedW(variant) / 1000);
        FeInterfaceUnit u = unit();
        if (u == null || u.iface() == null) return;
        FeInterface f = u.iface();
        double p = f.gridW();
        out[FePanels.MODE] = f.mode().ordinal();
        out[FePanels.SET_PCT] = (int) Math.round(100 * f.setpointW() / f.spec().ratedW());
        out[FePanels.P_KW10] = (int) Math.round(p / 100);
        out[FePanels.FE_T] = (int) Math.round(Math.abs(p) * (p >= 0 ? f.spec().efficiency() : 1 / f.spec().efficiency()) / (j * 20));
        out[FePanels.FILL_PCT] = (int) Math.round(100 * f.fill());
        out[FePanels.HZ100] = (int) Math.round(u.frequencyHz() * 100);
        out[FePanels.V_PCT] = (int) Math.round(u.voltagePu() * 100);
        int flags = f.connected() ? 1 << FePanels.F_ON : 0;
        if (j != ServerConfig.J_PER_FE.get()) flags |= 1 << FePanels.F_PARITY;
        out[FePanels.FLAGS] = flags;
    }

    /** FE-сторона шкафа: буфер двойника в FE по курсу границы. */
    private final class Storage implements IEnergyStorage {
        @Nullable
        private FeInterface iface() {
            if (level == null || level.isClientSide) return null;
            FeInterfaceUnit u = unit();
            return u == null ? null : u.iface();
        }

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            FeInterface f = iface();
            if (f == null || toReceive <= 0) return 0;
            double j = FeEnergy.jPerFe();
            return (int) Math.floor(f.receive(toReceive * j, simulate) / j);
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            FeInterface f = iface();
            if (f == null || toExtract <= 0) return 0;
            double j = FeEnergy.jPerFe();
            int fe = (int) Math.min(toExtract, Math.floor(f.extract(Double.MAX_VALUE, true) / j));
            if (!simulate && fe > 0) f.extract(fe * j, false);
            return fe;
        }

        @Override
        public int getEnergyStored() {
            FeInterface f = iface();
            return f == null ? 0 : (int) Math.min(Integer.MAX_VALUE, f.bufferJ() / FeEnergy.jPerFe());
        }

        @Override
        public int getMaxEnergyStored() {
            FeInterface f = iface();
            return f == null ? 0 : (int) Math.min(Integer.MAX_VALUE, f.spec().bufferJ() / FeEnergy.jPerFe());
        }

        @Override
        public boolean canExtract() {
            FeInterface f = iface();
            return f != null && f.mode() != FeInterface.Mode.EXPORT;
        }

        @Override
        public boolean canReceive() {
            FeInterface f = iface();
            return f != null && f.mode() != FeInterface.Mode.IMPORT;
        }
    }
}

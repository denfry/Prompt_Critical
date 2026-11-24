package dev.denfry.promptcritical.mod.isotopes;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.content.SimAchievements;
import dev.denfry.promptcritical.mod.ironworks.IronworksBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.reactor.TrigaCore;
import java.util.Arrays;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Импульсный реактор TRIGA {@code transient_rod_drive} (12 §12.6.1): привод импульсного стержня на мосту над
 * бассейном, под ним — зона из твэлов U-ZrH ({@link TrigaCore}). Зона работает только под водой: под мостом нужен
 * столб воды не меньше {@value #POOL_DEPTH} блоков (бассейн TRIGA — 6–7 м, вода — и теплоноситель, и защита).
 * Импульс ≥ 100 МВт без повреждения топлива — достижение «Импульс» (11).
 */
public final class TrigaBlockEntity extends IronworksBlockEntity implements PanelHost {
    /** Глубина воды под мостом, блоков [BALANCE]. */
    public static final int POOL_DEPTH = 5;
    /** Порог «Импульса», Вт (11 §11.2.2). */
    public static final double PULSE_ACHIEVEMENT_W = 100e6;
    private static final double DT = 0.05;

    private final TrigaCore core = new TrigaCore();
    private boolean pool;
    private int ticks;

    public TrigaBlockEntity(BlockPos pos, BlockState state) {
        super(IsotopeBlockEntities.TRIGA.get(), pos, state);
    }

    public TrigaCore core() {
        return core;
    }

    /** Под мостом бассейн нужной глубины. */
    public boolean pool() {
        return pool;
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        if (!(level instanceof ServerLevel sl)) return;
        if (ticks++ % 20 == 0) pool = checkPool(sl);
        if (!pool && !core.scrammed()) core.scram(events(sl));
        core.step(DT, events(sl));
        dev.denfry.promptcritical.mod.station.Stations.tripState(sl, worldPosition, core.scrammed(), "SCRAM"); // журнал станции 04d §4d.8.2
        BlockState s = getBlockState();
        boolean armed = core.armed(), fired = core.transientInsertion() < 1;
        if (s.hasProperty(BlockStateProperties.ENABLED) && s.hasProperty(BlockStateProperties.TRIGGERED)
                && (s.getValue(BlockStateProperties.ENABLED) != armed || s.getValue(BlockStateProperties.TRIGGERED) != fired))
            level.setBlock(worldPosition, s.setValue(BlockStateProperties.ENABLED, armed).setValue(BlockStateProperties.TRIGGERED, fired),
                    Block.UPDATE_CLIENTS);
        if (ticks % 200 == 0) setChanged();
    }

    private boolean checkPool(ServerLevel sl) {
        for (int i = 1; i <= POOL_DEPTH; i++)
            if (!sl.getFluidState(worldPosition.below(i)).is(FluidTags.WATER)) return false;
        return true;
    }

    private TrigaCore.Events events(ServerLevel sl) {
        BlockPos pos = worldPosition;
        return new TrigaCore.Events() {
            @Override
            public void scram(String reason, double powerW) {
                int i = switch (reason) {
                    case "short_period" -> 1;
                    case "high_power" -> 2;
                    case "pulse_timer" -> 3;
                    default -> 0;
                };
                tell(sl, TrigaPanels.MSG_SCRAM.get(TrigaPanels.REASONS[i].get()), ChatFormatting.YELLOW);
            }

            @Override
            public void fuelDamaged(double fuelK) {
                setChanged();
                tell(sl, TrigaPanels.MSG_FUEL.get(Math.round(fuelK - 273.15)), ChatFormatting.RED);
            }

            @Override
            public void pulseCompleted(double peakW, double energyJ, double fwhmS, double peakFuelK, boolean fuelDamaged) {
                setChanged();
                tell(sl, TrigaPanels.MSG_PULSE.get(Math.round(peakW / 1e6), String.format(java.util.Locale.ROOT, "%.1f", energyJ / 1e6),
                        String.format(java.util.Locale.ROOT, "%.1f", fwhmS * 1e3), Math.round(peakFuelK - 273.15)), ChatFormatting.AQUA);
                if (peakW >= PULSE_ACHIEVEMENT_W && !fuelDamaged)
                    SimAchievements.nearby(sl, pos, SimAchievements.PULSE, "peak_mw", peakW / 1e6);
            }
        };
    }

    private void tell(ServerLevel sl, Component msg, ChatFormatting color) {
        for (ServerPlayer p : sl.players())
            if (p.blockPosition().closerThan(worldPosition, 32)) p.displayClientMessage(msg.copy().withStyle(color), false);
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return TrigaPanels.TRIGA;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        double p = core.powerW();
        out[TrigaPanels.T_POWER_LOG100] = TrigaPanels.log100(p);
        out[TrigaPanels.T_POWER_DIAL] = p > 0 ? (int) Math.round(Math.max(0, Math.min(120, 10 * (Math.log10(p) + 3)))) : 0;
        out[TrigaPanels.T_FUEL_C] = (int) Math.round(core.fuelK() - 273.15);
        out[TrigaPanels.T_CONTROL_PCT] = (int) Math.round(core.controlInsertion() * 100);
        out[TrigaPanels.T_TRANSIENT_PCT] = (int) Math.round(core.transientInsertion() * 100);
        out[TrigaPanels.T_PULSE_CENTS] = (int) Math.round(core.pulseReactivity() / TrigaCore.BETA * 100);
        double t = core.periodS();
        out[TrigaPanels.T_PERIOD10] = Double.isFinite(t) ? (int) Math.round(Math.min(99_999, t * 10)) : TrigaPanels.PERIOD_INF;
        double peak = core.pulsePeakW();
        out[TrigaPanels.T_PEAK_LOG100] = peak > 0 ? TrigaPanels.log100(peak) : TrigaPanels.NONE;
        out[TrigaPanels.T_ENERGY_KJ] = (int) Math.round(Math.min(2e9, core.pulseEnergyJ() / 1e3));
        out[TrigaPanels.T_FWHM_MS10] = (int) Math.round(core.pulseFwhmS() * 1e4);
        out[TrigaPanels.T_PEAK_FUEL_C] = peak > 0 ? (int) Math.round(core.pulsePeakFuelK() - 273.15) : 0;
        int f = 0;
        if (core.scrammed()) f |= 1 << TrigaPanels.TF_SCRAM;
        if (core.pulseMode()) f |= 1 << TrigaPanels.TF_PULSE_MODE;
        if (core.armed()) f |= 1 << TrigaPanels.TF_ARMED;
        if (core.pulsing()) f |= 1 << TrigaPanels.TF_PULSING;
        if (core.fuelDamaged()) f |= 1 << TrigaPanels.TF_FUEL_FAILED;
        if (!pool) f |= 1 << TrigaPanels.TF_NO_POOL;
        out[TrigaPanels.T_FLAGS] = f;
    }

    /** Сброс АЗ и байпас — право {@code PROTECTION} станции (04d §4d.9.2). */
    @Override
    public dev.denfry.promptcritical.sim.journal.Permission requiredPermission(int id) {
        return id == TrigaPanels.BTN_RESET ? dev.denfry.promptcritical.sim.journal.Permission.PROTECTION : dev.denfry.promptcritical.sim.journal.Permission.CONTROL;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (!(level instanceof ServerLevel sl)) return;
        switch (id) {
            case TrigaPanels.BTN_DOWN -> core.drive(-1);
            case TrigaPanels.BTN_STOP -> core.drive(0);
            case TrigaPanels.BTN_UP -> core.drive(1);
            case TrigaPanels.BTN_SCRAM -> core.scram(events(sl));
            case TrigaPanels.BTN_RESET -> {
                if (!pool || core.fuelDamaged() || !core.reset()) player.displayClientMessage(TrigaPanels.MSG_RESET_REFUSED.get(), true);
            }
            case TrigaPanels.BTN_MODE -> {
                if (!core.setPulseMode(!core.pulseMode())) player.displayClientMessage(TrigaPanels.MSG_PULSE_REFUSED.get(), true);
            }
            case TrigaPanels.BTN_LESS -> core.setPulseReactivity(core.pulseReactivity() - TrigaPanels.CENTS_STEP / 100.0 * TrigaCore.BETA);
            case TrigaPanels.BTN_MORE -> core.setPulseReactivity(core.pulseReactivity() + TrigaPanels.CENTS_STEP / 100.0 * TrigaCore.BETA);
            case TrigaPanels.BTN_ARM -> {
                if (!core.arm()) player.displayClientMessage(TrigaPanels.MSG_ARM_REFUSED.get(), true);
            }
            case TrigaPanels.BTN_FIRE -> core.fire();
            default -> {
            }
        }
        setChanged();
    }

    // --- руки ---

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return false;
    }

    @Override
    public InteractionResult use(Player player) {
        return InteractionResult.PASS;
    }

    // --- сохранение ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        MutableStateTree tree = new MutableStateTree();
        core.save(new TreeStateWriter(tree));
        tag.put("core", NbtStateTrees.toNbt(tree));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("core")) core.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("core"))));
    }
}

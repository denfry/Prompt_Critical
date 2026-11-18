package dev.denfry.promptcritical.mod.drilling;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.drilling.WellLogging;
import dev.denfry.promptcritical.sim.drilling.Well;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.SplittableRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Каротажный подъёмник (09 §9.2.5, {@link WellLogging}): зонды — гамма, нейтронный (с закрытым источником Am-Be),
 * электрический — вставляют рукой; кнопка «Каротаж» спускает их в открытый ствол буровой ({@link DrillersConsoleBlockEntity})
 * от башмака до забоя и пишет кривые при подъёме со скоростью 0,15 м/с в медленном времени. Каротаж — при
 * остановленных насосах и открытом превенторе [допущение: подъём бурильной колонны не моделируется]. Прихват зонда
 * (вероятность §9.2.5) — зонды остаются в стволе; источник нейтронного зонда остаётся там же [допущение: ловильные
 * работы, цементный мост и учёт источника в 04b не сделаны]. Барабан крутится без вала Create [допущение].
 */
public final class LoggingUnitBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    public static final int POINTS = 24;
    private final EnumMap<WellLogging.Curve, Boolean> sondes = new EnumMap<>(WellLogging.Curve.class);
    private final Map<WellLogging.Curve, double[]> curves = new EnumMap<>(WellLogging.Curve.class);
    private double topM, bottomM, remainingS, depthNowM;
    private int runs;
    private boolean stuck;

    public LoggingUnitBlockEntity(BlockPos pos, BlockState state) {
        super(DrillingBlockEntities.LOGGING_UNIT.get(), pos, state);
        for (WellLogging.Curve c : WellLogging.Curve.values()) sondes.put(c, false);
    }

    public boolean running() {
        return remainingS > 0;
    }

    public int runs() {
        return runs;
    }

    public boolean stuck() {
        return stuck;
    }

    public double topM() {
        return topM;
    }

    @Nullable
    public double[] curve(WellLogging.Curve c) {
        return curves.get(c);
    }

    public boolean loaded(WellLogging.Curve c) {
        return sondes.get(c);
    }

    private static WellLogging.Curve curveOf(ItemStack s) {
        if (s.is(PcItems.GAMMA_LOGGING_SONDE.get())) return WellLogging.Curve.GR;
        if (s.is(PcItems.NEUTRON_LOGGING_SONDE.get())) return WellLogging.Curve.NPHI;
        if (s.is(PcItems.RESISTIVITY_LOGGING_SONDE.get())) return WellLogging.Curve.RT;
        return null;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return curveOf(stack) != null;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        WellLogging.Curve c = curveOf(stack);
        if (c == null || sondes.get(c) || running()) return ItemInteractionResult.CONSUME;
        sondes.put(c, true);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.displayClientMessage(DrillingText.msg("sonde_loaded", DrillingText.msg("curve." + c.name().toLowerCase(java.util.Locale.ROOT))), true);
        setChanged();
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        return InteractionResult.PASS;
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    /** Начать каротаж; {@code null} — начат, иначе ключ причины отказа. */
    @Nullable
    public String startLog() {
        if (running()) return "log_busy";
        if (!sondes.containsValue(true)) return "log_no_sonde";
        DrillersConsoleBlockEntity c = DrillersConsoleBlockEntity.near(level, worldPosition);
        Well w = c == null ? null : c.well();
        if (w == null) return "log_no_well";
        if (w.pumpRateM3s() > 0 || w.bopClosed() || w.blowout()) return "log_not_static";
        topM = w.geometry().shoeDepthM();
        bottomM = w.holeDepthM();
        double p = WellLogging.stuckProbability(w.prospect(), topM, bottomM);
        SplittableRandom r = new SplittableRandom(worldPosition.asLong() ^ (0x9E3779B97F4A7C15L * (runs + 1)));
        if (r.nextDouble() < p) {
            stuck = true;
            depthNowM = topM + r.nextDouble() * (bottomM - topM);
            boolean source = sondes.get(WellLogging.Curve.NPHI);
            for (WellLogging.Curve k : WellLogging.Curve.values()) sondes.put(k, false);
            for (Player pl : ((ServerLevel) level).players())
                if (pl.distanceToSqr(worldPosition.getCenter()) < 32 * 32)
                    pl.displayClientMessage(DrillingText.msg(source ? "log_stuck_source" : "log_stuck", DrillingText.num(depthNowM, 0))
                            .withStyle(ChatFormatting.RED), false);
            setChanged();
            return null;
        }
        stuck = false;
        remainingS = (bottomM - topM) / WellLogging.SPEED_MS;
        curves.clear();
        for (WellLogging.Curve k : WellLogging.Curve.values())
            if (sondes.get(k)) curves.put(k, WellLogging.log(k, w.prospect(), topM, bottomM, worldPosition.asLong() ^ runs ^ k.ordinal()));
        setChanged();
        return null;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel s) || remainingS <= 0) return;
        SimLevelBinding b = SimBindings.get(s);
        if (b == null) return;
        remainingS = Math.max(0, remainingS - 0.05 * b.world().clock().kSlow());
        depthNowM = topM + remainingS * WellLogging.SPEED_MS;
        if (remainingS <= 0) {
            runs++;
            level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.0f, 1.0f);
            setChanged();
        }
        if (level.getGameTime() % 10 == 0) {
            BlockState st = getBlockState();
            if (st.getValue(PcProperties.RUNNING) != running()) level.setBlock(worldPosition, st.setValue(PcProperties.RUNNING, running()), Block.UPDATE_ALL);
        }
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id != DrillingPanels.LB_LOG) return;
        String err = startLog();
        player.displayClientMessage(err != null ? DrillingText.msg(err)
                : stuck ? DrillingText.msg("log_stuck", DrillingText.num(depthNowM, 0))
                : DrillingText.msg("log_started", DrillingText.num(topM, 0), DrillingText.num(bottomM, 0)), true);
    }

    @Override
    public PanelLayout panelLayout() {
        return DrillingPanels.LOGGING;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        int f = 0;
        if (running()) f |= 1 << DrillingPanels.LF_RUNNING;
        if (stuck) f |= 1 << DrillingPanels.LF_STUCK;
        boolean done = runs > 0 && !running();
        if (done) f |= 1 << DrillingPanels.LF_DONE;
        for (WellLogging.Curve c : WellLogging.Curve.values()) if (sondes.get(c)) f |= 1 << (DrillingPanels.LF_GR + c.ordinal());
        out[DrillingPanels.L_FLAGS] = f;
        out[DrillingPanels.L_DEPTH] = (int) Math.round(depthNowM);
        out[DrillingPanels.L_TOP] = (int) Math.round(topM);
        out[DrillingPanels.L_BOTTOM] = (int) Math.round(bottomM);
        out[DrillingPanels.L_RUNS] = runs;
        if (!done) return;
        int[] base = {DrillingPanels.L_GR, DrillingPanels.L_NPHI, DrillingPanels.L_RT};
        double[] scale = {1, 1000, 10};
        for (WellLogging.Curve c : WellLogging.Curve.values()) {
            double[] d = curves.get(c);
            if (d == null) continue;
            for (int i = 0; i < POINTS; i++) {
                int j = (int) ((long) i * (d.length - 1) / (POINTS - 1));
                out[base[c.ordinal()] + 2 * i] = (int) Math.round(1000.0 * i / (POINTS - 1));
                out[base[c.ordinal()] + 2 * i + 1] = (int) Math.round(d[j] * scale[c.ordinal()]);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        for (WellLogging.Curve c : WellLogging.Curve.values()) {
            tag.putBoolean("sonde_" + c.name(), sondes.get(c));
            double[] d = curves.get(c);
            if (d != null) {
                long[] bits = new long[d.length];
                for (int i = 0; i < d.length; i++) bits[i] = Double.doubleToRawLongBits(d[i]);
                tag.putLongArray("curve_" + c.name(), bits);
            }
        }
        tag.putDouble("top", topM);
        tag.putDouble("bottom", bottomM);
        tag.putDouble("remaining", remainingS);
        tag.putInt("runs", runs);
        tag.putBoolean("stuck", stuck);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        curves.clear();
        for (WellLogging.Curve c : WellLogging.Curve.values()) {
            sondes.put(c, tag.getBoolean("sonde_" + c.name()));
            if (tag.contains("curve_" + c.name())) {
                long[] bits = tag.getLongArray("curve_" + c.name());
                double[] d = new double[bits.length];
                for (int i = 0; i < d.length; i++) d[i] = Double.longBitsToDouble(bits[i]);
                curves.put(c, d);
            }
        }
        topM = tag.getDouble("top");
        bottomM = tag.getDouble("bottom");
        remainingS = tag.getDouble("remaining");
        runs = tag.getInt("runs");
        stuck = tag.getBoolean("stuck");
    }
}

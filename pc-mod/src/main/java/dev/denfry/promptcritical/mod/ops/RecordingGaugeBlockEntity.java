package dev.denfry.promptcritical.mod.ops;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.boiler.FireTubeBoilerBlockEntity;
import dev.denfry.promptcritical.mod.boiler.SteamEngineBlockEntity;
import dev.denfry.promptcritical.mod.grid.GridMountedBlock;
import dev.denfry.promptcritical.mod.ironworks.IronworksBlockEntity;
import dev.denfry.promptcritical.mod.mine.MainMineFanBlockEntity;
import dev.denfry.promptcritical.mod.mine.MineDrainagePumpBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.ops.ChartDrum;
import dev.denfry.promptcritical.sim.ops.OpsConfig;
import dev.denfry.promptcritical.sim.plant.BoilerUnit;
import dev.denfry.promptcritical.sim.plant.SteamPlant;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Самопишущий прибор {@code recording_gauge} (13 §13.6.7, Э1 03c §3c.2.1): на котле пишет давление, на машине и
 * вентиляторе — обороты, на насосе — подачу. Барабан оборачивается за {@code ops.recording_gauge.period_s}, точка —
 * раз в {@code sample_s} реального времени ({@link ChartDrum}, 720 точек). Расходник — {@code chart_paper} (ПКМ);
 * пустой рукой лента снимается предметом {@code recorded_chart} с компонентом {@code chart_trace}.
 */
public final class RecordingGaugeBlockEntity extends IronworksBlockEntity {
    /** Величина записи и её шкала. */
    public enum Quantity {
        PRESSURE(0, 2), SPEED(0, 160), FAN_SPEED(0, 600), FLOW(0, 150);

        public final double min, max;

        Quantity(double min, double max) {
            this.min = min;
            this.max = max;
        }

        public String key() {
            return switch (this) {
                case PRESSURE -> "pressure";
                case SPEED, FAN_SPEED -> "speed";
                case FLOW -> "flow";
            };
        }
    }

    private final OpsConfig cfg = OpsConfig.defaults();
    @Nullable
    private ChartDrum drum;
    @Nullable
    private Quantity quantity;
    private long ticks;

    public RecordingGaugeBlockEntity(BlockPos pos, BlockState state) {
        super(OpsBlockEntities.RECORDING_GAUGE.get(), pos, state);
    }

    @Nullable
    public Quantity quantity() {
        OpsMachine m = level == null ? null : OpsWorld.machine(level, GridMountedBlock.mountPos(getBlockState(), worldPosition));
        if (m instanceof FireTubeBoilerBlockEntity) return Quantity.PRESSURE;
        if (m instanceof SteamEngineBlockEntity) return Quantity.SPEED;
        if (m instanceof MainMineFanBlockEntity) return Quantity.FAN_SPEED;
        if (m instanceof MineDrainagePumpBlockEntity) return Quantity.FLOW;
        return null;
    }

    /** Значение величины сейчас; NaN — установки нет. */
    private double read(Quantity q, SimLevelBinding b) {
        OpsMachine m = OpsWorld.machine(level, GridMountedBlock.mountPos(getBlockState(), worldPosition));
        long tick = b.world().clock().tick();
        return switch (q) {
            case PRESSURE -> {
                BoilerUnit u = m instanceof FireTubeBoilerBlockEntity f ? f.unit() : null;
                yield u == null || Double.isNaN(u.pressurePa()) ? Double.NaN : (u.pressurePa() - SteamPlant.ATMOSPHERE_PA) / 1e6;
            }
            case SPEED -> m instanceof SteamEngineBlockEntity e ? e.omega() * 60 / (2 * Math.PI) : Double.NaN;
            case FAN_SPEED -> m instanceof MainMineFanBlockEntity f ? f.fanRpm(tick) : Double.NaN;
            case FLOW -> m instanceof MineDrainagePumpBlockEntity p ? p.pumpedM3s() * 3600 : Double.NaN;
        };
    }

    public boolean paper() {
        return stateFlag(PcProperties.PAPER);
    }

    @Nullable
    public ChartDrum drum() {
        return drum;
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        ticks++;
        if (!paper()) return;
        Quantity q = quantity();
        if (q == null) return;
        if (drum == null || quantity != q) {
            drum = ChartDrum.of(cfg, q.min, q.max);
            quantity = q;
        }
        long every = Math.round(cfg.recordingSampleS() * 20);
        if (ticks % every == 0) {
            drum.sample(read(q, b));
            setChanged();
        }
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.is(PcItems.CHART_PAPER.get());
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (paper()) return ItemInteractionResult.FAIL;
        Quantity q = quantity();
        if (q == null) {
            player.displayClientMessage(OpsText.msg("chart_no_unit"), true);
            return ItemInteractionResult.FAIL;
        }
        stack.consume(1, player);
        drum = ChartDrum.of(cfg, q.min, q.max);
        quantity = q;
        setStateFlag(PcProperties.PAPER, true);
        level.playSound(null, worldPosition, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8f, 1.0f);
        setChanged();
        return ItemInteractionResult.SUCCESS;
    }

    /** Пустой рукой: снять ленту, если она есть; иначе показать, что пишет прибор. */
    @Override
    public InteractionResult use(Player player) {
        Quantity q = quantity();
        if (q == null) {
            player.displayClientMessage(OpsText.msg("chart_no_unit"), true);
            return InteractionResult.SUCCESS;
        }
        if (!paper() || drum == null) {
            player.displayClientMessage(OpsText.msg("chart_no_paper", OpsText.msg("quantity." + q.key())), true);
            return InteractionResult.SUCCESS;
        }
        ItemStack chart = tearOff(q);
        if (!player.addItem(chart)) player.drop(chart, false);
        level.playSound(null, worldPosition, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8f, 0.8f);
        return InteractionResult.SUCCESS;
    }

    /** Снять ленту: предмет {@code recorded_chart}, бумаги в приборе больше нет. */
    public ItemStack tearOff(Quantity q) {
        int pos = drum.position();
        short[] trace = drum.tearOff();
        List<Integer> points = new ArrayList<>(trace.length);
        for (short s : trace) points.add((int) s);
        ItemStack chart = new ItemStack(PcItems.RECORDED_CHART.get());
        chart.set(OpsComponents.CHART_TRACE.get(), new OpsComponents.ChartTrace(q.key(), (float) q.min, (float) q.max,
                (int) Math.round(cfg.recordingSampleS()), pos, points, level.getGameTime()));
        drum = null;
        setStateFlag(PcProperties.PAPER, false);
        setChanged();
        return chart;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("ticks", ticks);
        if (drum != null && quantity != null) {
            tag.putInt("quantity", quantity.ordinal());
            tag.putLong("position", drum.position());
            short[] t = drum.trace();
            int[] a = new int[t.length];
            for (int i = 0; i < t.length; i++) a[i] = t[i];
            tag.putIntArray("trace", a);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ticks = tag.getLong("ticks");
        if (tag.contains("trace")) {
            quantity = Quantity.values()[tag.getInt("quantity")];
            drum = ChartDrum.of(cfg, quantity.min, quantity.max);
            int[] a = tag.getIntArray("trace");
            long pos = tag.getLong("position");
            // восстановить ленту: перезаписать точки в порядке барабана
            short[] fill = new short[a.length];
            for (int i = 0; i < a.length; i++) fill[i] = (short) a[i];
            drum.restore(fill, pos);
        }
    }
}

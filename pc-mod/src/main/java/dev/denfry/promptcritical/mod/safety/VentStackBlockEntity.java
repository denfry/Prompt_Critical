package dev.denfry.promptcritical.mod.safety;

import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.uranium.UraniumProcessBlockEntity;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import dev.denfry.promptcritical.sim.radiation.PointSource;
import dev.denfry.promptcritical.sim.radiation.VentFilterBank;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Вентиляционная труба реакторного зала {@code vent_stack} со спецфильтрами (03a §3a.9.1, §3a.9.3). Нижнее звено трубы
 * ({@link VentStackBlock}, 3 × 3 × 3) — узел вытяжки: включённая вытяжка забирает выбросы радионуклидов
 * ({@link RadiationService#airRelease}) из точек в {@value #HALL_RADIUS} блоках и выпускает их с верха трубы (звенья
 * {@code vent_stack} друг на друге), пропустив через фильтры в {@value #FILTER_RANGE} блоках от контроллера основания
 * (до двух блоков снаружи звена): каждый {@code hepa_filter} — ступень по аэрозолю (цезий), каждый
 * {@code charcoal_filter} — ступень по йоду ({@link VentFilterBank}); в дождь воздух влажный (выше 70 %), и уголь
 * хуже держит органический йод. Задержанная активность копится и светит от первого фильтра как точечный источник 04b.
 * Разрежение зала (−20 Па) и воздушная сеть §3a.9.2 не моделируются [допущение: вытяжка забирает весь выброс зала].
 */
public final class VentStackBlockEntity extends UraniumProcessBlockEntity implements dev.denfry.promptcritical.mod.environment.SelfEmittingSource {
    public static final int HALL_RADIUS = 16, FILTER_RANGE = 3;
    private static final Map<Level, Set<VentStackBlockEntity>> STACKS = new WeakHashMap<>();

    private VentFilterBank bank = new VentFilterBank(0, 0);
    private boolean base = true, humid;
    @Nullable
    private BlockPos firstFilter;
    private BlockPos top;
    private double inI, outI, inCs, outCs;

    public VentStackBlockEntity(BlockPos pos, BlockState state) {
        super(SafetyBlockEntities.VENT_STACK.get(), pos, state, 0, 0, s -> false);
        top = pos;
        on = false; // вытяжку пускают со щита
    }

    /** Пуск или останов вытяжки. */
    public void setExhaust(boolean run) {
        on = run;
        setChanged();
    }

    public VentFilterBank bank() {
        return bank;
    }

    public double lastIn(Emitter e) {
        return e == Emitter.I131 ? inI : e == Emitter.CS137 ? inCs : 0;
    }

    public double lastOut(Emitter e) {
        return e == Emitter.I131 ? outI : e == Emitter.CS137 ? outCs : 0;
    }

    public Vec3 releasePoint() {
        return top.getCenter().add(0, 1, 0);
    }

    /** Вытяжка, которая забирает выброс из точки; {@code null} — выброс идёт как был. */
    @Nullable
    public static VentStackBlockEntity capture(Level level, Vec3 at) {
        Set<VentStackBlockEntity> s = STACKS.get(level);
        if (s == null) return null;
        VentStackBlockEntity best = null;
        double bestD = HALL_RADIUS * HALL_RADIUS;
        for (VentStackBlockEntity v : s) {
            if (v.isRemoved() || !v.on || !v.base) continue;
            double d = v.getBlockPos().getCenter().distanceToSqr(at);
            if (d <= bestD) {
                bestD = d;
                best = v;
            }
        }
        return best;
    }

    /** Пропустить выброс через фильтры, Бк/с после фильтров (вызов — раз в секунду на источник). */
    public double filter(Emitter e, double bqS) {
        double out = bank.pass(e, bqS, 1, humid);
        if (e == Emitter.I131) {
            inI += bqS;
            outI += out;
        } else if (e == Emitter.CS137) {
            inCs += bqS;
            outCs += out;
        }
        setChanged();
        return out;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) STACKS.computeIfAbsent(level, k -> new HashSet<>()).add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        Set<VentStackBlockEntity> s = STACKS.get(level);
        if (s != null) s.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        Set<VentStackBlockEntity> s = STACKS.get(level);
        if (s != null) s.remove(this);
    }

    /** Пересчитать фильтры и высоту трубы. */
    public void survey(ServerLevel level) {
        STACKS.computeIfAbsent(level, k -> new HashSet<>()).add(this);
        int seg = VentStackBlock.SEGMENT_H;
        base = !level.getBlockState(worldPosition.below(seg)).is(PcBlocks.VENT_STACK.get());
        BlockPos t = worldPosition;
        while (level.getBlockState(t.above(seg)).is(PcBlocks.VENT_STACK.get()) && t.getY() < level.getMaxBuildHeight()) t = t.above(seg);
        // верх — верхний ряд клеток последнего звена (контроллер звена — в его середине)
        top = t.above();
        int hepa = 0, charcoal = 0;
        firstFilter = null;
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-FILTER_RANGE, -FILTER_RANGE, -FILTER_RANGE),
                worldPosition.offset(FILTER_RANGE, FILTER_RANGE, FILTER_RANGE))) {
            BlockState s = level.getBlockState(p);
            boolean h = s.is(PcBlocks.HEPA_FILTER.get()), c = s.is(PcBlocks.CHARCOAL_FILTER.get());
            if (h) hepa++;
            if (c) charcoal++;
            if ((h || c) && firstFilter == null) firstFilter = p.immutable();
        }
        if (hepa != bank.hepaStages() || charcoal != bank.charcoalStages()) {
            VentFilterBank b = new VentFilterBank(hepa, charcoal);
            b.setRetained(bank.retainedI131Bq(), bank.retainedCs137Bq());
            bank = b;
        }
        humid = level.isRainingAt(top.above()) || level.isRaining();
    }

    @Override
    protected void process(ServerLevel level, double dtPhysS) {
        if ((level.getGameTime() + worldPosition.hashCode()) % 20 != 0) return;
        survey(level);
        bank.decay(1);
        if (firstFilter != null && (bank.retainedI131Bq() > 0 || bank.retainedCs137Bq() > 0)) {
            PointSource src = Emitter.I131.source(bank.retainedI131Bq()).plus(Emitter.CS137.source(bank.retainedCs137Bq()));
            RadiationService.source(level, firstFilter.getCenter(), src);
        }
        // средние за секунду для щита: копим по вызовам, обнуляем здесь
        lastI = new double[] {inI, outI};
        lastCs = new double[] {inCs, outCs};
        inI = outI = inCs = outCs = 0;
    }

    private double[] lastI = new double[2], lastCs = new double[2];

    @Override
    protected boolean running() {
        return on && base;
    }

    @Override
    public PanelLayout panelLayout() {
        return SafetyPanels.VENT_STACK;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[SafetyPanels.VS_DF_I_LOG10] = (int) Math.round(Math.log10(bank.df(Emitter.I131, humid)) * 100);
        v[SafetyPanels.VS_DF_CS_LOG10] = (int) Math.round(Math.log10(bank.df(Emitter.CS137, humid)) * 100);
        v[SafetyPanels.VS_HEPA] = bank.hepaStages();
        v[SafetyPanels.VS_CHARCOAL] = bank.charcoalStages();
        v[SafetyPanels.VS_RET_I_MBQ] = (int) Math.min(Integer.MAX_VALUE, Math.round(bank.retainedI131Bq() / 1e6));
        v[SafetyPanels.VS_RET_CS_MBQ] = (int) Math.min(Integer.MAX_VALUE, Math.round(bank.retainedCs137Bq() / 1e6));
        v[SafetyPanels.VS_OUT_KBQS] = (int) Math.min(Integer.MAX_VALUE, Math.round((lastI[1] + lastCs[1]) / 1e3));
        v[SafetyPanels.VS_IN_KBQS] = (int) Math.min(Integer.MAX_VALUE, Math.round((lastI[0] + lastCs[0]) / 1e3));
        v[SafetyPanels.VS_HEIGHT] = top.getY() - worldPosition.getY() + 1;
        v[SafetyPanels.VS_FLAGS] = (on && base ? 1 << SafetyPanels.F_VS_ON : 0) | (humid ? 1 << SafetyPanels.F_VS_HUMID : 0)
                | (bank.hepaStages() + bank.charcoalStages() == 0 ? 1 << SafetyPanels.F_VS_NO_FILTERS : 0)
                | (!base ? 1 << SafetyPanels.F_VS_NOT_BASE : 0);
    }

    @Override
    public void onPanelButton(net.minecraft.server.level.ServerPlayer player, int id) {
        if (id == SafetyPanels.B_HATCH) use(player);
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putDouble("ret_i131", bank.retainedI131Bq());
        tag.putDouble("ret_cs137", bank.retainedCs137Bq());
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        bank.setRetained(tag.getDouble("ret_i131"), tag.getDouble("ret_cs137"));
    }
}

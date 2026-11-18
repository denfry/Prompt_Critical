package dev.denfry.promptcritical.mod.ops;

import dev.denfry.promptcritical.mod.automation.AutomationBlockEntity;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.content.SimAchievements;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.automation.AutomationUnit;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.ops.LockoutRegistry;
import dev.denfry.promptcritical.sim.ops.OpsLogEntry;
import dev.denfry.promptcritical.sim.ops.OpsSubsystem;
import dev.denfry.promptcritical.sim.ops.PermitRegistry;
import dev.denfry.promptcritical.sim.ops.PermitState;
import dev.denfry.promptcritical.sim.ops.Turnaround;
import dev.denfry.promptcritical.sim.ops.TurnaroundPhase;
import java.util.Arrays;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Стенд нарядов-допусков {@code permit_board} (13 §13.2.2) в операторной: показывает наряды установки вокруг себя и
 * ведёт её остановочный ремонт ({@link Turnaround}, 13 §13.6.2). Установка — всё в радиусе от стенда (16 м, кнопками щита 4–32 м):
 * машины (блоки со свойством {@code running}), наряды, замки LOTO, блоки ПАЗ. Газоанализ освобождённой установки
 * заносит переносной газоанализатор у стенда.
 */
public final class PermitBoardBlockEntity extends dev.denfry.promptcritical.mod.ironworks.IronworksBlockEntity implements PanelHost {
    /** Радиус установки вокруг стенда по умолчанию, м [BALANCE]; кнопками щита — от {@value #SCOPE_MIN_M} до {@value #SCOPE_MAX_M}. */
    public static final double SCOPE_M = 16, SCOPE_MIN_M = 4, SCOPE_MAX_M = 32;
    private static final int UPDATE_TICKS = 20;
    private final int[] cache = new int[PermitBoardPanels.CHANNELS];
    private int ticks;
    private boolean gasFree;
    private long gasTick = Long.MIN_VALUE;
    private double scope = SCOPE_M;

    public PermitBoardBlockEntity(BlockPos pos, BlockState state) {
        super(OpsBlockEntities.PERMIT_BOARD.get(), pos, state);
    }

    @Nullable
    private OpsSubsystem ops() {
        return OpsWorld.ops(level);
    }

    /** Ремонт установки этого стенда; {@code null} — мира симуляции нет. */
    @Nullable
    public Turnaround turnaround() {
        OpsSubsystem ops = ops();
        return ops == null ? null : ops.turnaround(Lockouts.key(worldPosition));
    }

    /** Граница установки: радиус вокруг стенда, м. */
    public double scope() {
        return scope;
    }

    public void setScope(double m) {
        scope = Math.max(SCOPE_MIN_M, Math.min(SCOPE_MAX_M, m));
        setChanged();
    }

    /** Газоанализ установки у стенда (из {@link Permits#measure}). */
    void gasTested(boolean free, long tick) {
        gasFree = free;
        gasTick = tick;
        setChanged();
    }

    /** Состояние установки для условий фаз ремонта. */
    public Turnaround.Inputs inputs() {
        Turnaround t = turnaround();
        OpsSubsystem ops = ops();
        if (t == null || ops == null || !(level instanceof ServerLevel sl)) return new Turnaround.Inputs(0, 0, 0, 0, 0, 0, false);
        GridPos me = Lockouts.key(worldPosition);
        int running = 0, bypassed = 0, overdue = 0;
        for (BlockEntity be : Permits.blockEntities(sl, worldPosition, scope)) {
            BlockState s = be.getBlockState();
            if (s.hasProperty(PcProperties.RUNNING) && s.getValue(PcProperties.RUNNING)) running++;
            if (be instanceof AutomationBlockEntity a) {
                AutomationUnit u = a.unit();
                if (u != null && u.sis() != null) {
                    if (u.sis().bypass()) bypassed++;
                    if (u.sis().proofTestOverdue(u.sisProofIntervalH())) overdue++;
                }
            }
        }
        PermitRegistry r = ops.permits();
        int locks = 0;
        LockoutRegistry lr = ops.lockouts();
        for (GridPos p : lr.lockedPoints()) if (p.distanceSq(me) <= scope * scope) locks++;
        long now = Permits.tick(level);
        boolean gas = gasFree && gasTick != Long.MIN_VALUE && now - gasTick <= PermitRegistry.GAS_RETEST_S * PermitRegistry.TICKS_PER_S
                && (t.startTick() < 0 || gasTick >= t.startTick());
        long since = t.startTick() < 0 ? Long.MAX_VALUE : t.startTick();
        return new Turnaround.Inputs(running, r.openNear(me, scope), r.completedNear(me, scope, since), locks, bypassed, overdue, gas);
    }

    private int activePermits() {
        OpsSubsystem ops = ops();
        if (ops == null) return 0;
        GridPos me = Lockouts.key(worldPosition);
        int n = 0;
        for (PermitRegistry.Permit p : ops.permits().all())
            if (p.state() == PermitState.ACTIVE && p.target().distanceSq(me) <= scope * scope) n++;
        return n;
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        if (ticks++ % UPDATE_TICKS != 0) return;
        Turnaround t = turnaround();
        if (t == null) return;
        Turnaround.Inputs in = inputs();
        cache[PermitBoardPanels.PHASE] = t.phase().ordinal();
        cache[PermitBoardPanels.RUNNING] = in.runningMachines();
        cache[PermitBoardPanels.OPEN] = in.openPermits();
        cache[PermitBoardPanels.DONE] = in.completedPermits();
        cache[PermitBoardPanels.LOCKS] = in.locksHung();
        cache[PermitBoardPanels.SIS_BYPASSED] = in.sisBypassed();
        cache[PermitBoardPanels.SIS_OVERDUE] = in.sisOverdue();
        cache[PermitBoardPanels.FLAGS] = (in.gasFree() ? 1 << PermitBoardPanels.F_GAS : 0) | (t.complete() ? 1 << PermitBoardPanels.F_COMPLETE : 0);
        int mask = 0;
        for (Turnaround.Missing m : t.missing(in)) mask |= 1 << m.ordinal();
        cache[PermitBoardPanels.MISSING] = mask;
        cache[PermitBoardPanels.ACTIVE] = activePermits();
        cache[PermitBoardPanels.SCOPE] = (int) scope;
    }

    /**
     * Перевести ремонт в следующую фазу.
     *
     * @return невыполненные условия; пусто — переход сделан
     */
    public Set<Turnaround.Missing> next(@Nullable ServerPlayer by) {
        Turnaround t = turnaround();
        OpsSubsystem ops = ops();
        if (t == null || ops == null) return Set.of(Turnaround.Missing.DONE);
        TurnaroundPhase from = t.phase();
        Set<Turnaround.Missing> m = t.advance(inputs(), Permits.tick(level));
        if (!m.isEmpty()) {
            if (by != null) by.displayClientMessage(PermitText.msg("turnaround.missing", PermitText.phase(from), PermitText.missing(m))
                    .withStyle(ChatFormatting.GOLD), false);
            return m;
        }
        ops.journal(SimId.NONE, OpsLogEntry.Kind.TURNAROUND_PHASE, from + "->" + t.phase() + " @" + worldPosition.toShortString());
        if (by != null) by.displayClientMessage(PermitText.msg("turnaround.phase", PermitText.phase(t.phase())), false);
        if (t.complete()) {
            long minutes = (t.completeTick() - t.startTick()) / Math.round(PermitRegistry.TICKS_PER_S) / 60;
            if (by != null) by.displayClientMessage(PermitText.msg("turnaround.complete", minutes, t.repairs()).withStyle(ChatFormatting.GREEN), false);
            if (t.repairs() >= 1 && level instanceof ServerLevel sl) {
                SimAchievements.nearby(sl, worldPosition, SimAchievements.TURNAROUND, "repairs", t.repairs());
                if (by != null) SimAchievements.direct(by, SimAchievements.TURNAROUND, "repairs", t.repairs());
            }
        }
        ticks = 0;
        return m;
    }

    @Override
    public PanelLayout panelLayout() {
        return PermitBoardPanels.BOARD;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        System.arraycopy(cache, 0, out, 0, Math.min(cache.length, out.length));
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        Turnaround t = turnaround();
        if (t == null) return;
        if (id == PermitBoardPanels.B_NEXT) {
            next(player);
        } else if (id == PermitBoardPanels.B_RESET && t.complete()) {
            t.reset();
            player.displayClientMessage(PermitText.msg("turnaround.reset"), false);
        } else if (id == PermitBoardPanels.B_SCOPE_DOWN || id == PermitBoardPanels.B_SCOPE_UP) {
            setScope(scope + (id == PermitBoardPanels.B_SCOPE_UP ? 2 : -2));
        }
        ticks = 0;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("gas_free", gasFree);
        tag.putLong("gas_tick", gasTick);
        tag.putDouble("scope", scope);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        gasFree = tag.getBoolean("gas_free");
        gasTick = tag.contains("gas_tick") ? tag.getLong("gas_tick") : Long.MIN_VALUE;
        scope = tag.contains("scope") ? tag.getDouble("scope") : SCOPE_M;
    }
}

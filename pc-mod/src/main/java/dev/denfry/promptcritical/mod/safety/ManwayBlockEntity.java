package dev.denfry.promptcritical.mod.safety;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.ops.Lockouts;
import dev.denfry.promptcritical.mod.ops.OpsWorld;
import dev.denfry.promptcritical.mod.ops.Permits;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.uranium.UraniumProcessBlockEntity;
import dev.denfry.promptcritical.sim.ops.ConfinedSpaceAtmosphere;
import dev.denfry.promptcritical.sim.ops.ConfinedSpaceAtmosphere.Gas;
import dev.denfry.promptcritical.sim.ops.GasLimits;
import dev.denfry.promptcritical.sim.ops.GasTest;
import dev.denfry.promptcritical.sim.ops.OpsLogEntry;
import dev.denfry.promptcritical.sim.ops.OpsSubsystem;
import dev.denfry.promptcritical.sim.ops.PermitEnforcement;
import dev.denfry.promptcritical.sim.ops.PermitRegistry;
import dev.denfry.promptcritical.sim.ops.PermitType;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/**
 * Люк-лаз {@code manway} (13 §13.2.6): регистрирует замкнутое пространство за собой — заливкой воздуха с любой
 * стороны, ограниченной объёмом не больше {@value ConfinedSpaceAtmosphere#MAX_VOLUME} блоков (1 блок = 1 м³) — и
 * ведёт его атмосферу ({@link ConfinedSpaceAtmosphere}, 1 Гц реального времени). Притоки: газ трубой в штуцер люка
 * (азот и аргон — удушающие, товарный газ — метан, попутный — метан с 2 % H₂S, конвертерный — 70 % CO, 15 % CO₂, 15 % N₂
 * [ориентир 08 §8.6]) с расходом продувки {@value #PURGE_NM3S} нм³/с (пример §13.2.6: 50 л/с). Вентиляция: открытый
 * люк — {@value ConfinedSpaceAtmosphere#NATURAL_VENT_M3S} м³/с, переносной вентилятор в
 * {@value #VENTILATOR_RANGE} блоках при открытом люке — ещё {@value ConfinedSpaceAtmosphere#VENTILATOR_M3S} м³/с.
 *
 * <p>Вход и выход — Shift + ПКМ по люку (лаз в стене аппарата). Вход по наряду §13.2.4: при {@code ENFORCED} нужен
 * действующий наряд {@code CONFINED_SPACE} на люк и наблюдающий — другой игрок не дальше {@value #ATTENDANT_M} м от
 * люка и не внутри; при {@code ADVISORY} — предупреждение и запись в журнал. Каждую секунду игроки внутри дышат
 * атмосферой ({@link ConfinedSpaces#breathe}). Протечки через отсечную арматуру и разложение отложений не
 * моделируются [допущение: источником газа служит только штуцер].
 */
public final class ManwayBlockEntity extends UraniumProcessBlockEntity {
    /** Люк — пассивный блок. */
    @Override
    protected double ratedPowerW() {
        return 0;
    }

    public static final double PURGE_NM3S = 0.05, MOL_PER_NM3 = 44.6, ATTENDANT_M = 4;
    public static final int VENTILATOR_RANGE = 3, TANK_MB = 1000, RESCAN_TICKS = 200;

    private final FluidTank tank = new FluidTank(TANK_MB, s -> composition(s.getFluid()) != null) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private Set<Long> space = Set.of();
    @Nullable
    private Direction inside;
    private ConfinedSpaceAtmosphere atm = new ConfinedSpaceAtmosphere(1);
    private double pendingNm3, ventM3s;
    private int insideCount;
    private boolean attendant, permit, forced;
    private final Set<UUID> entrants = new HashSet<>();

    public ManwayBlockEntity(BlockPos pos, BlockState state) {
        super(SafetyBlockEntities.MANWAY.get(), pos, state, 0, 0, s -> false);
    }

    /** Мольные доли газа, подаваемого жидкостью {@code f}, по {@link Gas}; {@code null} — люк её не принимает. */
    @Nullable
    static double[] composition(Fluid f) {
        double[] x = new double[Gas.values().length];
        if (f.isSame(PcFluids.NITROGEN.source.get()) || f.isSame(PcFluids.ARGON.source.get())) x[Gas.N2.ordinal()] = 1;
        else if (f.isSame(PcFluids.SALES_GAS.source.get())) x[Gas.CH4.ordinal()] = 1;
        else if (f.isSame(PcFluids.ASSOCIATED_GAS.source.get())) {
            x[Gas.CH4.ordinal()] = 0.98;
            x[Gas.H2S.ordinal()] = 0.02;
        } else if (f.isSame(PcFluids.CONVERTER_GAS.source.get())) {
            x[Gas.CO.ordinal()] = 0.70;
            x[Gas.CO2.ordinal()] = 0.15;
            x[Gas.N2.ordinal()] = 0.15;
        } else return null;
        return x;
    }

    public ConfinedSpaceAtmosphere atmosphere() {
        return atm;
    }

    public boolean confined() {
        return !space.isEmpty();
    }

    public boolean contains(long pos) {
        return space.contains(pos);
    }

    public int volume() {
        return space.size();
    }

    public boolean hatchOpen() {
        BlockState s = getBlockState();
        return s.hasProperty(BlockStateProperties.OPEN) && s.getValue(BlockStateProperties.OPEN);
    }

    public void setHatch(boolean open) {
        BlockState s = getBlockState();
        if (level != null && s.hasProperty(BlockStateProperties.OPEN) && s.getValue(BlockStateProperties.OPEN) != open) {
            level.setBlock(worldPosition, s.setValue(BlockStateProperties.OPEN, open), Block.UPDATE_ALL);
            level.playSound(null, worldPosition, open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.8f, 0.8f);
        }
    }

    public double ventM3s() {
        return ventM3s;
    }

    /** Мгновенно заменить атмосферу (испытания, загрузка). */
    public void setAtmosphere(double[] fractions) {
        atm = new ConfinedSpaceAtmosphere(Math.max(1, volume()), fractions);
        setChanged();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) ConfinedSpaces.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        ConfinedSpaces.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        ConfinedSpaces.remove(this);
    }

    /** Пересчитать объём: наименьшая ограниченная заливка воздуха среди соседей люка. */
    public void rescan(ServerLevel level) {
        Set<Long> best = null;
        Direction bestDir = null;
        for (Direction d : Direction.values()) {
            BlockPos start = worldPosition.relative(d);
            if (best != null && best.contains(start.asLong())) continue;
            Set<Long> s = ConfinedSpaces.flood(level, start);
            if (s != null && (best == null || s.size() < best.size())) {
                best = s;
                bestDir = d;
            }
        }
        int before = space.size();
        space = best == null ? Set.of() : best;
        inside = bestDir;
        if (space.size() != before && !space.isEmpty()) atm = new ConfinedSpaceAtmosphere(space.size(), atm.fractions());
        ConfinedSpaces.add(this);
    }

    @Override
    protected void process(ServerLevel level, double dtPhysS) {
        long now = level.getGameTime();
        if ((now + worldPosition.hashCode()) % RESCAN_TICKS == 0 || (space.isEmpty() && now % 40 == 0)) rescan(level);
        if ((now + worldPosition.hashCode()) % 20 != 0) return;
        if (space.isEmpty()) {
            insideCount = 0;
            return;
        }
        // приток через штуцер: продувка с постоянным расходом, пока в буфере есть газ
        double[] in = null;
        double[] x = tank.isEmpty() ? null : composition(tank.getFluid().getFluid());
        if (x != null) {
            pendingNm3 += PURGE_NM3S;
            if (pendingNm3 >= 1) {
                tank.drain(1, IFluidHandler.FluidAction.EXECUTE);
                pendingNm3 -= 1;
            }
            in = new double[x.length];
            for (int i = 0; i < x.length; i++) in[i] = x[i] * PURGE_NM3S * MOL_PER_NM3;
        }
        boolean open = hatchOpen();
        forced = open && ventilator(level);
        ventM3s = (open ? ConfinedSpaceAtmosphere.NATURAL_VENT_M3S : 0) + (forced ? ConfinedSpaceAtmosphere.VENTILATOR_M3S : 0);
        atm.step(1, in, ventM3s);
        // люди внутри и у входа
        Set<UUID> now2 = new HashSet<>();
        attendant = false;
        Vec3 c = worldPosition.getCenter();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            if (inside(p)) {
                now2.add(p.getUUID());
                ConfinedSpaces.breathe(p, atm, now);
            } else if (p.distanceToSqr(c) <= ATTENDANT_M * ATTENDANT_M) attendant = true;
        }
        entrants.retainAll(now2);
        insideCount = now2.size();
        PermitRegistry r = Permits.registry(level);
        permit = r != null && r.activeCovering(PermitType.CONFINED_SPACE, Lockouts.key(worldPosition)) != null;
    }

    private boolean ventilator(ServerLevel level) {
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-VENTILATOR_RANGE, -VENTILATOR_RANGE, -VENTILATOR_RANGE),
                worldPosition.offset(VENTILATOR_RANGE, VENTILATOR_RANGE, VENTILATOR_RANGE))) {
            if (level.getBlockEntity(p) instanceof PortableVentilatorBlockEntity v && v.running()) return true;
        }
        return false;
    }

    /** Игрок внутри пространства (ноги или глаза). */
    public boolean inside(Player p) {
        return space.contains(p.blockPosition().asLong()) || space.contains(BlockPos.containing(p.getEyePosition()).asLong());
    }

    /** Атмосфера по газоанализу §13.2.3: замер переносным газоанализатором через люк или внутри. */
    public GasTest sample(long tick, UUID by, boolean bumped) {
        double lel = atm.lelFraction() * Math.min(1, atm.fraction(Gas.O2) / dev.denfry.promptcritical.mod.automation.ProcessSources.CATALYTIC_O2_MIN);
        return new GasTest(tick, by, "portable_gas_detector", bumped, atm.fraction(Gas.O2), lel, atm.ppm(Gas.H2S), atm.ppm(Gas.CO));
    }

    /** Атмосфера пригодна для входа по пределам §13.2.3. */
    public boolean fitToEnter() {
        GasTest t = new GasTest(0, new UUID(0, 0), "", true, atm.fraction(Gas.O2), atm.lelFraction(), atm.ppm(Gas.H2S), atm.ppm(Gas.CO));
        return GasLimits.DEFAULT.faults(PermitType.CONFINED_SPACE, t, true).isEmpty();
    }

    // ------------------------------------------------------------------ вход и выход

    /** Shift + ПКМ: изнутри — вылезти наружу, снаружи — войти через открытый люк по правилам наряда. */
    @Override
    public InteractionResult use(Player player) {
        if (!(player instanceof ServerPlayer p) || !(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        if (inside(p)) {
            leave(p);
            return InteractionResult.SUCCESS;
        }
        if (space.isEmpty()) {
            p.displayClientMessage(SafetyText.msg("not_confined").withStyle(ChatFormatting.GOLD), true);
            return InteractionResult.SUCCESS;
        }
        if (!hatchOpen()) {
            p.displayClientMessage(SafetyText.msg("hatch_closed").withStyle(ChatFormatting.GOLD), true);
            return InteractionResult.SUCCESS;
        }
        enter(p);
        return InteractionResult.SUCCESS;
    }

    /**
     * Попытка входа (§13.2.6).
     *
     * @return вошёл
     */
    public boolean enter(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        PermitEnforcement mode = Permits.enforcement();
        PermitRegistry r = Permits.registry(sl);
        boolean hasPermit = r != null && r.activeCovering(PermitType.CONFINED_SPACE, Lockouts.key(worldPosition)) != null;
        boolean watched = false;
        Vec3 c = worldPosition.getCenter();
        for (ServerPlayer o : sl.players())
            if (o != p && !o.isSpectator() && !inside(o) && o.distanceToSqr(c) <= ATTENDANT_M * ATTENDANT_M) watched = true;
        if (mode != PermitEnforcement.OFF && (!hasPermit || !watched)) {
            String why = !hasPermit ? "no_permit" : "no_attendant";
            if (mode == PermitEnforcement.ENFORCED) {
                p.displayClientMessage(SafetyText.msg(why).withStyle(ChatFormatting.RED), true);
                return false;
            }
            p.displayClientMessage(SafetyText.msg(why).withStyle(ChatFormatting.GOLD), false);
            journal(sl, OpsLogEntry.Kind.UNAUTHORIZED_WORK, "confined entry " + why + " by " + p.getGameProfile().getName());
        }
        BlockPos to = entryPoint();
        if (to == null) return false;
        ConfinedSpaces.move(p, to);
        entrants.add(p.getUUID());
        sl.playSound(null, worldPosition, SoundEvents.LADDER_STEP, SoundSource.PLAYERS, 1.0f, 0.8f);
        journal(sl, OpsLogEntry.Kind.CONFINED_ENTRY, p.getGameProfile().getName());
        if (!fitToEnter()) p.displayClientMessage(SafetyText.msg("entered_unsafe").withStyle(ChatFormatting.RED), true);
        else p.displayClientMessage(SafetyText.msg("entered"), true);
        return true;
    }

    /** Вылезти через люк наружу (или быть вытянутым треногой). */
    public void leave(ServerPlayer p) {
        BlockPos out = exitPoint();
        ConfinedSpaces.move(p, out);
        entrants.remove(p.getUUID());
        p.serverLevel().playSound(null, worldPosition, SoundEvents.LADDER_STEP, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    /** Точка внутри у люка: соседний блок пространства, на котором можно стоять. */
    @Nullable
    BlockPos entryPoint() {
        if (inside == null) return null;
        BlockPos p = worldPosition.relative(inside);
        // опуститься до пола пространства, но не дальше 4 блоков
        for (int i = 0; i < 4 && space.contains(p.below().asLong()); i++) p = p.below();
        return p;
    }

    /** Точка снаружи: напротив стороны пространства; занята — над люком. */
    BlockPos exitPoint() {
        Direction d = inside == null ? Direction.UP : inside.getOpposite();
        BlockPos p = worldPosition.relative(d);
        if (level != null && !level.getBlockState(p).getCollisionShape(level, p).isEmpty()) p = worldPosition.above();
        while (level != null && !level.getBlockState(p).getCollisionShape(level, p).isEmpty() && p.getY() < level.getMaxBuildHeight()) p = p.above();
        return p;
    }

    private void journal(ServerLevel sl, OpsLogEntry.Kind kind, String detail) {
        OpsSubsystem ops = OpsWorld.ops(sl);
        if (ops != null) ops.journal(dev.denfry.promptcritical.sim.core.SimId.NONE, kind, detail + " @" + worldPosition.toShortString());
    }

    @Override
    protected boolean running() {
        return insideCount > 0;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return tank;
    }

    @Override
    public PanelLayout panelLayout() {
        return SafetyPanels.MANWAY;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[SafetyPanels.MW_O2_X10] = (int) Math.round(atm.fraction(Gas.O2) * 1000);
        v[SafetyPanels.MW_LEL_X10] = (int) Math.round(Math.min(1, atm.lelFraction()) * 1000);
        v[SafetyPanels.MW_H2S] = (int) Math.round(Math.min(99_999, atm.ppm(Gas.H2S)));
        v[SafetyPanels.MW_CO] = (int) Math.round(Math.min(99_999, atm.ppm(Gas.CO)));
        v[SafetyPanels.MW_VOLUME] = volume();
        v[SafetyPanels.MW_VENT_LS] = (int) Math.round(ventM3s * 1000);
        v[SafetyPanels.MW_INSIDE] = insideCount;
        double t = atm.ventilationTimeS(ventM3s);
        v[SafetyPanels.MW_SAFE_S] = fitToEnter() ? 0 : Double.isInfinite(t) ? -1 : (int) Math.min(Integer.MAX_VALUE, Math.max(1, Math.round(t)));
        v[SafetyPanels.MW_TANK] = tank.getFluidAmount();
        v[SafetyPanels.MW_FLAGS] = (hatchOpen() ? 1 << SafetyPanels.F_OPEN : 0) | (confined() ? 1 << SafetyPanels.F_CONFINED : 0)
                | (attendant ? 1 << SafetyPanels.F_ATTENDANT : 0) | (permit ? 1 << SafetyPanels.F_PERMIT : 0)
                | (forced ? 1 << SafetyPanels.F_VENT : 0) | (confined() && !fitToEnter() ? 1 << SafetyPanels.F_UNSAFE : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == SafetyPanels.B_HATCH) setHatch(!hatchOpen());
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("tank", tank.writeToNBT(registries, new CompoundTag()));
        double[] x = atm.fractions();
        for (Gas g : Gas.values()) tag.putDouble("x_" + g.name().toLowerCase(java.util.Locale.ROOT), x[g.ordinal()]);
        tag.putDouble("pending", pendingNm3);
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("tank")) tank.readFromNBT(registries, tag.getCompound("tank"));
        if (tag.contains("x_o2")) {
            double[] x = new double[Gas.values().length];
            for (Gas g : Gas.values()) x[g.ordinal()] = tag.getDouble("x_" + g.name().toLowerCase(java.util.Locale.ROOT));
            atm = new ConfinedSpaceAtmosphere(1, x);
        }
        pendingNm3 = tag.getDouble("pending");
    }
}

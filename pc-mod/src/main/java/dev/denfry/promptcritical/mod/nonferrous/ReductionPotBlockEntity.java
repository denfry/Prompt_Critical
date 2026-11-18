package dev.denfry.promptcritical.mod.nonferrous;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.environment.AirQualityService;
import dev.denfry.promptcritical.mod.grid.DcLoad;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.uranium.UraniumProcessBlockEntity;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.environment.EmissionLedger;
import dev.denfry.promptcritical.sim.nonferrous.ReductionPot;
import dev.denfry.promptcritical.sim.nonferrous.ReductionPot.State;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Электролизёр Холла–Эру ({@link ReductionPot}, 08 §8.8.2) 3×2×3. Постоянный ток — от выпрямительного агрегата
 * ({@code rectifier_unit}, 07 §7.7.4) в его радиусе: ванны в {@value #SERIES_REACH} блоках друг от друга образуют серию,
 * ток серии один, напряжения складываются. Глинозём, обожжённые аноды, криолит (пусковая заливка и подпитка
 * фторидами), а для капремонта — подовые блоки и огнеупор загружают воронкой или рукой; алюминий выбирают ковшом —
 * чушками по {@code metallurgy.ingot_mass_kg} в склад (воронкой снизу).
 *
 * <p>Электролиз идёт в реальном времени (08 §8.0.3), шаг — 0,05 с на тик. Перфторуглероды анодного эффекта
 * записываются в учёт выбросов (14) источником {@code reduction_pot@…}.
 */
public final class ReductionPotBlockEntity extends UraniumProcessBlockEntity implements DcLoad {
    public static final int SERIES_REACH = 4, SERIES_MAX = 24;
    /** Капремонт: подовые блоки и огнеупорный кирпич [BALANCE]. */
    public static final int RELINE_CATHODES = 4, RELINE_BRICKS = 8;
    /** Запас анодного углерода, ниже которого ставится новый анод, кг. */
    public static final double ANODE_RESERVE_KG = 200, FLUORIDE_RESERVE_KG = 20;
    private static final double TICK_S = 0.05;

    private final ReductionPot pot = new ReductionPot();
    private double hopperKg, suppliedAmps;
    private long suppliedTick = -10;
    private final List<BlockPos> series = new ArrayList<>();
    private long seriesTick = -100;
    private State lastState = State.COLD;

    public ReductionPotBlockEntity(BlockPos pos, BlockState state) {
        super(NonferrousBlockEntities.REDUCTION_POT.get(), pos, state, 6, 4, ReductionPotBlockEntity::loadable);
    }

    static boolean loadable(ItemStack s) {
        return s.is(PcItems.ALUMINA.get()) || s.is(PcItems.CARBON_ANODE.get()) || s.is(PcItems.CRYOLITE.get())
                || s.is(PcItems.CATHODE_BLOCK.get()) || s.is(PcItems.REFRACTORY_BRICK.get());
    }

    public ReductionPot pot() {
        return pot;
    }

    /** Ток серии на этот тик, А (без обновления два тика — ноль). */
    public double amps() {
        return level != null && level.getGameTime() - suppliedTick <= 2 ? suppliedAmps : 0;
    }

    // --- шина постоянного тока ---

    @Override
    public double dcSetpointA() {
        return pot.currentSetA();
    }

    @Override
    public double dcDemandW() {
        double a = pot.currentSetA(), w = 0;
        for (ReductionPotBlockEntity p : series()) if (p.on) w += p.pot.dcDemandW(a);
        return w;
    }

    /** Ток от выпрямителя: та же сила тока на все ванны серии. */
    @Override
    public void supply(double amps) {
        for (ReductionPotBlockEntity p : series()) p.receive(amps);
    }

    void receive(double amps) {
        suppliedAmps = Math.max(0, amps);
        suppliedTick = level.getGameTime();
    }

    /** Серия: ванны, связанные цепочкой не дальше {@value #SERIES_REACH} блоков; пересобирается раз в секунду. */
    public List<ReductionPotBlockEntity> series() {
        List<ReductionPotBlockEntity> out = new ArrayList<>();
        if (level == null) return out;
        if (level.getGameTime() - seriesTick >= 20) {
            seriesTick = level.getGameTime();
            series.clear();
            Deque<BlockPos> todo = new ArrayDeque<>();
            todo.add(worldPosition);
            series.add(worldPosition);
            while (!todo.isEmpty() && series.size() < SERIES_MAX) {
                BlockPos c = todo.poll();
                for (BlockPos q : BlockPos.betweenClosed(c.offset(-SERIES_REACH, -1, -SERIES_REACH), c.offset(SERIES_REACH, 1, SERIES_REACH))) {
                    if (series.size() >= SERIES_MAX) break;
                    if (!series.contains(q) && level.getBlockEntity(q) instanceof ReductionPotBlockEntity) {
                        BlockPos im = q.immutable();
                        series.add(im);
                        todo.add(im);
                    }
                }
            }
        }
        for (BlockPos p : series) if (level.getBlockEntity(p) instanceof ReductionPotBlockEntity r) out.add(r);
        return out;
    }

    // --- процесс ---

    @Override
    protected void process(ServerLevel level, double dtPhys) {
        double dt = TICK_S;
        // бункер глинозёма и точечный питатель
        while (hopperKg < ReductionPot.FEEDER_KG_S * dt * 2 && pot.bathCharged()
                && !takeOne(s -> s.is(PcItems.ALUMINA.get())).isEmpty()) hopperKg += NonferrousUnits.bulkKg();
        double feed = Math.min(Math.min(pot.aluminaWanted(), ReductionPot.FEEDER_KG_S * dt), hopperKg);
        pot.addAlumina(feed);
        hopperKg -= feed;
        if (pot.anodeKg() < ANODE_RESERVE_KG && !takeOne(s -> s.is(PcItems.CARBON_ANODE.get())).isEmpty())
            pot.addAnodeKg(NonferrousUnits.bulkKg());
        if ((!pot.bathCharged() || pot.fluorideKg() < FLUORIDE_RESERVE_KG) && pot.state() != State.FROZEN
                && pot.state() != State.CATHODE_FAILURE && !takeOne(s -> s.is(PcItems.CRYOLITE.get())).isEmpty())
            pot.addCryoliteKg(NonferrousUnits.bulkKg());
        pot.step(dt, on ? amps() : 0);
        // ковш: металл сверх чушки — в склад
        double ingot = NonferrousUnits.ingotKg();
        while (pot.tappableKg(0) >= ingot && emit(PcItems.ALUMINIUM_INGOT.get())) pot.tap(ingot);
        if (pot.lastCf4KgS() > 0) {
            EmissionLedger ledger = AirQualityService.ledger(level);
            String id = "reduction_pot@" + worldPosition.toShortString();
            ledger.record(id, EmissionLedger.Category.PROCESS, EmissionLedger.Pollutant.CF4, pot.lastCf4KgS(), dt);
            ledger.record(id, EmissionLedger.Category.PROCESS, EmissionLedger.Pollutant.C2F6, pot.lastC2f6KgS(), dt);
        }
        if (pot.state() != lastState) onStateChange(level, lastState, pot.state());
        lastState = pot.state();
        if (level.getGameTime() % 10 == 0) {
            BlockState st = getBlockState();
            boolean alarm = pot.state() == State.ANODE_EFFECT;
            if (st.hasProperty(PcProperties.ALARM) && st.getValue(PcProperties.ALARM) != alarm)
                level.setBlock(worldPosition, st.setValue(PcProperties.ALARM, alarm), Block.UPDATE_CLIENTS);
        }
    }

    private void onStateChange(ServerLevel level, State from, State to) {
        switch (to) {
            case ANODE_EFFECT -> near(level, NonferrousText.msg("pot.anode_effect").withStyle(ChatFormatting.GOLD));
            case FREEZING -> near(level, NonferrousText.msg("pot.freezing").withStyle(ChatFormatting.GOLD));
            case FROZEN -> near(level, NonferrousText.msg("pot.frozen").withStyle(ChatFormatting.RED));
            case CATHODE_FAILURE -> {
                near(level, NonferrousText.msg("pot.cathode_failure").withStyle(ChatFormatting.RED));
                // металл уходит сквозь подину: огонь у основания ванны
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos f = worldPosition.relative(d, 2);
                    if (level.isEmptyBlock(f)) level.setBlock(f, BaseFireBlock.getState(level, f), Block.UPDATE_ALL);
                }
            }
            default -> {
            }
        }
    }

    private void near(ServerLevel level, net.minecraft.network.chat.Component msg) {
        for (Player p : level.getEntitiesOfClass(Player.class, new AABB(worldPosition).inflate(24))) p.displayClientMessage(msg, false);
    }

    /** Капремонт: из загрузки берутся подовые блоки и огнеупор. */
    public boolean reline() {
        if (pot.state() != State.FROZEN && pot.state() != State.CATHODE_FAILURE) return false;
        if (count(s -> s.is(PcItems.CATHODE_BLOCK.get())) < RELINE_CATHODES || count(s -> s.is(PcItems.REFRACTORY_BRICK.get())) < RELINE_BRICKS)
            return false;
        for (int i = 0; i < RELINE_CATHODES; i++) takeOne(s -> s.is(PcItems.CATHODE_BLOCK.get()));
        for (int i = 0; i < RELINE_BRICKS; i++) takeOne(s -> s.is(PcItems.REFRACTORY_BRICK.get()));
        hopperKg = 0;
        lastState = State.COLD;
        setChanged();
        return pot.reline();
    }

    @Override
    protected boolean running() {
        State s = pot.state();
        return s == State.NORMAL || s == State.ANODE_EFFECT || s == State.FREEZING;
    }

    @Override
    @Nullable
    public IFluidHandler fluids(@Nullable Direction side) {
        return null;
    }

    @Override
    public PanelLayout panelLayout() {
        return NonferrousPanels.POT;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[NonferrousPanels.T_C] = (int) Math.round(pot.tempK() - 273.15);
        v[NonferrousPanels.A] = (int) Math.round(pot.aluminaFraction() * 1000);
        v[NonferrousPanels.B] = (int) Math.round(pot.lastVoltage() * 100);
        v[NonferrousPanels.C] = (int) Math.round(pot.lastAmps() / 1000);
        v[NonferrousPanels.D] = (int) Math.round(pot.lastAlKgS() * 36000);
        v[NonferrousPanels.E] = pot.state().ordinal();
        v[NonferrousPanels.TANK] = (int) Math.round(Math.min(1, pot.anodeKg() / (4 * NonferrousUnits.bulkKg())) * 1000);
        v[NonferrousPanels.TANK2] = (int) Math.round(Math.min(1, pot.metalKg() / NonferrousUnits.ingotKg()) * 1000);
        v[NonferrousPanels.TANK3] = (int) Math.round(pot.currentSetA() / 1000);
        State s = pot.state();
        v[NonferrousPanels.FLAGS] = (on ? 1 << NonferrousPanels.F_ON : 0) | (running() ? 1 << NonferrousPanels.F_RUN : 0)
                | (pot.bathCharged() && pot.aluminaFraction() < 0.022 ? 1 << NonferrousPanels.F_WARN : 0)
                | (s == State.FREEZING || s == State.COLD || s == State.STARTUP ? 1 << NonferrousPanels.F_AUX : 0)
                | (s == State.ANODE_EFFECT || s == State.FROZEN || s == State.CATHODE_FAILURE ? 1 << NonferrousPanels.F_ALARM : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case NonferrousPanels.B_RUN -> use(player);
            case NonferrousPanels.B_DOWN -> pot.setCurrentSetA(pot.currentSetA() - 10e3);
            case NonferrousPanels.B_UP -> pot.setCurrentSetA(pot.currentSetA() + 10e3);
            case NonferrousPanels.B_AUX -> player.displayClientMessage(NonferrousText.msg(reline() ? "pot.relined" : "pot.reline_refused",
                    RELINE_CATHODES, RELINE_BRICKS), true);
            default -> {
            }
        }
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putDouble("hopper", hopperKg);
        MutableStateTree t = new MutableStateTree();
        pot.save(new TreeStateWriter(t));
        tag.put("pot", NbtStateTrees.toNbt(t));
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        hopperKg = tag.getDouble("hopper");
        if (tag.contains("pot")) pot.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("pot"))));
        lastState = pot.state();
    }
}

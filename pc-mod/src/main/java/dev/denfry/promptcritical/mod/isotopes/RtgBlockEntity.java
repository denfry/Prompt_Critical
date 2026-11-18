package dev.denfry.promptcritical.mod.isotopes;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.environment.ActivityData;
import dev.denfry.promptcritical.mod.environment.EnvironmentService;
import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.ironworks.IronworksBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.isotopes.Rtg;
import dev.denfry.promptcritical.sim.radiation.ActivityInventory;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Термоэлектрический РИТЭГ {@code rtg_thermoelectric} (12 §12.5): источник тепла — предмет с активностью
 * ({@code sr90_heat_source} — как «Бета-М», {@code pu238_heat_source} — как GPHS), тепло — остаточное
 * тепловыделение его нуклидов, поэтому мощность падает с периодом полураспада сама. Преобразователь рассчитан на
 * тепло источника при первой зарядке ({@link Rtg#steady}): со спадом тепла остывает горячий спай и падает КПД, а
 * термоэлементы стареют на {@code isotopes.rtg_te_degradation_per_year}. Холодная сторона — воздух биома или вода
 * рядом (морские РИТЭГи охлаждаются лучше).
 *
 * <p>Электричество питает потребителей без сети в радиусе {@value #LAMP_RANGE} блоков: светильники (пока хватает
 * мощности) и выпрямители реле {@code relay_power_supply} вплотную — телемеханику удалённой скважины. Защита корпуса
 * (обеднённый уран и сталь у «Бета-М», полиэтилен у плутониевого) ослабляет излучение источника; разломанный РИТЭГ
 * роняет источник без защиты — он излучает как предмет ({@link RadiationService}).
 */
public final class RtgBlockEntity extends IronworksBlockEntity implements PanelHost {
    public static final int PERIOD = 20, LAMP_RANGE = 16;
    /**
     * Буферный аккумулятор маячного РИТЭГа (12 §12.5.1): «Бета-М» на 10 Вт питал маячный огонь через аккумулятор —
     * днём РИТЭГ заряжает, ночью огонь берёт больше, чем РИТЭГ даёт. 1 кВт·ч ≈ сутки работы [оценка].
     */
    public static final double BUFFER_J = 3.6e6;
    /**
     * Пропускание защиты корпуса для γ и нейтронов: у «Бета-М» сотни килограммов обеднённого урана — мощность дозы на
     * поверхности ≈ 1 мЗв/ч при голом источнике в десятки Гр/ч на метре (10⁻⁴); плутониевый — 2–5 см полиэтилена.
     */
    public static final double SHIELD_SR = 1e-4, SHIELD_PU = 0.3;
    /** Расчётные тепловые мощности базовых преобразователей, Вт. */
    public static final double BETA_M_DESIGN_W = 230, SIGE_DESIGN_W = 430;
    private static final Map<Level, Map<BlockPos, Boolean>> LOADED = new java.util.WeakHashMap<>();

    private final ItemStackHandler source = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isHeatSource(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private double designW;
    private long ageTicks;
    private Rtg.State state = Rtg.State.OFF;
    private boolean overtempWarned;
    /** Потребители за текущий период: позиция → Вт, игровое время запроса. */
    private final Map<BlockPos, long[]> loads = new HashMap<>();
    private double storedJ;
    private double loadW;

    public RtgBlockEntity(BlockPos pos, BlockState state) {
        super(IsotopeBlockEntities.RTG.get(), pos, state);
    }

    public static boolean isHeatSource(ItemStack s) {
        return (s.is(PcItems.SR90_HEAT_SOURCE.get()) || s.is(PcItems.PU238_HEAT_SOURCE.get())) && s.has(ActivityData.ACTIVITY.get());
    }

    public Rtg.State state() {
        return state;
    }

    public ItemStack heatSource() {
        return source.getStackInSlot(0);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) LOADED.computeIfAbsent(level, l -> new HashMap<>()).put(worldPosition.immutable(), true);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            Map<BlockPos, Boolean> m = LOADED.get(level);
            if (m != null) m.remove(worldPosition);
        }
    }

    private boolean plutonium() {
        return heatSource().is(PcItems.PU238_HEAT_SOURCE.get());
    }

    private Rtg.Converter converter() {
        boolean pu = plutonium();
        Rtg.Converter base = pu ? Rtg.SIGE : Rtg.BETA_M;
        double k = (pu ? SIGE_DESIGN_W : BETA_M_DESIGN_W) / Math.max(1, designW);
        return new Rtg.Converter(base.id(), base.zt(), base.rHotKPerW() * k, base.rColdKPerW() * k, base.maxHotK());
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        if (!(level instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        if (Math.floorMod(now + worldPosition.hashCode(), PERIOD) != 0) return;
        ItemStack st = heatSource();
        ActivityData a = st.get(ActivityData.ACTIVITY.get());
        if (a == null) {
            // термоэлементы стареют и без источника не молодеют; расчёт преобразователя — под следующий источник
            state = Rtg.State.OFF;
            designW = 0;
        } else {
            ActivityInventory inv = a.inventory(now);
            double heat = inv.decayHeatW();
            if (designW <= 0) designW = heat;
            ageTicks += PERIOD;
            boolean water = false;
            for (Direction d : Direction.values()) water |= sl.getFluidState(worldPosition.relative(d)).is(FluidTags.WATER);
            double ambientK = water ? 278.15 : EnvironmentService.sample(sl, worldPosition, 1).airTempC() + 273.15;
            double years = ageTicks * ActivityData.GAME_S_PER_TICK / (365.25 * 86400);
            state = Rtg.steady(converter(), heat, ambientK, water ? 0.3 : 1, years, IsotopeComponents.rtgSystemFactor(),
                    IsotopeComponents.rtgDegradationPerYear());
            if (state.overtemp() && !overtempWarned) {
                overtempWarned = true;
                for (ServerPlayer p : sl.players())
                    if (p.blockPosition().closerThan(worldPosition, 32))
                        p.displayClientMessage(IsotopeText.msg("rtg_overtemp", Math.round(state.hotK() - 273.15)).withStyle(ChatFormatting.RED), false);
            } else if (!state.overtemp()) overtempWarned = false;
            RadiationService.source(sl, Vec3.atCenterOf(worldPosition), inv.source().scaled(plutonium() ? SHIELD_PU : SHIELD_SR));
        }
        loads.values().removeIf(v -> v[1] < now - 2L * PERIOD);
        loadW = 0;
        for (long[] v : loads.values()) loadW += v[0];
        // избыток мощности — в буферный аккумулятор (PERIOD тиков — 1 с)
        storedJ = Math.min(BUFFER_J, storedJ + Math.max(0, state.electricW() - loadW) * PERIOD / 20.0);
        BlockState s = getBlockState();
        boolean active = state.electricW() > 0.5;
        if (s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT) != active)
            level.setBlock(worldPosition, s.setValue(BlockStateProperties.LIT, active), Block.UPDATE_ALL);
    }

    /** Заряд буферного аккумулятора, Дж. */
    public double storedJ() {
        return storedJ;
    }

    /** Для тестов: зарядить буфер. */
    public void setStoredJ(double j) {
        storedJ = Math.max(0, Math.min(BUFFER_J, j));
    }

    /** Электрическая мощность, Вт. */
    public double electricW() {
        return state.electricW();
    }

    /**
     * Светильник просит {@code watts}: горит, если вместе с уже питаемыми потребителями (кроме него самого) мощности
     * хватает. Детерминированно: кто раньше попросил, тот и питается.
     */
    boolean feed(BlockPos consumer, int watts, long now) {
        long[] mine = loads.get(consumer);
        double others = 0;
        for (Map.Entry<BlockPos, long[]> e : loads.entrySet())
            if (!e.getKey().equals(consumer) && e.getValue()[1] >= now - 2L * PERIOD) others += e.getValue()[0];
        double deficitW = others + watts - state.electricW();
        if (deficitW > 0) {
            // недостаток покрывает буферный аккумулятор, пока он не разряжен
            double need = deficitW * PERIOD / 20.0;
            if (storedJ < need) {
                if (mine != null) loads.remove(consumer);
                return false;
            }
            storedJ -= need;
        }
        loads.put(consumer.immutable(), new long[] {watts, now});
        return true;
    }

    /**
     * Питание светильника от ближайшего РИТЭГа в радиусе {@value #LAMP_RANGE} блоков (12 §12.5.1: маяки).
     *
     * @return горит ли светильник
     */
    public static boolean feedLamp(Level level, BlockPos lamp, int watts, long now) {
        Map<BlockPos, Boolean> m = LOADED.get(level);
        if (m == null || m.isEmpty()) return false;
        BlockPos best = null;
        long bd = Long.MAX_VALUE;
        for (BlockPos p : m.keySet()) {
            int dx = Math.abs(p.getX() - lamp.getX()), dy = Math.abs(p.getY() - lamp.getY()), dz = Math.abs(p.getZ() - lamp.getZ());
            if (dx > LAMP_RANGE || dy > LAMP_RANGE || dz > LAMP_RANGE) continue;
            long d = (long) dx * dx + (long) dy * dy + (long) dz * dz;
            if (d < bd || (d == bd && best != null && p.compareTo(best) < 0)) {
                best = p;
                bd = d;
            }
        }
        return best != null && level.isLoaded(best) && level.getBlockEntity(best) instanceof RtgBlockEntity r && r.feed(lamp, watts, now);
    }

    /** РИТЭГ вплотную к {@code consumer} (с источником или без). */
    public static boolean adjacent(Level level, BlockPos consumer) {
        for (Direction d : Direction.values())
            if (level.getBlockEntity(consumer.relative(d)) instanceof RtgBlockEntity) return true;
        return false;
    }

    /**
     * Вход выпрямителя реле от РИТЭГа вплотную, о.е.: доля мощности {@code demandW}, которую он покрывает.
     */
    public static double supplyPu(Level level, BlockPos consumer, double demandW, long now) {
        double best = 0;
        for (Direction d : Direction.values()) {
            BlockEntity be = level.getBlockEntity(consumer.relative(d));
            if (be instanceof RtgBlockEntity r && demandW > 0) {
                double free = r.state.electricW();
                for (Map.Entry<BlockPos, long[]> e : r.loads.entrySet())
                    if (!e.getKey().equals(consumer) && e.getValue()[1] >= now - 2L * PERIOD) free -= e.getValue()[0];
                double give = Math.max(0, Math.min(demandW, free));
                if (give > 0) r.loads.put(consumer.immutable(), new long[] {Math.round(give), now});
                best = Math.max(best, Math.min(1, give / demandW));
            }
        }
        return best;
    }

    // --- руки ---

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return source;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return isHeatSource(stack);
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        if (!isHeatSource(stack) || !heatSource().isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        source.setStackInSlot(0, stack.split(1));
        return ItemInteractionResult.SUCCESS;
    }

    /** Shift + ПКМ: вынуть источник (без защиты — облучение!). */
    @Override
    public InteractionResult use(Player player) {
        ItemStack s = heatSource();
        if (s.isEmpty()) return InteractionResult.PASS;
        source.setStackInSlot(0, ItemStack.EMPTY);
        if (!player.addItem(s)) player.drop(s, false);
        return InteractionResult.SUCCESS;
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return IsotopePanels.RTG;
    }

    @Override
    public IItemHandlerModifiable panelItems() {
        return source;
    }

    @Override
    public void readPanel(int[] out) {
        out[IsotopePanels.R_THERMAL_W] = (int) Math.round(state.thermalW());
        out[IsotopePanels.R_ELECTRIC_DW] = (int) Math.round(state.electricW() * 10);
        out[IsotopePanels.R_HOT_C] = state.thermalW() > 0 ? (int) Math.round(state.hotK() - 273.15) : 0;
        out[IsotopePanels.R_COLD_C] = state.thermalW() > 0 ? (int) Math.round(state.coldK() - 273.15) : 0;
        out[IsotopePanels.R_REMAIN_PCT] = designW > 0 ? (int) Math.round(state.thermalW() / designW * 1000) : 0;
        out[IsotopePanels.R_LOAD_DW] = (int) Math.round(loadW * 10);
        out[IsotopePanels.R_EFF_PERMILLE] = (int) Math.round(state.efficiency() * 1000);
        out[IsotopePanels.R_AGE_DAYS] = (int) Math.round(ageTicks * ActivityData.GAME_S_PER_TICK / 86400);
        int f = 0;
        if (state.overtemp()) f |= 1 << IsotopePanels.RF_OVERTEMP;
        if (state.electricW() > 0.5) f |= 1 << IsotopePanels.RF_ACTIVE;
        if (loadW > state.electricW() * 0.95 && loadW > 0) f |= 1 << IsotopePanels.RF_FULL;
        out[IsotopePanels.R_FLAGS] = f;
        out[IsotopePanels.R_KIND] = heatSource().isEmpty() ? 0 : plutonium() ? 2 : 1;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("stored_j", storedJ);
        tag.put("source", source.serializeNBT(registries));
        tag.putDouble("design_w", designW);
        tag.putLong("age", ageTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        storedJ = tag.getDouble("stored_j");
        if (tag.contains("source")) source.deserializeNBT(registries, tag.getCompound("source"));
        designW = tag.getDouble("design_w");
        ageTicks = tag.getLong("age");
    }
}

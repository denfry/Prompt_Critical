package dev.denfry.promptcritical.mod.greenhouse;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.environment.EnvironmentService;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.powerhouse.SteamTurbineBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.thermal.Greenhouse;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Регистр теплицы {@code greenhouse_coil} (17 §17.3.6). Стоит внутри остеклённого объёма и вплотную к конденсатору
 * паровой турбины: берёт тепло её отработавшего пара из охлаждающей воды и держит в теплице 18 °C. Объём находится
 * заливкой воздуха от регистра, как вент-зона, до {@value Greenhouse#MAX_VOLUME} блоков; ограждение — грани объёма:
 * стекло с U = 5,8 Вт/(м²·К), остальное — глухая стена. Растения внутри растут быстрее по кардинальной кривой
 * температуры, холоднее 5 °C — не растут (см. {@link GreenhouseGrowth}). Вне теплицы ванильный рост не трогается.
 *
 * <p>Наибольшая мощность регистра — {@value #COIL_MAX_W} Вт [оценка: оребрённый трубный регистр на 5 кг/с воды при
 * охлаждении на 10 К]. Отбор тепла не облегчает градирню: вакуум конденсатора по-прежнему считает турбина.
 */
public final class GreenhouseCoilBlockEntity extends DeviceBlockEntity implements FoundryDevice {
    public static final double COIL_MAX_W = 200e3;
    /** Пересчёт объёма, тиков. */
    public static final int RESCAN_TICKS = 600;

    private static final Map<Level, Set<GreenhouseCoilBlockEntity>> ACTIVE = new WeakHashMap<>();

    @Nullable
    private Greenhouse greenhouse;
    private double insideC = Double.NaN;
    private final LongOpenHashSet zone = new LongOpenHashSet();
    private final LongArrayList plants = new LongArrayList();
    private double glassM2, wallM2, ambientC, availableW;
    private int ticks;

    public GreenhouseCoilBlockEntity(BlockPos pos, BlockState state) {
        super(GreenhouseBlockEntities.GREENHOUSE_COIL.get(), pos, state);
    }

    /** Регистр, в теплице которого стоит {@code pos}; {@code null} — вне теплиц. */
    @Nullable
    static GreenhouseCoilBlockEntity at(Level level, BlockPos pos) {
        Set<GreenhouseCoilBlockEntity> s = ACTIVE.get(level);
        if (s == null) return null;
        long key = pos.asLong();
        for (GreenhouseCoilBlockEntity c : s)
            if (!c.isRemoved() && c.greenhouse != null && c.zone.contains(key)) return c;
        return null;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) ACTIVE.computeIfAbsent(level, l -> Collections.newSetFromMap(new WeakHashMap<>())).add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        Set<GreenhouseCoilBlockEntity> s = level == null ? null : ACTIVE.get(level);
        if (s != null) s.remove(this);
    }

    /** Температура внутри, °C; NaN — регистр не в теплице. */
    public double insideC() {
        return greenhouse == null ? Double.NaN : greenhouse.insideC();
    }

    /** Множитель роста растений внутри; 0 — рост остановлен. */
    public double growthMultiplier() {
        return greenhouse == null ? 1 : Greenhouse.growthMultiplier(greenhouse.insideC());
    }

    /** Задать температуру воздуха внутри, °C (испытания): ничего не делает, если регистр не в теплице. */
    public void setInsideC(double c) {
        if (greenhouse != null) greenhouse.setInsideC(c);
        insideC = c;
    }

    public boolean enclosed() {
        return greenhouse != null;
    }

    public int volume() {
        return zone.size();
    }

    public double glassM2() {
        return glassM2;
    }

    public double heatW() {
        return greenhouse == null ? 0 : greenhouse.heatW();
    }

    public int plants() {
        return plants.size();
    }

    /** Пересчитать объём теплицы заливкой от регистра. */
    public void rescan(ServerLevel sl) {
        zone.clear();
        plants.clear();
        glassM2 = wallM2 = 0;
        Set<Long> flood = flood(sl);
        if (flood == null) {
            if (greenhouse != null) insideC = greenhouse.insideC();
            greenhouse = null;
            return;
        }
        for (long l : flood) zone.add(l);
        for (long l : flood) {
            BlockPos p = BlockPos.of(l);
            BlockState s = sl.getBlockState(p);
            if (plant(s)) plants.add(l);
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (zone.contains(n.asLong()) || n.equals(worldPosition)) continue;
                if (glass(sl.getBlockState(n))) glassM2++;
                else wallM2++;
            }
        }
        if (glassM2 == 0) {
            zone.clear();
            plants.clear();
            greenhouse = null;
            return;
        }
        double ua = Greenhouse.U_SINGLE_GLASS * glassM2 + Greenhouse.U_OPAQUE * wallM2;
        double start = greenhouse != null ? greenhouse.insideC() : Double.isNaN(insideC) ? ambient(sl) : insideC;
        greenhouse = new Greenhouse(ua, zone.size(), start);
        insideC = start;
    }

    /** Воздух вокруг регистра: от верхней грани, затем от боковых; {@code null} — объём не замкнут или больше предела. */
    @Nullable
    private Set<Long> flood(ServerLevel sl) {
        for (Direction d : new Direction[] {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos start = worldPosition.relative(d);
            if (!open(sl, start)) continue;
            Set<Long> seen = new java.util.HashSet<>();
            ArrayDeque<BlockPos> q = new ArrayDeque<>();
            seen.add(start.asLong());
            q.add(start);
            while (!q.isEmpty()) {
                BlockPos p = q.poll();
                for (Direction e : Direction.values()) {
                    BlockPos n = p.relative(e);
                    if (!sl.isLoaded(n)) return null;
                    if (seen.contains(n.asLong()) || !open(sl, n)) continue;
                    seen.add(n.asLong());
                    if (seen.size() > Greenhouse.MAX_VOLUME) return null;
                    q.add(n);
                }
            }
            return seen;
        }
        return null;
    }

    private static boolean open(ServerLevel sl, BlockPos p) {
        BlockState s = sl.getBlockState(p);
        return s.isAir() || (s.getCollisionShape(sl, p).isEmpty() && s.getFluidState().isEmpty());
    }

    static boolean glass(BlockState s) {
        return s.is(Tags.Blocks.GLASS_BLOCKS) || s.is(Tags.Blocks.GLASS_PANES) || s.is(PcBlocks.GLASS_BLOCK.get())
                || s.is(PcBlocks.CLEANROOM_WINDOW.get());
    }

    static boolean plant(BlockState s) {
        return s.isRandomlyTicking() && (s.is(BlockTags.CROPS) || s.is(BlockTags.SAPLINGS) || s.getBlock() instanceof BonemealableBlock);
    }

    private double ambient(ServerLevel sl) {
        return EnvironmentService.sample(sl, worldPosition, 1).airTempC();
    }

    /** Тепло конденсаторов, которых регистр касается, Вт. */
    private double condenserHeatW(ServerLevel sl) {
        double w = 0;
        for (Direction d : Direction.values()) w += SteamTurbineBlockEntity.condenserHeatW(sl, worldPosition.relative(d));
        return w;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl)) return;
        if (ticks++ % RESCAN_TICKS == 0) rescan(sl);
        if (ticks % 20 != 0 || greenhouse == null) return;
        ambientC = ambient(sl);
        availableW = Math.min(COIL_MAX_W, condenserHeatW(sl));
        greenhouse.step(1.0, ambientC, availableW);
        insideC = greenhouse.insideC();
        growPlants(sl);
        if (ticks % 200 == 0) setChanged();
    }

    /**
     * Добавочный рост сверх ванильного: каждому растению за секунду — лишние случайные тики с вероятностью
     * (m − 1)·20·randomTickSpeed/4096, так что в среднем растения растут в m раз быстрее.
     */
    private void growPlants(ServerLevel sl) {
        double m = Greenhouse.growthMultiplier(greenhouse.insideC());
        if (m <= 1 || plants.isEmpty()) return;
        int speed = sl.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        double chance = (m - 1) * 20.0 * speed / 4096.0;
        for (int i = 0; i < plants.size(); i++) {
            if (sl.random.nextDouble() >= chance) continue;
            BlockPos p = BlockPos.of(plants.getLong(i));
            BlockState s = sl.getBlockState(p);
            if (plant(s)) s.randomTick(sl, p, sl.random);
        }
    }

    @Override
    public InteractionResult use(Player player) {
        if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        rescan(sl);
        if (greenhouse == null) {
            player.displayClientMessage(GreenhouseText.msg("not_enclosed").withStyle(ChatFormatting.GOLD), false);
            return InteractionResult.SUCCESS;
        }
        double amb = ambient(sl), avail = Math.min(COIL_MAX_W, condenserHeatW(sl));
        player.displayClientMessage(GreenhouseText.msg("status", zone.size(), (int) glassM2, (int) wallM2, f1(greenhouse.insideC()), f1(amb)), false);
        if (avail <= 0) player.displayClientMessage(GreenhouseText.msg("no_condenser").withStyle(ChatFormatting.GOLD), false);
        else player.displayClientMessage(GreenhouseText.msg("heat", f1(greenhouse.heatW() / 1e3), f1(avail / 1e3), f1(greenhouse.lossW() / 1e3),
                String.format(Locale.ROOT, "%.2f", Greenhouse.coilWaterKgS(greenhouse.heatW()))), false);
        double m = Greenhouse.growthMultiplier(greenhouse.insideC());
        if (m <= 0) player.displayClientMessage(GreenhouseText.msg("too_cold").withStyle(ChatFormatting.AQUA), false);
        else player.displayClientMessage(GreenhouseText.msg("growth", String.format(Locale.ROOT, "%.2f", m), plants.size()), false);
        return InteractionResult.SUCCESS;
    }

    private static String f1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        double t = greenhouse != null ? greenhouse.insideC() : insideC;
        if (!Double.isNaN(t)) tag.putDouble("inside_c", t);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        insideC = tag.contains("inside_c") ? tag.getDouble("inside_c") : Double.NaN;
    }

    @Override
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return false;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}

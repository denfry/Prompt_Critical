package dev.denfry.promptcritical.mod.acoustics;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.acoustics.BarrierMaterial;
import dev.denfry.promptcritical.sim.acoustics.NoiseExposure;
import dev.denfry.promptcritical.sim.acoustics.SoundLevel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Шум машин у игроков и износ их подшипников (04a §4a.2, §4a.7; 13 §13.7). Раз в секунду для каждого игрока:
 *
 * <ul>
 *   <li>машины Create в {@value #RANGE_M} м (чанки вокруг игрока) делают шаг износа — каждая один раз за проход;
 *       заклинивший подшипник ломает машину;</li>
 *   <li>уровень у уха — энергетическая сумма {@value #MAX_SOURCES} самых громких машин с расхождением и
 *       звукоизоляцией блоков на луче ({@link SoundLevel}, {@link BarrierMaterial});</li>
 *   <li>в наушниках ({@link HearingProtectionItem}) — на {@value NoiseExposure#PROTECTOR_DB} дБ тише; доза копится по
 *       правилу 3 дБ ({@link NoiseExposure}) и спадает с периодом в игровые сутки [BALANCE];</li>
 *   <li>сверх дозы — утомление от шума (усталость), громче {@value NoiseExposure#STUN_DB} дБ(А) — оглушение;
 *       строка над хотбаром предупреждает о шуме выше 85 дБ(А) без защиты.</li>
 * </ul>
 */
public final class NoiseService {
    public static final double RANGE_M = 32;
    static final int MAX_SOURCES = 24;
    /** Ниже этого уровня без стен источник не считается (и луч не трассируется), дБ(А). */
    static final double AUDIBLE_DB = 30;
    /** Период спада дозы, с реального времени: игровые сутки [BALANCE]. */
    public static final double RECOVERY_HALF_LIFE_S = 1200;
    static final double STEP_M = 0.25;
    static final String TAG = PromptCritical.MOD_ID + ":noise_dose";
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();
    private static final Map<ServerPlayer, Double> LAST_DB = new WeakHashMap<>();
    private static final Map<ServerPlayer, Warning> SHOWN = new WeakHashMap<>();

    private enum Warning { QUIET, LOUD, PROTECTED, OVERDOSE }

    /** Источник шума: центр машины и её L_WA. */
    public record Source(BlockPos pos, double lwDb) {
    }

    static {
        text("loud", "Noise %s dB(A): put on hearing protection", "Шум %s дБ(А): наденьте противошумные наушники");
        text("protected", "Noise %s dB(A), %s dB(A) under the ear muffs", "Шум %s дБ(А), под наушниками %s дБ(А)");
        text("overdose", "Your ears are ringing: the day's noise dose is used up (%s%%)",
                "Звенит в ушах: дневная доза шума выбрана (%s%%)");
        text("stunned", "Deafened by %s dB(A)", "Оглушение: %s дБ(А)");
        text("seized", "A bearing seized and wrecked the machine", "Подшипник заклинило — машина разрушена");
    }

    private NoiseService() {
    }

    private static void text(String key, String en, String ru) {
        LANG.put("message.promptcritical.noise." + key, new String[] {en, ru});
    }

    public static Map<String, String[]> lang() {
        return LANG;
    }

    private static net.minecraft.network.chat.MutableComponent msg(String key, Object... args) {
        return Component.translatable("message.promptcritical.noise." + key, args);
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel s && s.getGameTime() % 20 == 0) tick(s);
        });
    }

    // --- источники ---

    /** Машины с вращением в радиусе от точки (по загруженным чанкам). */
    static List<BlockPos> machinesNear(ServerLevel level, Vec3 at) {
        List<BlockPos> out = new ArrayList<>();
        int r = (int) Math.ceil(RANGE_M / 16);
        int cx = BlockPos.containing(at).getX() >> 4, cz = BlockPos.containing(at).getZ() >> 4;
        double r2 = RANGE_M * RANGE_M;
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values())
                    if (be instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity
                            && Vec3.atCenterOf(be.getBlockPos()).distanceToSqr(at) <= r2)
                        out.add(be.getBlockPos());
            }
        return out;
    }

    /** Работающие машины вокруг точки с их L_WA. */
    public static List<Source> sources(ServerLevel level, Vec3 at) {
        List<Source> out = new ArrayList<>();
        for (BlockPos p : machinesNear(level, at)) {
            MachineAcoustics.Probe probe = MachineAcoustics.probe(level, p);
            if (probe == null) continue;
            double lw = MachineAcoustics.soundPowerDb(level, p, probe);
            if (Double.isFinite(lw)) out.add(new Source(p, lw));
        }
        return out;
    }

    /** Уровень звукового давления в точке от машин вокруг, дБ(А); тишина — −∞. */
    public static double levelAt(ServerLevel level, Vec3 ear) {
        return levelAt(level, ear, sources(level, ear));
    }

    static double levelAt(ServerLevel level, Vec3 ear, List<Source> sources) {
        record Heard(Source s, double d, double open) {
        }
        List<Heard> heard = new ArrayList<>();
        for (Source s : sources) {
            double d = Vec3.atCenterOf(s.pos()).distanceTo(ear);
            double open = SoundLevel.atDistance(s.lwDb(), d, 0);
            if (open >= AUDIBLE_DB) heard.add(new Heard(s, d, open));
        }
        heard.sort(Comparator.comparingDouble(Heard::open).reversed());
        int n = Math.min(heard.size(), MAX_SOURCES);
        double[] levels = new double[n];
        for (int i = 0; i < n; i++) {
            Heard h = heard.get(i);
            levels[i] = SoundLevel.atDistance(h.s.lwDb(), h.d, occlusion(level, Vec3.atCenterOf(h.s.pos()), ear));
        }
        return SoundLevel.sum(levels);
    }

    /** Звукоизоляция блоков на луче, дБ (без блока источника и блока слушателя). */
    public static double occlusion(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1e-6) return 0;
        int n = (int) Math.ceil(len / STEP_M);
        BlockPos src = BlockPos.containing(from), dst = BlockPos.containing(to);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        long last = Long.MIN_VALUE;
        double tl = 0;
        for (int i = 0; i < n; i++) {
            Vec3 at = from.add(d.scale((i + 0.5) / n));
            p.set(at.x, at.y, at.z);
            if (p.asLong() == last || p.equals(src) || p.equals(dst)) continue;
            last = p.asLong();
            if (!level.isLoaded(p)) continue;
            tl += material(level.getBlockState(p)).tlDb;
            if (tl >= SoundLevel.MAX_OCCLUSION_DB) return SoundLevel.MAX_OCCLUSION_DB;
        }
        return tl;
    }

    /** Материал блока для звукоизоляции (04a §4a.2.2). */
    public static BarrierMaterial material(BlockState s) {
        if (s.getFluidState().is(FluidTags.WATER)) return BarrierMaterial.WATER;
        if (s.isAir()) return BarrierMaterial.AIR;
        String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        if (id.contains("concrete")) return BarrierMaterial.CONCRETE;
        if (id.contains("pane")) return BarrierMaterial.PANE;
        if (id.contains("glass")) return BarrierMaterial.GLASS;
        if (id.contains("wool") || id.contains("carpet")) return BarrierMaterial.SOFT;
        if (!s.blocksMotion()) return s.isSolid() ? BarrierMaterial.PANE : BarrierMaterial.PLANT;
        SoundType t = s.getSoundType();
        if (t == SoundType.METAL || t == SoundType.COPPER || t == SoundType.NETHERITE_BLOCK || t == SoundType.ANVIL
                || t == SoundType.CHAIN || id.contains("iron") || id.contains("steel")) return BarrierMaterial.METAL;
        if (t == SoundType.WOOD || t == SoundType.NETHER_WOOD || t == SoundType.BAMBOO_WOOD || t == SoundType.CHERRY_WOOD)
            return BarrierMaterial.WOOD;
        return BarrierMaterial.STONE;
    }

    // --- игроки ---

    /** Противошумные наушники на голове. */
    public static boolean protectedEars(Player p) {
        var head = p.getItemBySlot(EquipmentSlot.HEAD);
        if (head.getItem() instanceof HearingProtectionItem) return true;
        String id = BuiltInRegistries.ITEM.getKey(head.getItem()).getPath();
        return id.contains("hearing_protection") || id.contains("ear_muff") || id.contains("earmuff");
    }

    /** Дневная доза шума игрока (1 — допустимая). */
    public static double dose(Player p) {
        return p.getPersistentData().getDouble(TAG);
    }

    public static void setDose(Player p, double dose) {
        p.getPersistentData().putDouble(TAG, Math.max(0, dose));
    }

    /** Уровень у игрока на последнем замере (без наушников), дБ(А); −∞ — тихо или ещё не мерили. */
    public static double lastLevelDb(ServerPlayer p) {
        return LAST_DB.getOrDefault(p, Double.NEGATIVE_INFINITY);
    }

    static void tick(ServerLevel level) {
        Set<BlockPos> stepped = new HashSet<>();
        for (ServerPlayer p : List.copyOf(level.players())) {
            if (p.isSpectator()) continue;
            Vec3 ear = p.getEyePosition();
            List<Source> sources = new ArrayList<>();
            for (BlockPos pos : machinesNear(level, ear)) {
                MachineAcoustics.Probe probe = MachineAcoustics.probe(level, pos);
                if (probe == null || !probe.machine()) continue;
                if (stepped.add(pos.immutable()) && MachineAcoustics.stepWear(level, pos, probe)) {
                    seize(level, pos);
                    continue;
                }
                double lw = MachineAcoustics.soundPowerDb(level, pos, probe);
                if (Double.isFinite(lw)) sources.add(new Source(pos.immutable(), lw));
            }
            double l = levelAt(level, ear, sources);
            LAST_DB.put(p, l);
            expose(p, l, 1);
        }
    }

    /** Подшипник заклинило: вал встаёт рывком, машина срывается с места (ломается с выпадением). */
    static void seize(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.5f, 0.6f);
        level.destroyBlock(pos, true);
        MachineAcoustics.forget(level, pos);
        for (ServerPlayer p : level.players())
            if (p.blockPosition().closerThan(pos, RANGE_M)) p.displayClientMessage(msg("seized").withStyle(ChatFormatting.RED), false);
    }

    /**
     * Шаг воздействия шума на игрока.
     *
     * @param levelDb уровень у уха без защиты, дБ(А)
     * @param dtS     длительность, с
     */
    public static void expose(ServerPlayer p, double levelDb, double dtS) {
        if (p.isCreative() || p.isSpectator()) return;
        boolean muffs = protectedEars(p);
        double atEar = Double.isFinite(levelDb) ? NoiseExposure.atEar(levelDb, muffs) : Double.NEGATIVE_INFINITY;
        double dose = NoiseExposure.step(dose(p), dtS, atEar, RECOVERY_HALF_LIFE_S);
        setDose(p, dose);
        int ticks = (int) Math.round(dtS * 20) + 20;
        if (atEar >= NoiseExposure.STUN_DB) {
            p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0, false, true));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1, false, true));
            p.displayClientMessage(msg("stunned", fmt(atEar)).withStyle(ChatFormatting.RED), true);
        }
        if (dose > 1) p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 0, false, true));
        Warning w = dose > 1 ? Warning.OVERDOSE
                : levelDb >= NoiseExposure.UPPER_ACTION_DB ? (muffs ? Warning.PROTECTED : Warning.LOUD) : Warning.QUIET;
        if (SHOWN.get(p) == w) return;
        SHOWN.put(p, w);
        switch (w) {
            case LOUD -> p.displayClientMessage(msg("loud", fmt(levelDb)).withStyle(ChatFormatting.GOLD), true);
            case PROTECTED -> p.displayClientMessage(msg("protected", fmt(levelDb), fmt(atEar)).withStyle(ChatFormatting.GREEN), true);
            case OVERDOSE -> p.displayClientMessage(msg("overdose", String.format(Locale.ROOT, "%.0f", dose * 100))
                    .withStyle(ChatFormatting.RED), false);
            default -> {
            }
        }
    }

    static String fmt(double db) {
        return Double.isFinite(db) ? String.format(Locale.ROOT, "%.0f", db) : "<30";
    }
}

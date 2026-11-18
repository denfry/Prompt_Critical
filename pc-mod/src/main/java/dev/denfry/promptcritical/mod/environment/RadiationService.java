package dev.denfry.promptcritical.mod.environment;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.radiation.DoseRate;
import dev.denfry.promptcritical.sim.radiation.PointSource;
import dev.denfry.promptcritical.sim.radiation.RadiationSickness;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Ионизирующее излучение в мире: точечные источники γ и нейтронов (работающий котёл, остаточное тепловыделение,
 * отработанный материал) и вспышки (СЦР). Раз в секунду для каждого игрока считается мощность дозы от всех источников
 * в {@value #RANGE_M} м с ослаблением блоками на прямой ({@link ShieldBlocks}, {@link DoseRate}), плюс фон
 * {@value #BACKGROUND_USV_H} мкЗв/ч. Доза копится в данных игрока: полная (для дозиметра) и острая — она спадает
 * с периодом {@value RadiationSickness#RECOVERY_HALF_DAYS} игровых суток и задаёт лучевую болезнь
 * ({@link RadiationSickness}). Зиверт для γ и нейтронов приравнен к грею (взвешивающие коэффициенты уже в коэффициенте
 * перевода нейтронов).
 */
public final class RadiationService {
    public static final double RANGE_M = 200, BACKGROUND_USV_H = 0.1;
    static final int HOLD_TICKS = 40;
    /** Игровые секунды за тик (тик — 3,6 с игрового времени): для восстановления. */
    static final double GAME_S_PER_TICK = 3.6;
    static final String TAG = PromptCritical.MOD_ID + ":dose";
    public static final ResourceKey<DamageType> RADIATION = ResourceKey.create(Registries.DAMAGE_TYPE, PromptCritical.id("radiation"));
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();
    private static final Map<ServerLevel, Map<Vec3, Held>> SOURCES = new WeakHashMap<>();

    private record Held(PointSource source, long until) {
    }

    static {
        text("mild", "Nausea and weakness: acute radiation sickness, grade I (over 1 Gy)",
                "Тошнота и слабость: острая лучевая болезнь I степени (больше 1 Гр)");
        text("moderate", "Vomiting, fever: acute radiation sickness, grade II (over 2 Gy)",
                "Рвота, жар: острая лучевая болезнь II степени (больше 2 Гр)");
        text("severe", "Bone marrow failure: acute radiation sickness, grade III (over 4 Gy)",
                "Костный мозг не работает: острая лучевая болезнь III степени (больше 4 Гр)");
        text("extreme", "Lethal dose: acute radiation sickness, grade IV (over 6 Gy)",
                "Смертельная доза: острая лучевая болезнь IV степени (больше 6 Гр)");
        text("burst", "BLUE FLASH: criticality excursion. Your dose: %s Sv", "ГОЛУБАЯ ВСПЫШКА: СЦР. Ваша доза: %s Зв");
        LANG.put("death.attack.promptcritical.radiation", new String[] {"%1$s died of radiation sickness", "%1$s умер от лучевой болезни"});
        LANG.put("death.attack.promptcritical.radiation.player", new String[] {"%1$s died of radiation sickness while fighting %2$s",
                "%1$s умер от лучевой болезни, спасаясь от %2$s"});
    }

    private RadiationService() {
    }

    private static void text(String key, String en, String ru) {
        LANG.put("message.promptcritical.radiation." + key, new String[] {en, ru});
    }

    public static Map<String, String[]> lang() {
        return LANG;
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable("message.promptcritical.radiation." + key, args);
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel s) tick(s);
        });
    }

    /** Непрерывный источник в точке; источник обновляет его хотя бы раз в {@value #HOLD_TICKS} тиков. */
    public static void source(ServerLevel level, Vec3 at, PointSource src) {
        if (src.empty()) return;
        SOURCES.computeIfAbsent(level, l -> new HashMap<>()).put(at, new Held(src, level.getGameTime() + HOLD_TICKS));
    }

    /**
     * Вспышка: полное число частиц; доза достаётся всем игрокам в радиусе сразу, с ослаблением стенами.
     *
     * @param announce сообщить игроку дозу общим текстом (иначе сообщает вызывающий по возвращённым дозам)
     * @return доза каждого облучённого игрока, Зв
     */
    public static Map<ServerPlayer, Double> burst(ServerLevel level, Vec3 at, PointSource burst, boolean announce) {
        Map<ServerPlayer, Double> doses = new LinkedHashMap<>();
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceToSqr(at) > RANGE_M * RANGE_M || p.isSpectator()) continue;
            Vec3 eye = p.getEyePosition();
            double sv = DoseRate.burstSv(burst, eye.distanceTo(at), ShieldBlocks.between(level, at, eye));
            doses.put(p, sv);
            if (p.isCreative()) continue;
            add(p, sv);
            if (announce) p.displayClientMessage(msg("burst", String.format(java.util.Locale.ROOT, "%.2f", sv)).withStyle(ChatFormatting.RED), false);
            effects(p, level);
        }
        return doses;
    }

    /** Мощность дозы в точке от всех источников, Зв/ч (без фона). */
    public static double rateSvH(ServerLevel level, Vec3 at) {
        Map<Vec3, Held> m = SOURCES.get(level);
        if (m == null) return 0;
        double sum = 0;
        long now = level.getGameTime();
        for (Map.Entry<Vec3, Held> e : m.entrySet()) {
            if (e.getValue().until < now) continue; // просрочен: не зависим от того, успел ли тик уровня его вычистить
            Vec3 src = e.getKey();
            double r = src.distanceTo(at);
            if (r > RANGE_M) continue;
            List<DoseRate.Layer> layers = ShieldBlocks.between(level, src, at);
            sum += DoseRate.totalSvH(e.getValue().source, r, layers);
        }
        return sum;
    }

    /** Полная накопленная доза игрока, Зв. */
    public static double totalSv(ServerPlayer p) {
        return data(p).getDouble("total");
    }

    /** Острая доза игрока, Зв (Гр). */
    public static double acuteSv(ServerPlayer p) {
        return data(p).getDouble("acute");
    }

    /** Мощность дозы у игрока на последнем пересчёте, Зв/ч (с фоном). */
    public static double lastRateSvH(ServerPlayer p) {
        return data(p).getDouble("rate");
    }

    private static CompoundTag data(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(TAG)) root.put(TAG, new CompoundTag());
        return root.getCompound(TAG);
    }

    static void add(ServerPlayer p, double sv) {
        CompoundTag d = data(p);
        d.putDouble("total", d.getDouble("total") + sv);
        d.putDouble("acute", d.getDouble("acute") + sv);
    }

    private static void tick(ServerLevel level) {
        long now = level.getGameTime();
        Map<Vec3, Held> m = SOURCES.get(level);
        if (m != null) m.values().removeIf(h -> h.until < now);
        if (now % 20 != 0) return;
        for (ServerPlayer p : level.players()) {
            double rate = rateSvH(level, p.getEyePosition()) + BACKGROUND_USV_H * 1e-6;
            CompoundTag d = data(p);
            d.putDouble("rate", rate);
            if (p.isCreative() || p.isSpectator()) continue;
            add(p, rate / 3600);
            d.putDouble("acute", RadiationSickness.recover(d.getDouble("acute"), 20 * GAME_S_PER_TICK / 86400));
            effects(p, level);
        }
    }

    /** Детерминированные эффекты по острой дозе; сообщение — при переходе на новую ступень. */
    private static void effects(ServerPlayer p, ServerLevel level) {
        CompoundTag d = data(p);
        double acute = d.getDouble("acute");
        RadiationSickness.Stage st = RadiationSickness.stage(acute);
        int shown = d.getInt("stage");
        if (st.ordinal() > shown) {
            String key = switch (st) {
                case MILD -> "mild";
                case MODERATE -> "moderate";
                case SEVERE -> "severe";
                case EXTREMELY_SEVERE -> "extreme";
                default -> null;
            };
            if (key != null) p.displayClientMessage(msg(key).withStyle(st.ordinal() >= 4 ? ChatFormatting.RED : ChatFormatting.GOLD), false);
        }
        d.putInt("stage", st.ordinal());
        if (st.ordinal() >= RadiationSickness.Stage.MILD.ordinal()) {
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, st.ordinal() >= 3 ? 1 : 0, false, false));
            if (level.random.nextInt(60) == 0) p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0, false, false));
        }
        if (st.ordinal() >= RadiationSickness.Stage.MODERATE.ordinal()) p.addEffect(new MobEffectInstance(MobEffects.HUNGER, 60, 1, false, false));
        if (st.ordinal() >= RadiationSickness.Stage.SEVERE.ordinal())
            p.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, st == RadiationSickness.Stage.EXTREMELY_SEVERE ? 1 : 0, false, false));
        // свыше 20 Гр — церебральная форма: смерть за минуты
        if (acute >= 20) {
            DamageSource src = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(RADIATION));
            p.hurt(src, Float.MAX_VALUE);
        }
    }
}

package dev.denfry.promptcritical.mod.environment;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.consequence.GasDispersion;
import dev.denfry.promptcritical.sim.consequence.ToxicGas;
import dev.denfry.promptcritical.sim.environment.Environment;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3f;

/**
 * Выбросы токсичных газов в атмосферу ({@link ToxicGas}): непрерывные (утечка, абгаз без поглотителя, сброс с
 * переполненного хранилища) и мгновенные (разрыв аппарата). Раз в секунду для каждого игрока и каждого газа считается
 * приземная концентрация по гауссовой модели ({@link GasDispersion}) с ветром и классом устойчивости из погоды места;
 * копится токсическая нагрузка Σ Cⁿ·t, вероятность гибели по пробиту газа переводится в урон: рост вероятности на ΔP
 * отнимает ΔP максимального здоровья, при P → 1 игрок погибает. Пороги самочувствия — запах, ERPG-2 (тошнота), IDLH
 * (слабость, замедление), AEGL-3 (удушье) — свои у каждого газа. Блоки = метры; высота над источником ослабляет
 * концентрацию множителем exp(−z²/2σz²).
 *
 * <p>UF₆ отдельным газом не считается: во влажном воздухе он гидролизуется за секунды, и опасность облака — HF
 * ({@link #uf6Vent}, {@link #uf6Puff}; уранилфторид оседает аэрозолем, его химическая токсичность для почек здесь не
 * моделируется).
 */
public final class ToxicReleaseService {
    /** Непрерывный выброс живёт, пока источник его обновляет, плюс запас, тиков. */
    static final int VENT_HOLD_TICKS = 40;
    /** Клуб забывается через 30 мин; дальше 1,2 км выбросы не считаются. */
    static final double PUFF_MAX_S = 1800, RANGE_M = 1200;
    private static final Map<ToxicGas, ResourceKey<DamageType>> DAMAGE = new EnumMap<>(ToxicGas.class);
    private static final Map<ToxicGas, DustParticleOptions> FUME = new EnumMap<>(ToxicGas.class);
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();
    private static final Map<ServerLevel, State> STATES = new WeakHashMap<>();

    static {
        DAMAGE.put(ToxicGas.CHLORINE, key("chlorine"));
        DAMAGE.put(ToxicGas.HYDROGEN_FLUORIDE, key("hydrogen_fluoride"));
        DAMAGE.put(ToxicGas.SULFUR_DIOXIDE, key("sulfur_dioxide"));
        DAMAGE.put(ToxicGas.HYDROGEN_SULFIDE, key("hydrogen_sulfide"));
        FUME.put(ToxicGas.SULFUR_DIOXIDE, new DustParticleOptions(new Vector3f(0.85f, 0.85f, 0.80f), 1.4f));
        FUME.put(ToxicGas.HYDROGEN_SULFIDE, new DustParticleOptions(new Vector3f(0.80f, 0.82f, 0.78f), 1.0f));
        text(ToxicGas.SULFUR_DIOXIDE, "smell", "A choking, struck-match smell: sulfur dioxide", "Удушливый запах горелой спички: сернистый газ");
        text(ToxicGas.SULFUR_DIOXIDE, "idlh", "Sulfur dioxide above 100 ppm: airways spasm. Leave across the wind",
                "Сернистого газа больше 100 ppm: спазм дыхательных путей. Уходите поперёк ветра");
        text(ToxicGas.SULFUR_DIOXIDE, "aegl3", "Sulfur dioxide above 42 ppm for minutes: lungs are being burnt",
                "Сернистый газ выше 42 ppm несколько минут: ожог лёгких");
        text(ToxicGas.HYDROGEN_SULFIDE, "smell", "Rotten eggs: hydrogen sulfide. If the smell goes away, the gas has not",
                "Тухлые яйца: сероводород. Если запах пропал — газ не ушёл");
        text(ToxicGas.HYDROGEN_SULFIDE, "idlh", "Hydrogen sulfide above 100 ppm: you can no longer smell it. Self-contained breathing apparatus only",
                "Сероводорода больше 100 ppm: запаха уже нет. Только изолирующий дыхательный аппарат");
        text(ToxicGas.HYDROGEN_SULFIDE, "aegl3", "Hydrogen sulfide: dizziness and knockdown are coming", "Сероводород: головокружение, вот-вот потеря сознания");
        LANG.put("death.attack.promptcritical.sulfur_dioxide", new String[] {"%1$s choked on sulfur dioxide", "%1$s задохнулся сернистым газом"});
        LANG.put("death.attack.promptcritical.sulfur_dioxide.player", new String[] {"%1$s choked on sulfur dioxide while fighting %2$s",
                "%1$s задохнулся сернистым газом, спасаясь от %2$s"});
        LANG.put("death.attack.promptcritical.hydrogen_sulfide", new String[] {"%1$s was knocked down by hydrogen sulfide",
                "%1$s погиб от сероводорода"});
        LANG.put("death.attack.promptcritical.hydrogen_sulfide.player", new String[] {"%1$s was knocked down by hydrogen sulfide while fighting %2$s",
                "%1$s погиб от сероводорода, спасаясь от %2$s"});
        FUME.put(ToxicGas.CHLORINE, new DustParticleOptions(new Vector3f(0.78f, 0.86f, 0.30f), 1.6f));
        FUME.put(ToxicGas.HYDROGEN_FLUORIDE, new DustParticleOptions(new Vector3f(0.92f, 0.93f, 0.95f), 1.8f));
        text(ToxicGas.CHLORINE, "smell", "A sharp, bleach-like smell: chlorine in the air", "Резкий запах хлорки: в воздухе хлор");
        text(ToxicGas.CHLORINE, "idlh", "Chlorine above 10 ppm: eyes and throat burn. Get out across the wind and uphill",
                "Хлора больше 10 ppm: режет глаза и горло. Уходите поперёк ветра и на возвышенность");
        text(ToxicGas.CHLORINE, "aegl3", "Chlorine above 50 ppm: choking, lungs are being damaged", "Хлора больше 50 ppm: удушье, поражение лёгких");
        text(ToxicGas.HYDROGEN_FLUORIDE, "smell", "White fumes and a pungent, irritating smell: hydrogen fluoride",
                "Белый дым и резкий раздражающий запах: фтористый водород");
        text(ToxicGas.HYDROGEN_FLUORIDE, "idlh", "Hydrogen fluoride above 30 ppm: eyes, nose and throat burn. Leave across the wind",
                "Фтористого водорода больше 30 ppm: жжёт глаза, нос и горло. Уходите поперёк ветра");
        text(ToxicGas.HYDROGEN_FLUORIDE, "aegl3", "Hydrogen fluoride above 170 ppm: pulmonary oedema, fluoride is poisoning the body",
                "Фтористого водорода больше 170 ppm: отёк лёгких, фтор отравляет организм");
        LANG.put("death.attack.promptcritical.hydrogen_fluoride", new String[] {"%1$s was poisoned by hydrogen fluoride",
                "%1$s отравлен фтористым водородом"});
        LANG.put("death.attack.promptcritical.hydrogen_fluoride.player", new String[] {
                "%1$s was poisoned by hydrogen fluoride while fighting %2$s", "%1$s отравлен фтористым водородом, спасаясь от %2$s"});
    }

    private record Source(BlockPos pos, ToxicGas gas) {
    }

    private record Vent(double kgS, long until) {
    }

    private record Puff(BlockPos pos, ToxicGas gas, double kg, long startTick) {
    }

    private static final class State {
        final Map<Source, Vent> vents = new HashMap<>();
        final List<Puff> puffs = new ArrayList<>();
        /** {нагрузка, вероятность гибели, флаги сообщений} по игроку и газу. */
        final Map<UUID, EnumMap<ToxicGas, double[]>> exposure = new HashMap<>();
    }

    private ToxicReleaseService() {
    }

    private static ResourceKey<DamageType> key(String id) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, PromptCritical.id(id));
    }

    private static void text(ToxicGas gas, String key, String en, String ru) {
        LANG.put(prefix(gas) + key, new String[] {en, ru});
    }

    private static String prefix(ToxicGas gas) {
        return "message.promptcritical.toxic." + gas.name().toLowerCase(Locale.ROOT) + ".";
    }

    /** Тексты сообщений и смертей (en_us, ru_ru) для {@code PcLanguageProvider}. */
    public static Map<String, String[]> lang() {
        return LANG;
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel s) tick(s);
        });
    }

    private static State state(ServerLevel level) {
        return STATES.computeIfAbsent(level, l -> new State());
    }

    /** Непрерывный выброс газа, кг/с; источник обновляет его каждый тик, пока течёт. */
    public static void vent(ServerLevel level, BlockPos pos, ToxicGas gas, double kgS) {
        if (kgS <= 0) return;
        state(level).vents.put(new Source(pos.immutable(), gas), new Vent(kgS, level.getGameTime() + VENT_HOLD_TICKS));
    }

    /** Мгновенный выброс газа, кг. */
    public static void puff(ServerLevel level, BlockPos pos, ToxicGas gas, double kg) {
        if (kg > 0) state(level).puffs.add(new Puff(pos.immutable(), gas, kg, level.getGameTime()));
    }

    /** Непрерывная утечка UF₆, кг/с: в облаке — HF от гидролиза. */
    public static void uf6Vent(ServerLevel level, BlockPos pos, double uf6KgS) {
        vent(level, pos, ToxicGas.HYDROGEN_FLUORIDE, uf6KgS * ToxicGas.HF_PER_UF6);
    }

    /** Мгновенный выброс UF₆, кг. */
    public static void uf6Puff(ServerLevel level, BlockPos pos, double uf6Kg) {
        puff(level, pos, ToxicGas.HYDROGEN_FLUORIDE, uf6Kg * ToxicGas.HF_PER_UF6);
    }

    /** Текущий непрерывный выброс газа у источника, кг/с. */
    public static double ventKgS(ServerLevel level, BlockPos pos, ToxicGas gas) {
        State s = STATES.get(level);
        Vent v = s == null ? null : s.vents.get(new Source(pos, gas));
        return v == null ? 0 : v.kgS;
    }

    /** Приземная концентрация газа в точке, ppm. */
    public static double ppmAt(ServerLevel level, ToxicGas gas, double x, double y, double z) {
        State s = STATES.get(level);
        if (s == null || s.vents.isEmpty() && s.puffs.isEmpty()) return 0;
        long now = level.getGameTime();
        double kgM3 = 0;
        for (Map.Entry<Source, Vent> e : s.vents.entrySet())
            if (e.getKey().gas == gas) kgM3 += at(level, e.getKey().pos, e.getValue().kgS, -1, x, y, z);
        for (Puff p : s.puffs) if (p.gas == gas) kgM3 += at(level, p.pos, 0, (now - p.startTick) / 20.0, x, y, z) * p.kg;
        return gas.ppm(kgM3);
    }

    /** Концентрация от одного источника: непрерывного (tS < 0) или клуба массой 1 кг через tS секунд, кг/м³. */
    private static double at(ServerLevel level, BlockPos src, double kgS, double tS, double x, double y, double z) {
        double dx = x - (src.getX() + 0.5), dz = z - (src.getZ() + 0.5), dy = y - src.getY();
        if (dx * dx + dz * dz > RANGE_M * RANGE_M) return 0;
        Environment.Sample e = EnvironmentService.sample(level, src, 10);
        GasDispersion.Stability st = GasDispersion.stability(e.meanWindMs(), e.solarWm2());
        double[] f = GasDispersion.windFrame(dx, dz, e.windDirRad());
        double c, along;
        if (tS < 0) {
            c = GasDispersion.plumeKgM3(kgS, e.meanWindMs(), st, f[0], f[1]);
            along = Math.max(1, f[0]);
        } else {
            c = GasDispersion.puffKgM3(1, e.meanWindMs(), st, f[0], f[1], tS);
            along = Math.max(1, Math.max(1, e.meanWindMs()) * tS);
        }
        double sz = Math.max(1, st.sigmaZ(along)), h = Math.max(0, dy);
        return c * Math.exp(-h * h / (2 * sz * sz));
    }

    private static void tick(ServerLevel level) {
        State s = STATES.get(level);
        if (s == null) return;
        long now = level.getGameTime();
        s.vents.values().removeIf(v -> v.until < now);
        s.puffs.removeIf(p -> (now - p.startTick) / 20.0 > PUFF_MAX_S);
        if (now % 20 != 0) return;
        if (s.vents.isEmpty() && s.puffs.isEmpty() && s.exposure.isEmpty()) return;
        particles(level, s, now);
        for (ServerPlayer p : level.players())
            for (ToxicGas gas : ToxicGas.values()) expose(level, s, p, gas);
        s.exposure.keySet().removeIf(id -> level.getPlayerByUUID(id) == null);
    }

    private static void particles(ServerLevel level, State s, long now) {
        for (Map.Entry<Source, Vent> e : s.vents.entrySet()) {
            BlockPos pos = e.getKey().pos;
            double kgS = e.getValue().kgS;
            Environment.Sample w = EnvironmentService.sample(level, pos, 10);
            double ux = -Math.sin(w.windDirRad()), uz = Math.cos(w.windDirRad());
            int n = (int) Math.min(12, 2 + kgS * 20);
            for (int i = 0; i < n; i++) {
                double d = 1 + level.random.nextDouble() * Math.min(40, 6 + kgS * 60);
                level.sendParticles(FUME.get(e.getKey().gas), pos.getX() + 0.5 + ux * d, pos.getY() + 0.3 + level.random.nextDouble() * 0.8,
                        pos.getZ() + 0.5 + uz * d, 1, d * 0.08, 0.1, d * 0.08, 0);
            }
        }
        for (Puff p : s.puffs) {
            Environment.Sample w = EnvironmentService.sample(level, p.pos, 10);
            double t = (now - p.startTick) / 20.0, d = Math.max(1, w.meanWindMs()) * t;
            if (d > 200) continue;
            double ux = -Math.sin(w.windDirRad()), uz = Math.cos(w.windDirRad());
            level.sendParticles(FUME.get(p.gas), p.pos.getX() + 0.5 + ux * d, p.pos.getY() + 0.5, p.pos.getZ() + 0.5 + uz * d,
                    (int) Math.min(40, 4 + p.kg / 20), 2 + d * 0.15, 0.6, 2 + d * 0.15, 0);
        }
    }

    private static void expose(ServerLevel level, State s, ServerPlayer p, ToxicGas gas) {
        if (p.isCreative() || p.isSpectator()) return;
        // СИЗ 13 §13.7: аппарат или противогаз с фильтром своей группы, затем респиратор с поддувом 15.8
        double ambient = dev.denfry.promptcritical.mod.safety.PpeService.inhaled(p, dev.denfry.promptcritical.sim.ops.Ppe.contaminant(gas),
                ppmAt(level, gas, p.getX(), p.getEyeY(), p.getZ()), dev.denfry.promptcritical.sim.mining.MineGas.O2.freshAir, 1);
        double ppm = dev.denfry.promptcritical.mod.gear.ElectricGearService.inhaledPpm(p, ambient, 1);
        EnumMap<ToxicGas, double[]> byGas = s.exposure.get(p.getUUID());
        double[] ex = byGas == null ? null : byGas.get(gas);
        if (ppm < gas.odourPpm) {
            if (ex != null) {
                ex[0] *= Math.exp(-1.0 / 1800); // острая нагрузка: организм оправляется за десятки минут
                if (ex[0] < 1e-3) byGas.remove(gas);
                if (byGas.isEmpty()) s.exposure.remove(p.getUUID());
            }
            return;
        }
        if (byGas == null) s.exposure.put(p.getUUID(), byGas = new EnumMap<>(ToxicGas.class));
        if (ex == null) byGas.put(gas, ex = new double[3]);
        ex[0] += gas.load(ppm, 1.0 / 60);
        double lethal = gas.lethality(ex[0]);
        double dp = lethal - ex[1];
        int flags = (int) ex[2];
        // сероводород выше 100 ppm парализует обоняние: запаха нет с первого вдоха
        if ((flags & 1) == 0 && !(gas == ToxicGas.HYDROGEN_SULFIDE && ppm >= ToxicGas.H2S_ANOSMIA_PPM)) {
            p.displayClientMessage(msg(gas, "smell").withStyle(ChatFormatting.YELLOW), false);
            flags |= 1;
        }
        if (ppm >= gas.erpg2Ppm) p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, false, false));
        if (ppm >= gas.idlhPpm) {
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 1, false, false));
            if ((flags & 2) == 0) {
                p.displayClientMessage(msg(gas, "idlh").withStyle(ChatFormatting.GOLD), false);
                flags |= 2;
            }
        }
        if (ppm >= gas.aegl3Ppm) {
            p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
            if ((flags & 4) == 0) {
                p.displayClientMessage(msg(gas, "aegl3").withStyle(ChatFormatting.RED), false);
                flags |= 4;
            }
        }
        ex[2] = flags;
        DamageSource src = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE.get(gas)));
        if (lethal > 0.98) p.hurt(src, Float.MAX_VALUE);
        else if (dp > 0.002) p.hurt(src, (float) (dp * p.getMaxHealth()));
        ex[1] = Math.max(ex[1], lethal);
    }

    private static net.minecraft.network.chat.MutableComponent msg(ToxicGas gas, String key) {
        return Component.translatable(prefix(gas) + key);
    }
}

package dev.denfry.promptcritical.mod.chlor;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.environment.EnvironmentService;
import dev.denfry.promptcritical.sim.chlor.ChlorineCloud;
import dev.denfry.promptcritical.sim.environment.Environment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
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
 * Выбросы хлора в атмосферу: непрерывные (утечка из ванн, абгаз без щелочной колонны, сброс с переполненного
 * хранилища) и мгновенные (разрыв аппарата). Раз в секунду для каждого игрока считается приземная концентрация по
 * гауссовой модели ({@link ChlorineCloud}) с ветром и классом устойчивости из погоды места; копится токсическая
 * нагрузка Σ C²·t, вероятность гибели по пробиту CCPS переводится в урон: рост вероятности на ΔP отнимает ΔP
 * максимального здоровья, при P → 1 игрок погибает. Пороги самочувствия — запах 0,3 ppm, ERPG-2 3 ppm (тошнота),
 * IDLH 10 ppm (слабость, замедление), AEGL-3 50 ppm (удушье). Блоки = метры, высота над источником ослабляет
 * концентрацию множителем exp(−z²/2σz²).
 */
public final class ChlorineReleaseService {
    public static final ResourceKey<DamageType> CHLORINE = ResourceKey.create(Registries.DAMAGE_TYPE, PromptCritical.id("chlorine"));
    /** Непрерывный выброс живёт, пока источник его обновляет, плюс запас, тиков. */
    static final int VENT_HOLD_TICKS = 40;
    /** Клуб забывается, уйдя на 2 км или через 30 мин. */
    static final double PUFF_MAX_S = 1800, RANGE_M = 600;
    private static final DustParticleOptions CL2 = new DustParticleOptions(new Vector3f(0.78f, 0.86f, 0.30f), 1.6f);
    private static final Map<ServerLevel, State> STATES = new WeakHashMap<>();

    private record Vent(BlockPos pos, double kgS, long until) {
    }

    private record Puff(BlockPos pos, double kg, long startTick) {
    }

    private static final class State {
        final Map<BlockPos, Vent> vents = new HashMap<>();
        final List<Puff> puffs = new ArrayList<>();
        final Map<UUID, double[]> exposure = new HashMap<>(); // {нагрузка ppm²·мин, вероятность, флаги сообщений}
    }

    private ChlorineReleaseService() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel s) tick(s);
        });
    }

    private static State state(ServerLevel level) {
        return STATES.computeIfAbsent(level, l -> new State());
    }

    /** Непрерывный выброс, кг/с; источник обновляет его каждый тик, пока течёт. */
    public static void vent(ServerLevel level, BlockPos pos, double kgS) {
        if (kgS <= 0) return;
        state(level).vents.put(pos.immutable(), new Vent(pos.immutable(), kgS, level.getGameTime() + VENT_HOLD_TICKS));
    }

    /** Мгновенный выброс массы хлора, кг. */
    public static void puff(ServerLevel level, BlockPos pos, double kg) {
        if (kg > 0) state(level).puffs.add(new Puff(pos.immutable(), kg, level.getGameTime()));
    }

    /** Текущий выброс у источника, кг/с (для щитов и тестов). */
    public static double ventKgS(ServerLevel level, BlockPos pos) {
        State s = STATES.get(level);
        Vent v = s == null ? null : s.vents.get(pos);
        return v == null ? 0 : v.kgS;
    }

    /** Приземная концентрация хлора в точке, ppm. */
    public static double ppmAt(ServerLevel level, double x, double y, double z) {
        State s = STATES.get(level);
        if (s == null || s.vents.isEmpty() && s.puffs.isEmpty()) return 0;
        long now = level.getGameTime();
        double kgM3 = 0;
        for (Vent v : s.vents.values()) kgM3 += at(level, v.pos, v.kgS, -1, x, y, z);
        for (Puff p : s.puffs) kgM3 += at(level, p.pos, 0, (now - p.startTick) / 20.0, x, y, z) * p.kg;
        return ChlorineCloud.ppm(kgM3);
    }

    /** Концентрация от одного источника: непрерывного (tS < 0) или клуба массой 1 кг через tS секунд, кг/м³. */
    private static double at(ServerLevel level, BlockPos src, double kgS, double tS, double x, double y, double z) {
        double dx = x - (src.getX() + 0.5), dz = z - (src.getZ() + 0.5), dy = y - src.getY();
        if (dx * dx + dz * dz > RANGE_M * RANGE_M * 4) return 0;
        Environment.Sample e = EnvironmentService.sample(level, src, 10);
        ChlorineCloud.Stability st = ChlorineCloud.stability(e.meanWindMs(), e.solarWm2());
        double[] f = ChlorineCloud.windFrame(dx, dz, e.windDirRad());
        double c;
        double along;
        if (tS < 0) {
            c = ChlorineCloud.plumeKgM3(kgS, e.meanWindMs(), st, f[0], f[1]);
            along = Math.max(1, f[0]);
        } else {
            c = ChlorineCloud.puffKgM3(1, e.meanWindMs(), st, f[0], f[1], tS);
            along = Math.max(1, Math.max(1, e.meanWindMs()) * tS);
        }
        double sz = Math.max(1, st.sigmaZ(along));
        return c * Math.exp(-Math.max(0, dy) * Math.max(0, dy) / (2 * sz * sz));
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
        for (ServerPlayer p : level.players()) expose(level, s, p);
        s.exposure.keySet().removeIf(id -> level.getPlayerByUUID(id) == null);
    }

    private static void particles(ServerLevel level, State s, long now) {
        for (Vent v : s.vents.values()) {
            Environment.Sample e = EnvironmentService.sample(level, v.pos, 10);
            double ux = -Math.sin(e.windDirRad()), uz = Math.cos(e.windDirRad());
            int n = (int) Math.min(12, 2 + v.kgS * 20);
            for (int i = 0; i < n; i++) {
                double d = 1 + level.random.nextDouble() * Math.min(40, 6 + v.kgS * 60);
                level.sendParticles(CL2, v.pos.getX() + 0.5 + ux * d, v.pos.getY() + 0.3 + level.random.nextDouble() * 0.8,
                        v.pos.getZ() + 0.5 + uz * d, 1, d * 0.08, 0.1, d * 0.08, 0);
            }
        }
        for (Puff p : s.puffs) {
            Environment.Sample e = EnvironmentService.sample(level, p.pos, 10);
            double t = (now - p.startTick) / 20.0, d = Math.max(1, e.meanWindMs()) * t;
            if (d > 200) continue;
            double ux = -Math.sin(e.windDirRad()), uz = Math.cos(e.windDirRad());
            level.sendParticles(CL2, p.pos.getX() + 0.5 + ux * d, p.pos.getY() + 0.5, p.pos.getZ() + 0.5 + uz * d,
                    (int) Math.min(40, 4 + p.kg / 20), 2 + d * 0.15, 0.6, 2 + d * 0.15, 0);
        }
    }

    private static void expose(ServerLevel level, State s, ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) return;
        double ppm = ppmAt(level, p.getX(), p.getEyeY(), p.getZ());
        double[] ex = s.exposure.get(p.getUUID());
        if (ppm < ChlorineCloud.ODOUR_PPM) {
            if (ex != null) {
                ex[0] *= Math.exp(-1.0 / 1800); // острая нагрузка: организм оправляется за десятки минут
                if (ex[0] < 1) s.exposure.remove(p.getUUID());
            }
            return;
        }
        if (ex == null) s.exposure.put(p.getUUID(), ex = new double[3]);
        ex[0] += ppm * ppm / 60;
        double lethal = ChlorineCloud.lethality(ex[0]);
        double dp = lethal - ex[1];
        int flags = (int) ex[2];
        if ((flags & 1) == 0) {
            p.displayClientMessage(ChlorText.msg("cloud.smell").withStyle(ChatFormatting.YELLOW), false);
            flags |= 1;
        }
        if (ppm >= ChlorineCloud.ERPG2_PPM) p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, false, false));
        if (ppm >= ChlorineCloud.IDLH_PPM) {
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 1, false, false));
            if ((flags & 2) == 0) {
                p.displayClientMessage(ChlorText.msg("cloud.idlh").withStyle(ChatFormatting.GOLD), false);
                flags |= 2;
            }
        }
        if (ppm >= ChlorineCloud.AEGL3_PPM) {
            p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
            if ((flags & 4) == 0) {
                p.displayClientMessage(ChlorText.msg("cloud.aegl3").withStyle(ChatFormatting.RED), false);
                flags |= 4;
            }
        }
        ex[2] = flags;
        DamageSource src = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(CHLORINE));
        if (lethal > 0.98) p.hurt(src, Float.MAX_VALUE);
        else if (dp > 0.002) p.hurt(src, (float) (dp * p.getMaxHealth()));
        ex[1] = Math.max(ex[1], lethal);
    }

    /** Очистить состояние мира (тесты). */
    static void clear(ServerLevel level) {
        STATES.remove(level);
    }
}

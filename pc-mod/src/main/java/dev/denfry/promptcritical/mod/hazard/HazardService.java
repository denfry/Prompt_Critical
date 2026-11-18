package dev.denfry.promptcritical.mod.hazard;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.environment.EnvironmentService;
import dev.denfry.promptcritical.mod.environment.ToxicReleaseService;
import dev.denfry.promptcritical.mod.grid.GridDevice;
import dev.denfry.promptcritical.mod.ops.TankFires;
import dev.denfry.promptcritical.mod.registry.PcTags;
import dev.denfry.promptcritical.sim.consequence.BlastScaling;
import dev.denfry.promptcritical.sim.consequence.DamageBudgetConfig;
import dev.denfry.promptcritical.sim.consequence.DamageKind;
import dev.denfry.promptcritical.sim.consequence.DamageRequest;
import dev.denfry.promptcritical.sim.consequence.GasDispersion;
import dev.denfry.promptcritical.sim.consequence.ServerMode;
import dev.denfry.promptcritical.sim.consequence.ToxicGas;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.environment.Environment;
import dev.denfry.promptcritical.sim.hazard.Fireball;
import dev.denfry.promptcritical.sim.hazard.Flammable;
import dev.denfry.promptcritical.sim.hazard.FlammableCloud;
import dev.denfry.promptcritical.sim.hazard.Ignition;
import dev.denfry.promptcritical.sim.hazard.JetFire;
import dev.denfry.promptcritical.sim.hazard.PoolFire;
import dev.denfry.promptcritical.sim.hazard.ThermalRadiation;
import dev.denfry.promptcritical.sim.hazard.VapourCloudExplosion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Аварии нефтегаза в мире (09 §9.11): тот же конвейер, что у ядерного острова — истечение → облако → воспламенение →
 * последствие → {@code DamageRequest} в {@code DamageBudget} (04d) и выброс в {@link ToxicReleaseService}. Раз в секунду:
 * <ul>
 *   <li><b>утечки газа</b> ({@link #vent}) держатся, пока источник их обновляет. При начале — бросок по Коксу — Лису
 *   — Энгу ({@link Ignition}): немедленное воспламенение — струйный пожар; иначе облако копится по гауссову шлейфу
 *   ({@link FlammableCloud}), детекторы видят его долю НКПР ({@link #lelFraction}). Задержанное воспламенение (по
 *   броску или от источника в облаке: огонь, лава, горящие печи — 1,0/с; вращающиеся механизмы Create и устройства
 *   электросети — 0,1/с; редстоун под током — 0,02/с; блоки с меткой {@code explosion_proof} не искрят) даёт взрыв
 *   облака по загромождению (TNO) и пожар-вспышку, после чего утечка горит струйным пожаром у источника;</li>
 *   <li><b>сероводород</b> в газе уходит токсичным облаком, а в горящей струе сгорает в SO₂ (§9.11.8);</li>
 *   <li><b>пожары пролива</b> — горящие резервуары ({@link TankFires}) и горящие разливы ({@link OilSpills}) — излучают
 *   по Бабраускасу — Хескестаду (§9.11.4);</li>
 *   <li><b>огненный шар</b> BLEVE ({@link #bleve}) жжёт всех в прямой видимости по CCPS (§9.11.6).</li>
 * </ul>
 * Тепловое излучение ранит игроков по порогам API 521 и поджигает дерево и листву выше 12,5 кВт/м². Режим сервера
 * (04d §4d.1): в {@code creative_safe} — только частицы и звук: ни урона, ни огня, ни разрушений; в {@code contained}
 * территорий пока нет, и он ведёт себя как {@code realistic} (см. {@code LevelDamageMutator}).
 */
public final class HazardService {
    /** Утечка гаснет, если источник не обновил её столько секунд. */
    static final int HOLD_S = 2;
    /** Задержанное воспламенение по броску наступает в пределах этого времени от начала утечки, с [моё]. */
    static final double DELAYED_WINDOW_S = 60;
    /** Струя в факеле: расстояние до оси, ближе которого аппарат «в пламени», м. */
    static final double IN_FLAME_M = 1.5;
    /** Поток на сосуд, охваченный пожаром пролива, кВт/м² (нижняя граница 100–250, §9.11.5). */
    public static final double ENGULFMENT_KW_M2 = 100;
    /** Огонь на блоки в секунду на одно событие — защита сервера. */
    static final int MAX_IGNITIONS_PER_S = 4;
    /** Пожар-вспышка: урон тем, кто внутри облака, и время горения [BALANCE]. */
    static final float FLASH_FIRE_HP = 8;
    static final int FLASH_FIRE_BURN_S = 8;

    private static final Map<ServerLevel, State> STATES = new WeakHashMap<>();
    private static long requestSeq = 1L << 41;

    /** Одна утечка газа. */
    public static final class Release {
        final String id;
        BlockPos pos;
        Flammable gas;
        double kgS, h2sFraction, ageS, delayedAtS;
        int heldS;
        boolean jet;
        Ignition.Outcome outcome;
        FlammableCloud.Result cloud = FlammableCloud.Result.EMPTY;
        Environment.Sample weather;
        GasDispersion.Stability stability;
        final Map<Long, Double> congestion = new HashMap<>();

        Release(String id) {
            this.id = id;
        }

        public boolean burning() {
            return jet;
        }

        public FlammableCloud.Result cloud() {
            return cloud;
        }

        public double kgS() {
            return kgS;
        }
    }

    /** Горящий пролив (резервуар или разлив): центр, диаметр и мощность. */
    record Pool(Vec3 center, double diameterM, double heatW) {}

    record Ball(Vec3 center, double massKg, long startTick) {}

    private static final class State {
        final Map<String, Release> releases = new LinkedHashMap<>();
        final List<Pool> pools = new ArrayList<>();
        final List<Ball> balls = new ArrayList<>();
        final SplittableRandom rnd;

        State(long seed) {
            rnd = new SplittableRandom(seed ^ 0x9e3779b97f4a7c15L);
        }
    }

    private HazardService() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(HazardService::onLevelTick);
    }

    private static State state(ServerLevel level) {
        return STATES.computeIfAbsent(level, l -> new State(l.getSeed()));
    }

    public static ServerMode mode() {
        return ServerConfig.SERVER_MODE.get();
    }

    /** Последствия вредят игрокам и миру (не {@code creative_safe}). */
    public static boolean harmful() {
        return mode() != ServerMode.CREATIVE_SAFE;
    }

    // ------------------------------------------------------------------ API

    /**
     * Утечка газа из точки {@code pos}: источник вызывает каждую секунду, пока течёт.
     *
     * @param id          ключ источника (одна утечка на ключ)
     * @param h2sFraction массовая доля сероводорода в газе
     * @param ignited     утечка сразу в огне (сброс клапана в пламя, поджог факелом)
     * @return утечка
     */
    public static Release vent(ServerLevel level, String id, BlockPos pos, Flammable gas, double kgS, double h2sFraction, boolean ignited) {
        State s = state(level);
        Release r = s.releases.get(id);
        if (r == null) {
            r = new Release(id);
            r.outcome = Ignition.roll(kgS, true, s.rnd);
            r.delayedAtS = s.rnd.nextDouble() * DELAYED_WINDOW_S;
            r.jet = r.outcome == Ignition.Outcome.IMMEDIATE;
            s.releases.put(id, r);
        }
        r.pos = pos.immutable();
        r.gas = gas;
        r.kgS = Math.max(0, kgS);
        r.h2sFraction = Math.clamp(h2sFraction, 0, 1);
        r.heldS = 0;
        if (ignited) r.jet = true;
        return r;
    }

    /** Источник поджигания облака под рукой игрока или стенда: облако утечки {@code id} воспламеняется сейчас. */
    public static void ignite(ServerLevel level, String id) {
        Release r = state(level).releases.get(id);
        if (r != null && !r.jet) igniteCloud(level, r);
    }

    /** Стенд (GameTest): утечка {@code id} не воспламеняется сама — только от источника или {@link #ignite}. */
    public static void holdIgnition(ServerLevel level, String id) {
        Release r = state(level).releases.get(id);
        if (r == null) return;
        r.outcome = Ignition.Outcome.NONE;
        r.jet = false;
    }

    public static Release release(ServerLevel level, String id) {
        return state(level).releases.get(id);
    }

    /** Доля НКПР горючего газа в точке (наибольшая по утечкам): для газоанализаторов. */
    public static double lelFraction(ServerLevel level, BlockPos p) {
        State s = STATES.get(level);
        if (s == null) return 0;
        double max = 0;
        for (Release r : s.releases.values()) {
            if (r.jet || r.weather == null) continue;
            double c = FlammableCloud.concentrationAt(r.gas, r.kgS, r.weather.meanWindMs(), r.stability, r.weather.windDirRad(), r.ageS,
                    p.getX() + 0.5 - (r.pos.getX() + 0.5), p.getY() + 0.5 - r.pos.getY(), p.getZ() + 0.5 - (r.pos.getZ() + 0.5));
            max = Math.max(max, r.gas.lelFraction(c));
        }
        return max;
    }

    /** Поток тепла на точку от струйных пожаров, горящих проливов и огненных шаров, кВт/м². */
    public static double heatFluxAt(ServerLevel level, Vec3 p) {
        State s = STATES.get(level);
        if (s == null) return 0;
        double q = 0;
        for (Release r : s.releases.values()) {
            if (!r.jet || r.kgS <= 0) continue;
            double qw = JetFire.heatReleaseW(r.kgS, r.gas), len = JetFire.lengthM(qw);
            Vec3 a = Vec3.atBottomCenterOf(r.pos.above()), b = a.add(0, len, 0);
            double d = distanceToSegment(p, a, b);
            q += d <= IN_FLAME_M ? JetFire.IMPINGEMENT_KW_M2 : JetFire.fluxKwM2(qw, p.distanceTo(a.add(0, len / 2, 0)));
        }
        for (Pool pool : s.pools) {
            double horiz = Math.hypot(p.x - pool.center.x, p.z - pool.center.z), dy = p.y - pool.center.y;
            if (horiz <= pool.diameterM / 2 + 1 && dy >= -1 && dy <= 3) q += ENGULFMENT_KW_M2;
            else q += PoolFire.fluxKwM2(PoolFire.Fuel.CRUDE, pool.diameterM, p.distanceTo(pool.center));
        }
        long now = level.getGameTime();
        for (Ball ball : s.balls)
            if (now - ball.startTick <= Fireball.durationS(ball.massKg) * 20) q += Fireball.fluxKwM2(ball.massKg, p.distanceTo(ball.center));
        return q;
    }

    /**
     * BLEVE (§9.11.6): огненный шар массой {@code massKg} и ударная волна {@code blastJ} (уже × 0,4) из центра
     * {@code center}. Осколки ({@code MissileHazard} 04c) не моделируются.
     */
    public static void bleve(ServerLevel level, BlockPos center, double massKg, double blastJ, String sourceId) {
        State s = state(level);
        Vec3 c = Vec3.atCenterOf(center);
        double d = Fireball.diameterM(massKg);
        if (harmful()) {
            s.balls.add(new Ball(c.add(0, d / 4, 0), massKg, level.getGameTime()));
            blast(level, center, blastJ, 1);
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1, c.z, 3, 1.5, 1.5, 1.5, 0);
        level.sendParticles(ParticleTypes.FLAME, c.x, c.y + d / 4, c.z, 400, d / 6, d / 6, d / 6, 0.05);
        level.playSound(null, center, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 8f, 0.5f);
        tell(level, center, 256, harmful() ? HazardText.msg("bleve", Math.round(d)) : HazardText.msg("bleve_safe"));
    }

    // ------------------------------------------------------------------ тик

    private static void onLevelTick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel level) || level.getGameTime() % 20 != 0) return;
        State s = STATES.get(level);
        tickPools(level, s == null ? null : s);
        if (s == null) return;
        for (Iterator<Release> it = s.releases.values().iterator(); it.hasNext(); ) {
            Release r = it.next();
            if (++r.heldS > HOLD_S || !level.isLoaded(r.pos)) {
                it.remove();
                continue;
            }
            r.ageS += 1;
            tickRelease(level, s, r);
        }
        long now = level.getGameTime();
        s.balls.removeIf(b -> now - b.startTick > Fireball.durationS(b.massKg) * 20 + 20);
        for (Ball b : s.balls) radiate(level, b.center, p -> Fireball.fluxKwM2(b.massKg, p.distanceTo(b.center)), Fireball.diameterM(b.massKg) * 4);
    }

    private static void tickRelease(ServerLevel level, State s, Release r) {
        // сероводород: из негорящей утечки — облаком H₂S, из горящей — SO₂ (§9.11.8)
        double h2s = r.kgS * r.h2sFraction;
        if (h2s > 0) {
            if (r.jet) ToxicReleaseService.vent(level, r.pos.above(2), ToxicGas.SULFUR_DIOXIDE, h2s * ToxicGas.SULFUR_DIOXIDE.molarMassG / ToxicGas.HYDROGEN_SULFIDE.molarMassG);
            else ToxicReleaseService.vent(level, r.pos, ToxicGas.HYDROGEN_SULFIDE, h2s);
        }
        if (r.jet) {
            jetFire(level, r);
            return;
        }
        r.weather = EnvironmentService.sample(level, r.pos, 10);
        r.stability = GasDispersion.stability(r.weather.meanWindMs(), r.weather.solarWm2());
        if (level.getGameTime() % 200 == 0) r.congestion.clear();
        BlockPos o = r.pos;
        r.cloud = FlammableCloud.integrate(r.gas, r.kgS * (1 - r.h2sFraction), r.weather.meanWindMs(), r.stability, r.weather.windDirRad(),
                r.ageS, (east, up, south) -> congestion(level, r, o.offset((int) Math.floor(east), (int) Math.floor(up), (int) Math.floor(south))));
        if (r.cloud.flammableKg() <= 0) return;
        boolean fire = r.outcome == Ignition.Outcome.DELAYED && r.ageS >= r.delayedAtS;
        if (!fire) fire = sourceInCloud(level, s, r);
        if (fire) igniteCloud(level, r);
    }

    /** Доля непустых блоков в кубе 5×5×5 (выборка 27 точек через блок), с кешем на 10 с. */
    private static double congestion(ServerLevel level, Release r, BlockPos p) {
        long key = BlockPos.asLong(p.getX() >> 1, p.getY() >> 1, p.getZ() >> 1);
        Double c = r.congestion.get(key);
        if (c != null) return c;
        int solid = 0, n = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx += 2)
            for (int dy = -2; dy <= 2; dy += 2)
                for (int dz = -2; dz <= 2; dz += 2) {
                    m.set(p.getX() + dx, p.getY() + dy, p.getZ() + dz);
                    n++;
                    if (level.isLoaded(m) && !level.getBlockState(m).isAir()) solid++;
                }
        double v = (double) solid / n;
        r.congestion.put(key, v);
        return v;
    }

    /** Источник зажигания в горючей части облака, бросок по его вероятности за секунду. */
    private static boolean sourceInCloud(ServerLevel level, State s, Release r) {
        FlammableCloud.Result c = r.cloud;
        int half = (int) Math.clamp(Math.cbrt(c.flammableM3()) / 2 + 2, 2, 12);
        BlockPos center = r.pos.offset((int) Math.round(c.centroidEast()), (int) Math.round(c.centroidUp()), (int) Math.round(c.centroidSouth()));
        Ignition.Source best = null;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-half, -Math.min(half, 4), -half), center.offset(half, Math.min(half, 6), half))) {
            if (!level.isLoaded(p)) continue;
            Ignition.Source src = ignitionSource(level, p);
            if (src == null || (best != null && best.perSecond >= src.perSecond)) continue;
            double conc = FlammableCloud.concentrationAt(r.gas, r.kgS, r.weather.meanWindMs(), r.stability, r.weather.windDirRad(), r.ageS,
                    p.getX() + 0.5 - (r.pos.getX() + 0.5), p.getY() + 0.5 - r.pos.getY(), p.getZ() + 0.5 - (r.pos.getZ() + 0.5));
            if (r.gas.flammable(conc)) best = src;
            if (best == Ignition.Source.OPEN_FLAME) break;
        }
        return best != null && Ignition.ignitesThisSecond(best, s.rnd);
    }

    /** Источник зажигания в клетке (§9.11.3), {@code null} — нет. */
    static Ignition.Source ignitionSource(ServerLevel level, BlockPos p) {
        BlockState st = level.getBlockState(p);
        if (st.isAir()) return null;
        if (st.is(PcTags.Blocks.EXPLOSION_PROOF)) return null;
        if (st.getBlock() instanceof BaseFireBlock || st.is(Blocks.LAVA) || st.is(Blocks.MAGMA_BLOCK)) return Ignition.Source.OPEN_FLAME;
        if (st.hasProperty(BlockStateProperties.LIT) && st.getValue(BlockStateProperties.LIT)) return Ignition.Source.OPEN_FLAME;
        if (st.getBlock() instanceof RedStoneWireBlock && st.getValue(RedStoneWireBlock.POWER) > 0) return Ignition.Source.REDSTONE;
        var be = level.getBlockEntity(p);
        if (be instanceof KineticBlockEntity k && k.getSpeed() != 0) return Ignition.Source.SPARKING;
        if (be instanceof GridDevice) return Ignition.Source.SPARKING;
        return null;
    }

    /** Воспламенение облака: взрыв загромождённой части (TNO), пожар-вспышка остального, огонь уходит к утечке. */
    static void igniteCloud(ServerLevel level, Release r) {
        FlammableCloud.Result c = r.cloud;
        BlockPos center = r.pos.offset((int) Math.round(c.centroidEast()), (int) Math.round(c.centroidUp()), (int) Math.round(c.centroidSouth()));
        int cls = VapourCloudExplosion.strengthClass(c.meanCongestion(), false, r.gas);
        double energy = VapourCloudExplosion.mechanicalEnergyJ(c.congestedM3(), cls);
        // пожар-вспышка: кто внутри горючей части облака, горит
        double reach = Math.cbrt(Math.max(1, c.flammableM3())) + 2;
        if (harmful())
            for (ServerPlayer p : level.getPlayers(p -> p.position().distanceTo(Vec3.atCenterOf(center)) <= reach)) {
                p.setRemainingFireTicks(FLASH_FIRE_BURN_S * 20);
                p.hurt(level.damageSources().inFire(), FLASH_FIRE_HP);
            }
        level.sendParticles(ParticleTypes.FLAME, center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5, (int) Math.min(600, 40 + c.flammableM3() / 4),
                reach / 2, reach / 4, reach / 2, 0.08);
        level.playSound(null, center, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 4f, 0.6f);
        if (energy > 0) {
            blast(level, center, energy, 2);
            tell(level, center, 192, HazardText.msg("vce", Math.round(c.flammableKg()), cls));
        } else {
            tell(level, center, 96, HazardText.msg("flash_fire"));
        }
        r.jet = true;
        r.cloud = FlammableCloud.Result.EMPTY;
    }

    /** Взрыв: заявка в бюджет разрушений и ванильный взрыв без разрушения блоков (урон сущностям, звук, частицы). */
    static void blast(ServerLevel level, BlockPos origin, double energyJ, int priority) {
        if (energyJ <= 0) return;
        SimLevelBinding b = SimBindings.get(level);
        DamageBudgetConfig dc = b == null ? DamageBudgetConfig.defaults(mode()) : b.damage().config();
        double power = BlastScaling.power(energyJ, dc.tntReferenceKg(), dc.maxExplosionPower());
        if (b != null)
            b.damage().submit(new DamageRequest(requestSeq++, SimId.NONE, null, DamageKind.BLAST,
                    new GridPos(origin.getX(), origin.getY(), origin.getZ()), energyJ, BlastScaling.radiusBlocks(Math.max(power, 1)), priority));
        if (harmful() && power > 0)
            level.explode(null, origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5, (float) Math.max(1, power), false,
                    Level.ExplosionInteraction.NONE);
        else level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5, 1, 0, 0, 0, 0);
    }

    /** Струйный пожар: вертикальный факел над утечкой, излучение, огонь в пламени. */
    private static void jetFire(ServerLevel level, Release r) {
        double q = JetFire.heatReleaseW(r.kgS * (1 - r.h2sFraction), r.gas), len = Math.max(1, JetFire.lengthM(q));
        Vec3 a = Vec3.atBottomCenterOf(r.pos.above()), mid = a.add(0, len / 2, 0), b = a.add(0, len, 0);
        level.sendParticles(ParticleTypes.FLAME, mid.x, mid.y, mid.z, (int) Math.min(200, 10 + len * 4), 0.3, len / 3, 0.3, 0.05);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, b.x, b.y, b.z, 6, 0.6, 0.4, 0.6, 0.02);
        if (r.ageS % 4 == 1) level.playSound(null, r.pos, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 3f, 0.5f);
        if (r.ageS == 1) tell(level, r.pos, 64, HazardText.msg("jet_fire"));
        radiate(level, mid, p -> distanceToSegment(p, a, b) <= IN_FLAME_M ? JetFire.IMPINGEMENT_KW_M2 : JetFire.fluxKwM2(q, p.distanceTo(mid)), 64);
    }

    /** Горящие резервуары и разливы: пожары пролива с излучением (§9.11.4). */
    private static void tickPools(ServerLevel level, State s) {
        Set<BlockPos> tanks = TankFires.burningCells(level);
        List<BlockPos> spills = OilSpills.burningCells(level);
        if ((tanks == null || tanks.isEmpty()) && spills.isEmpty()) {
            if (s != null) s.pools.clear();
            return;
        }
        if (s == null) s = state(level);
        s.pools.clear();
        List<BlockPos> cells = new ArrayList<>();
        if (tanks != null) cells.addAll(tanks);
        cells.addAll(spills);
        // клетки → связные очаги (соседство по горизонтали и через блок), один пролив — один источник
        boolean[] used = new boolean[cells.size()];
        for (int i = 0; i < cells.size(); i++) {
            if (used[i]) continue;
            List<BlockPos> group = new ArrayList<>();
            group.add(cells.get(i));
            used[i] = true;
            for (int g = 0; g < group.size(); g++)
                for (int j = 0; j < cells.size(); j++)
                    if (!used[j] && group.get(g).distManhattan(cells.get(j)) <= 2) {
                        used[j] = true;
                        group.add(cells.get(j));
                    }
            double x = 0, y = 0, z = 0;
            for (BlockPos p : group) {
                x += p.getX() + 0.5;
                y += p.getY();
                z += p.getZ() + 0.5;
            }
            double d = PoolFire.diameter(group.size());
            double qw = PoolFire.heatReleaseW(PoolFire.Fuel.CRUDE, d, PoolFire.COMBUSTION_EFFICIENCY);
            Vec3 base = new Vec3(x / group.size(), y / group.size(), z / group.size());
            Vec3 c = base.add(0, PoolFire.flameHeightM(qw, d) / 2, 0);
            s.pools.add(new Pool(base, d, qw));
            double frac = PoolFire.radiantFraction(d);
            radiate(level, c, p -> ThermalRadiation.pointSourceKwM2(frac, qw, p.distanceTo(c)), 48);
        }
    }

    /** Излучение на игроков в радиусе и поджог дерева выше 12,5 кВт/м². */
    private static void radiate(ServerLevel level, Vec3 c, java.util.function.ToDoubleFunction<Vec3> flux, double radius) {
        if (!harmful()) return;
        for (ServerPlayer p : level.getPlayers(p -> p.position().distanceToSqr(c) <= radius * radius)) {
            if (p.isCreative() || p.isSpectator()) continue;
            double q = flux.applyAsDouble(p.getEyePosition());
            double hp = ThermalRadiation.damagePerSecond(q);
            if (hp <= 0) continue;
            if (!p.level().clip(new net.minecraft.world.level.ClipContext(p.getEyePosition(), c, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, p)).getType().equals(net.minecraft.world.phys.HitResult.Type.MISS)) continue;
            p.hurt(level.damageSources().inFire(), (float) hp);
            if (ThermalRadiation.ignitesWood(q)) p.setRemainingFireTicks(Math.max(p.getRemainingFireTicks(), 60));
            if (level.getGameTime() % 100 == 0)
                p.displayClientMessage(HazardText.msg("heat", Math.round(q * 10) / 10.0).withStyle(ChatFormatting.GOLD), true);
        }
        // дерево, шерсть и листва в зоне 12,5 кВт/м² загораются (выборка нескольких клеток в секунду)
        SplittableRandom rnd = state(level).rnd;
        int lit = 0;
        for (int k = 0; k < 24 && lit < MAX_IGNITIONS_PER_S; k++) {
            double rr = rnd.nextDouble() * radius;
            double ang = rnd.nextDouble() * 2 * Math.PI;
            BlockPos p = BlockPos.containing(c.x + rr * Math.cos(ang), c.y - 2 + rnd.nextDouble() * 4, c.z + rr * Math.sin(ang));
            if (!level.isLoaded(p) || !ThermalRadiation.ignitesWood(flux.applyAsDouble(Vec3.atCenterOf(p)))) continue;
            BlockState st = level.getBlockState(p);
            if (!st.isFlammable(level, p, net.minecraft.core.Direction.UP)) continue;
            BlockPos up = p.above();
            if (level.getBlockState(up).isAir()) {
                level.setBlock(up, BaseFireBlock.getState(level, up), 3);
                lit++;
            }
        }
    }

    static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        double t = len2 <= 0 ? 0 : Math.clamp(p.subtract(a).dot(ab) / len2, 0, 1);
        return p.distanceTo(a.add(ab.scale(t)));
    }

    static void tell(ServerLevel level, BlockPos at, double radius, net.minecraft.network.chat.MutableComponent msg) {
        for (ServerPlayer p : level.getPlayers(p -> p.distanceToSqr(at.getCenter()) < radius * radius))
            p.displayClientMessage(msg.copy().withStyle(ChatFormatting.RED), false);
    }

    /** Стенд (GameTest): забыть все утечки и пожары измерения. */
    public static void clear(ServerLevel level) {
        STATES.remove(level);
    }
}

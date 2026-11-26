package dev.denfry.promptcritical.mod.environment;

import dev.denfry.promptcritical.mod.radiation.ContaminationService;
import dev.denfry.promptcritical.sim.consequence.GasDispersion;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import dev.denfry.promptcritical.sim.radiation.Fallout;
import dev.denfry.promptcritical.sim.radiation.SurfaceContamination;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * След выпадения выброса (04b §4b.9): раз в секунду каждый действующий выброс оседает из приземного факела на верхние
 * грани блоков по ветру — столбцами 4×4 блока по карте высот, в секторе ±3σy, до {@link Fallout#MAX_DISTANCE_M}.
 * Загрязнение поверхностей хранит двух долгоживущих представителей (α — Pu-239, β — Cs-137, {@link SurfaceContamination}),
 * поэтому оседают только нуклиды с периодом от года: йод-131 за 8 суток уходит сам и в карте застрял бы навсегда.
 */
public final class FalloutService {
    /** Столбец следа, блоков. */
    static final int CELL = 4;
    /** Не больше столбцов от одного выброса за секунду [BALANCE {@code radiation.contamination.max_sections_per_event}]. */
    static final int MAX_CELLS = 4096;
    /** Меньше этого за секунду на грань не пишется, Бк/см². */
    static final double MIN_BQ_CM2 = 1e-7;
    private static final double YEAR_S = 365.25 * 86400;

    private FalloutService() {
    }

    /** Выброс, начиная с которого шлейф виден (пар и аэрозоль аварийного масштаба), Бк/с. */
    static final double VISIBLE_BQ_S = 1e10;

    /**
     * Видимый шлейф крупного выброса: пар с аэрозолем выходит из точки выброса и уходит по ветру со скоростью ветра
     * модели рассеяния (м/с → блоков за тик), поднимаясь, пока остывает. Радиоактивность глазом не видна — виден пар.
     */
    static void plume(ServerLevel level, long now) {
        java.util.Map<Vec3, double[]> total = new java.util.HashMap<>();
        RadiationService.eachRelease(level, now, (at, nuclide, bqS) -> total.computeIfAbsent(at, k -> new double[1])[0] += bqS);
        for (var e : total.entrySet()) {
            if (e.getValue()[0] < VISIBLE_BQ_S) continue;
            Vec3 at = e.getKey();
            var w = EnvironmentService.sample(level, BlockPos.containing(at), 10);
            double from = w.windDirRad(), u = Math.max(1, w.meanWindMs()) / 20;
            Vec3 wind = new Vec3(-Math.sin(from) * u, 0, Math.cos(from) * u);
            int n = (int) Math.min(6, 1 + Math.log10(e.getValue()[0] / VISIBLE_BQ_S) * 2);
            var r = level.random;
            for (int i = 0; i < n; i++)
                dev.denfry.promptcritical.mod.fx.WorldFx.fly(level, i % 2 == 0 ? net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE
                                : net.minecraft.core.particles.ParticleTypes.CLOUD,
                        at.add(r.nextGaussian() * 0.5, r.nextDouble(), r.nextGaussian() * 0.5),
                        wind.scale(0.8 + 0.4 * r.nextDouble()).add(0, 0.03 + 0.03 * r.nextDouble(), 0));
        }
    }

    static void tick(ServerLevel level, long now) {
        RadiationService.eachRelease(level, now, (at, nuclide, bqS) -> deposit(level, at, nuclide, bqS, 1, Fallout.MAX_DISTANCE_M));
    }

    /**
     * Осаждает {@code seconds} секунд выброса {@code bqS} нуклида {@code nuclide} из точки {@code at} на расстоянии до
     * {@code maxDistanceM} по ветру (не дальше {@link Fallout#MAX_DISTANCE_M}).
     *
     * @return сколько столбцов получили загрязнение
     */
    public static int deposit(ServerLevel level, Vec3 at, Emitter nuclide, double bqS, double seconds, double maxDistanceM) {
        double vd = Fallout.depositionVelocity(nuclide);
        // табл. 4b.9.1: что не оседает (благородные газы), то и дождём не вымывается
        if (!(bqS > 0) || vd <= 0 || nuclide.halfLifeS < YEAR_S) return 0;
        BlockPos src = BlockPos.containing(at);
        var w = EnvironmentService.sample(level, src, 10);
        var st = GasDispersion.stability(w.meanWindMs(), w.solarWm2());
        BlockPos sky = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, src);
        double lambda = Fallout.washout(level.isRainingAt(sky), level.isThundering() && level.isRainingAt(sky));
        double u = w.meanWindMs(), from = w.windDirRad();
        double ux = -Math.sin(from), uz = Math.cos(from);
        boolean alpha = nuclide == Emitter.PU238 || nuclide == Emitter.AM241 || nuclide == Emitter.CM244;
        LongOpenHashSet seen = new LongOpenHashSet();
        int cells = 0;
        double reachX = Math.min(maxDistanceM, Fallout.MAX_DISTANCE_M);
        for (double x = CELL / 2.0; x <= reachX && cells < MAX_CELLS; x += CELL) {
            double reach = 3 * st.sigmaY(Math.max(x, 1)) + CELL;
            for (double y = -reach; y <= reach && cells < MAX_CELLS; y += CELL) {
                int cx = Math.floorDiv((int) Math.floor(at.x + x * ux - y * uz), CELL) * CELL;
                int cz = Math.floorDiv((int) Math.floor(at.z + x * uz + y * ux), CELL) * CELL;
                if (!seen.add(BlockPos.asLong(cx, 0, cz))) continue;
                // depletion once per cell, at its centre; the flux itself per block: near the source the plume is
                // narrower than a cell
                double[] fc = GasDispersion.windFrame(cx + CELL / 2.0 - at.x, cz + CELL / 2.0 - at.z, from);
                if (fc[0] > reachX || !level.isLoaded(new BlockPos(cx, src.getY(), cz))) continue;
                double dep = Fallout.depletion(vd, lambda, u, st, Math.max(fc[0], 1));
                boolean any = false;
                for (int ix = 0; ix < CELL; ix++)
                    for (int iz = 0; iz < CELL; iz++) {
                        int bx = cx + ix, bz = cz + iz;
                        double[] f = GasDispersion.windFrame(bx + 0.5 - at.x, bz + 0.5 - at.z, from);
                        double bqCm2 = Fallout.undepletedFluxBqM2S(bqS, u, st, f[0], f[1], vd, lambda) * dep * seconds / 1e4;
                        if (bqCm2 < MIN_BQ_CM2) continue;
                        BlockPos top = new BlockPos(bx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1, bz);
                        ContaminationService.deposit(level, top, alpha ? SurfaceContamination.of(bqCm2, 0, 1) : SurfaceContamination.of(0, bqCm2, 1));
                        any = true;
                    }
                if (!any) continue;
                cells++;
            }
        }
        return cells;
    }
}

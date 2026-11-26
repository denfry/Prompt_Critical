package dev.denfry.promptcritical.mod.fx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Видимые последствия крупных аварий на сервере: струи, огненные шары, ударные волны, пылевые облака. Частицы уходят
 * дальней отправкой (как у взрывов TNT), чтобы авария была видна издалека, а не только в 32 блоках. Масштаб эффектов
 * — от физики события (расход течи, энергия взрыва, объём помещения), не от желания показать побольше.
 */
public final class WorldFx {
    /** Дальше этого крупные эффекты не отправляются, блоков. */
    public static final double RANGE = 256;

    private WorldFx() {
    }

    /** {@code count} частиц разбросом ({@code dx}, {@code dy}, {@code dz}); при {@code count} = 0 — одна, летящая со скоростью (dx, dy, dz)·speed. */
    public static void send(ServerLevel level, ParticleOptions type, double x, double y, double z, int count, double dx, double dy,
            double dz, double speed) {
        for (ServerPlayer p : level.players())
            if (p.distanceToSqr(x, y, z) < RANGE * RANGE) level.sendParticles(p, type, true, x, y, z, count, dx, dy, dz, speed);
    }

    /** Одна частица из {@code at} со скоростью {@code v}, блоков за тик. */
    public static void fly(ServerLevel level, ParticleOptions type, Vec3 at, Vec3 v) {
        double s = v.length();
        if (s < 1e-6) send(level, type, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        else send(level, type, at.x, at.y, at.z, 0, v.x / s, v.y / s, v.z / s, s);
    }

    /**
     * Струя: {@code n} частиц из {@code from} по направлению {@code dir} со скоростью {@code speed} блоков/тик и
     * расхождением {@code spread} (доля скорости поперёк).
     */
    public static void jet(ServerLevel level, ParticleOptions type, Vec3 from, Vec3 dir, double speed, double spread, int n, RandomSource r) {
        Vec3 d = dir.normalize();
        for (int i = 0; i < n; i++) {
            Vec3 v = d.add(r.nextGaussian() * spread, r.nextGaussian() * spread, r.nextGaussian() * spread).normalize()
                    .scale(speed * (0.7 + 0.6 * r.nextDouble()));
            fly(level, type, from.add(r.nextGaussian() * 0.15, r.nextGaussian() * 0.15, r.nextGaussian() * 0.15), v);
        }
    }

    /**
     * Огненный шар горения газа в помещении: пламя и клубы в воздухе шара радиуса {@code radius} вокруг {@code center},
     * только в свободных блоках (стены огонь не проходит).
     */
    public static void fireball(ServerLevel level, Vec3 center, double radius, int n, RandomSource r) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0, tries = 0; i < n && tries < 4 * n; tries++) {
            Vec3 p = center.add((r.nextDouble() * 2 - 1) * radius, (r.nextDouble() * 2 - 1) * radius, (r.nextDouble() * 2 - 1) * radius);
            if (p.distanceToSqr(center) > radius * radius) continue;
            if (!level.getBlockState(m.set(p.x, p.y, p.z)).isAir()) continue;
            i++;
            Vec3 out = p.subtract(center).normalize().scale(0.15 + 0.2 * r.nextDouble());
            fly(level, i % 7 == 0 ? ParticleTypes.EXPLOSION : i % 4 == 0 ? ParticleTypes.LARGE_SMOKE : PcParticles.FIRE_PUFF.get(), p, out);
        }
        send(level, ParticleTypes.FLASH, center.x, center.y, center.z, 1, 0, 0, 0, 0);
    }

    /**
     * Ударная волна взрыва энергии {@code energyJ}: вспышка, взрывные клубы в центре, кольцо пыли по земле и столб дыма.
     * Размеры растут как корень кубический из энергии (подобие Хопкинсона), от 1 МДж — радиус около 4 блоков.
     */
    public static void blast(ServerLevel level, Vec3 center, double energyJ, RandomSource r) {
        double scale = Math.max(1, Math.cbrt(Math.max(energyJ, 1) / 1e6));
        double radius = Math.min(40, 4 * scale);
        send(level, ParticleTypes.FLASH, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        int emitters = (int) Math.min(12, 1 + scale);
        for (int i = 0; i < emitters; i++)
            send(level, ParticleTypes.EXPLOSION_EMITTER, center.x + r.nextGaussian() * radius * 0.25, center.y + r.nextDouble() * radius * 0.3,
                    center.z + r.nextGaussian() * radius * 0.25, 1, 0, 0, 0, 0);
        // the shock front over the ground: dust running outwards
        int ring = (int) Math.min(240, 40 * scale);
        for (int i = 0; i < ring; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            Vec3 dir = new Vec3(Math.cos(a), 0.05 + 0.1 * r.nextDouble(), Math.sin(a));
            fly(level, i % 3 == 0 ? PcParticles.SMOKE_PUFF.get() : ParticleTypes.POOF, center.add(dir.scale(1.5)),
                    dir.scale(radius / 12 * (0.6 + 0.6 * r.nextDouble())));
        }
        // the mushroom of hot gas and dust rising over the site
        int column = (int) Math.min(120, 20 * scale);
        for (int i = 0; i < column; i++)
            fly(level, i % 2 == 0 ? PcParticles.SMOKE_PUFF.get() : ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, center.add(r.nextGaussian() * radius * 0.2, r.nextDouble() * 2, r.nextGaussian() * radius * 0.2),
                    new Vec3(r.nextGaussian() * 0.02, 0.12 + 0.1 * r.nextDouble(), r.nextGaussian() * 0.02));
    }

    /** Осколки блока {@code state} у {@code at}: брызги кусков материала. */
    public static void shards(ServerLevel level, BlockState state, Vec3 at, int n) {
        send(level, new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, n, 0.4, 0.4, 0.4, 0.4);
    }
}

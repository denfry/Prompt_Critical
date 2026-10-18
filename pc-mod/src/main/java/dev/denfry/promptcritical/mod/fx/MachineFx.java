package dev.denfry.promptcritical.mod.fx;

import dev.denfry.promptcritical.mod.boiler.BoilerGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Частицы и звуки машин на клиенте (04a §4a: машину слышно и видно раньше, чем открыт щит): дым, искры, пламя,
 * пыль, стружка, брызги, выхлоп. Точки задаются в пикселях модели (фасад на север) от угла контроллера. Вызывается
 * из клиентского тика BE; на сервере ничего не делает.
 */
public final class MachineFx {
    private static final ParticleOptions SAND_DUST = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());
    private static final ParticleOptions IRON_CHIPS = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.IRON_NUGGET));

    private final Level level;
    private final BlockPos controller;
    private final Direction facing;
    private final RandomSource random;

    private MachineFx(Level level, BlockPos controller, Direction facing) {
        this.level = level;
        this.controller = controller;
        this.facing = facing;
        this.random = level.random;
    }

    /** Эффекты машины; {@code null} — не клиент. */
    public static MachineFx of(Level level, BlockPos controller, Direction facing) {
        return level != null && level.isClientSide ? new MachineFx(level, controller, facing) : null;
    }

    public RandomSource random() {
        return random;
    }

    /** Точка модели в мире. */
    public Vec3 at(double px, double py, double pz) {
        return BoilerGeometry.world(controller, facing, px, py, pz);
    }

    private void emit(ParticleOptions p, Vec3 at, double spread, double vx, double vy, double vz) {
        level.addParticle(p, at.x + (random.nextDouble() - 0.5) * spread, at.y, at.z + (random.nextDouble() - 0.5) * spread, vx, vy, vz);
    }

    /** Дым из трубы или летка: {@code black} — плотный недожог. */
    public void smoke(Vec3 at, boolean black, double spread) {
        level.addAlwaysVisibleParticle(black ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CAMPFIRE_COSY_SMOKE, true,
                at.x + (random.nextDouble() - 0.5) * spread, at.y, at.z + (random.nextDouble() - 0.5) * spread, 0, 0.05, 0);
    }

    /** Лёгкий дымок (остывающая форма, тлеющий слой). */
    public void wisp(Vec3 at, double spread) {
        emit(ParticleTypes.SMOKE, at, spread, 0, 0.03, 0);
    }

    /** Языки пламени (дверца, леток, слой на решётке). */
    public void flame(Vec3 at, double spread) {
        emit(ParticleTypes.SMALL_FLAME, at, spread, 0, 0.01 + random.nextDouble() * 0.02, 0);
    }

    /** Искры: вылетают вверх и в стороны, падают (вагранка, разливка). */
    public void sparks(Vec3 at, int n) {
        for (int i = 0; i < n; i++) emit(ParticleTypes.LAVA, at, 0.3, 0, 0, 0);
    }

    /** Капля расплава с жёлоба. */
    public void drip(Vec3 at) {
        emit(ParticleTypes.DRIPPING_LAVA, at, 0.1, 0, 0, 0);
    }

    /** Клуб пара: выхлоп машины, свисток. {@code dir} — направление струи, блоков/тик. */
    public void puff(Vec3 at, Vec3 dir, int n) {
        for (int i = 0; i < n; i++) {
            emit(ParticleTypes.CLOUD, at, 0.15, dir.x + (random.nextDouble() - 0.5) * 0.03, dir.y + random.nextDouble() * 0.04,
                    dir.z + (random.nextDouble() - 0.5) * 0.03);
        }
    }

    /** Песчаная пыль (встряхивание, выбивка). */
    public void dust(Vec3 at, double spread, int n) {
        for (int i = 0; i < n; i++) emit(SAND_DUST, at, spread, 0, 0.05 + random.nextDouble() * 0.05, 0);
    }

    /** Стружка из-под резца. */
    public void chips(Vec3 at, int n) {
        for (int i = 0; i < n; i++) {
            emit(IRON_CHIPS, at, 0.05, (random.nextDouble() - 0.5) * 0.12, 0.05 + random.nextDouble() * 0.08,
                    (random.nextDouble() - 0.5) * 0.12);
        }
    }

    /** Брызги на зеркале воды. */
    public void splash(Vec3 at, double spread, int n) {
        for (int i = 0; i < n; i++) emit(ParticleTypes.SPLASH, at, spread, 0, 0.1, 0);
    }

    /** Капель из сальника. */
    public void leak(Vec3 at) {
        emit(ParticleTypes.DRIPPING_WATER, at, 0.05, 0, 0, 0);
    }

    public void sound(Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.BLOCKS, volume, pitch * (0.95f + random.nextFloat() * 0.1f), false);
    }

    /** Случайное событие с частотой {@code perSecond} в секунду при вызове каждый тик. */
    public boolean chance(double perSecond) {
        return random.nextDouble() < perSecond / 20.0;
    }
}

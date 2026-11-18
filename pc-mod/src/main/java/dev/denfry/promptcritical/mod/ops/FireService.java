package dev.denfry.promptcritical.mod.ops;

import dev.denfry.promptcritical.mod.body.ShockService;
import dev.denfry.promptcritical.mod.grid.GridDevice;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.npp.SfrReactorBlockEntity;
import dev.denfry.promptcritical.sim.ops.FireSuppression;
import dev.denfry.promptcritical.sim.ops.FireSuppression.Agent;
import dev.denfry.promptcritical.sim.ops.FireSuppression.Effect;
import dev.denfry.promptcritical.sim.ops.FireSuppression.FireClass;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Пожар в мире для систем пожаротушения (13 §13.5): горящие блоки огня. Класс очага — по окружению: у натриевой течи
 * быстрого реактора — D (металл), у устройства электросети — E (электроустановка), над нефтепроявлением, природным
 * битумом или на резервуаре Create с нефтепродуктом — B (пролив и резервуар, §13.5.4), иначе A (твёрдые горючие).
 * Пожаров класса C (струя газа) в мире пока нет: газопроводы не горят.
 */
public final class FireService {
    /** Мощность одного горящего блока, кВт [моё: порядок горения штабеля древесины на 1 м², SFPE — 0,3–1 МВт/м²]. */
    public static final double KW_PER_FIRE_BLOCK = 300;
    /** Радиус влияния натриевого пожара БН и устройства сети на класс очага, блоков. */
    static final int SODIUM_RADIUS = 8, ELECTRICAL_RADIUS = 1;

    private FireService() {
    }

    public static boolean burning(Level level, BlockPos p) {
        return level.getBlockState(p).getBlock() instanceof BaseFireBlock;
    }

    /** Горящие блоки в кубе радиуса {@code r}. */
    public static List<BlockPos> fires(Level level, BlockPos center, int r) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r)))
            if (burning(level, p)) out.add(p.immutable());
        return out;
    }

    /** Мощность пожара в зоне, кВт. */
    public static double powerKw(Level level, BlockPos center, int r) {
        return fires(level, center, r).size() * KW_PER_FIRE_BLOCK;
    }

    /** Горючие жидкости класса B в резервуарах Create (нефть, нефтепродукты, спирты, смолы). */
    public static boolean hydrocarbon(net.minecraft.world.level.material.Fluid f) {
        for (var e : java.util.List.of(dev.denfry.promptcritical.mod.registry.PcFluids.CRUDE_OIL, dev.denfry.promptcritical.mod.registry.PcFluids.GASOLINE,
                dev.denfry.promptcritical.mod.registry.PcFluids.KEROSENE, dev.denfry.promptcritical.mod.registry.PcFluids.DIESEL_FUEL,
                dev.denfry.promptcritical.mod.registry.PcFluids.FUEL_OIL, dev.denfry.promptcritical.mod.registry.PcFluids.NGL,
                dev.denfry.promptcritical.mod.registry.PcFluids.NATURAL_GASOLINE, dev.denfry.promptcritical.mod.registry.PcFluids.PYROLYSIS_GASOLINE,
                dev.denfry.promptcritical.mod.registry.PcFluids.PYROLYSIS_FUEL_OIL, dev.denfry.promptcritical.mod.registry.PcFluids.CRUDE_BENZOL,
                dev.denfry.promptcritical.mod.registry.PcFluids.COAL_TAR, dev.denfry.promptcritical.mod.registry.PcFluids.METHANOL))
            if (f.isSame(e.source.get())) return true;
        return false;
    }

    /** Резервуар Create с горючей жидкостью под клеткой (контроллер), {@code null} — нет. */
    @Nullable
    public static com.simibubi.create.content.fluids.tank.FluidTankBlockEntity tankBelow(Level level, BlockPos p) {
        if (level.getBlockEntity(p.below()) instanceof com.simibubi.create.content.fluids.tank.FluidTankBlockEntity t) {
            var c = t.getControllerBE();
            if (c != null && !c.getTankInventory().getFluid().isEmpty() && hydrocarbon(c.getTankInventory().getFluid().getFluid())) return c;
        }
        return null;
    }

    /** Пролив класса B: огонь над нефтепроявлением, битумом или резервуаром с нефтепродуктом. */
    public static boolean poolFire(Level level, BlockPos p) {
        var below = level.getBlockState(p.below());
        return below.is(dev.denfry.promptcritical.mod.registry.PcBlocks.OIL_SEEP.get())
                || below.is(dev.denfry.promptcritical.mod.registry.PcBlocks.NATURAL_BITUMEN.get()) || tankBelow(level, p) != null
                || below.is(dev.denfry.promptcritical.mod.registry.PcBlocks.CRUDE_OIL.get());
    }

    /** Класс очага у клетки. */
    public static FireClass classAt(Level level, BlockPos p) {
        if (level instanceof ServerLevel sl)
            for (SfrReactorBlockEntity s : Ironworks.blockEntities(sl, new AABB(p).inflate(SODIUM_RADIUS), SfrReactorBlockEntity.class))
                if (s.reactor().sodiumFireW() > 0) return FireClass.D;
        if (electricalNear(level, p)) return FireClass.E;
        if (poolFire(level, p)) return FireClass.B;
        return FireClass.A;
    }

    /** Рядом устройство электросети — электроустановка под напряжением (снятым считается только запертое и проверенное). */
    static boolean electricalNear(Level level, BlockPos p) {
        int r = ELECTRICAL_RADIUS;
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-r, -r, -r), p.offset(r, r, r))) {
            var be = level.getBlockEntity(q);
            if (be instanceof GridDevice) {
                if (be instanceof IsolationPoint ip && ip.isolatedPosition() && ip.noEnergy()) continue;
                return true;
            }
        }
        return false;
    }

    /**
     * Подать вещество на очаги в радиусе {@code r}: где вещество годится — огонь гаснет; вода на металл — вспышка с
     * водородом, вода и пена на электроустановку — удар током тому, кто тушит.
     *
     * @return погашено очагов
     */
    public static int apply(ServerLevel level, BlockPos center, int r, Agent agent, @Nullable Player who) {
        int out = 0;
        for (BlockPos p : fires(level, center, r)) {
            FireClass c = classAt(level, p);
            Effect e = FireSuppression.effect(c, agent, c == FireClass.E);
            switch (e) {
                case EFFECTIVE -> {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    level.sendParticles(ParticleTypes.CLOUD, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 4, 0.3, 0.2, 0.3, 0.02);
                    out++;
                }
                case DANGEROUS -> {
                    if (c == FireClass.D) {
                        // натрий + вода: водород и вспышка, огонь не гаснет
                        level.explode(null, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 1.0f, false, Level.ExplosionInteraction.NONE);
                        if (who != null) who.displayClientMessage(OpsFireText.msg("water_on_metal").withStyle(ChatFormatting.DARK_RED), true);
                    } else if (who != null) {
                        who.hurt(new DamageSource(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                                .getOrThrow(ShockService.ELECTROCUTION)), 6);
                        who.displayClientMessage(OpsFireText.msg("water_on_live").withStyle(ChatFormatting.DARK_RED), true);
                    }
                }
                case INEFFECTIVE -> {
                    // вода без пены на горящую жидкость разбрызгивает её: площадь пролива +20 % в минуту (§13.5.4)
                    if (c == FireClass.B && agent == Agent.WATER && level.random.nextDouble() < FireSuppression.WATER_ON_B_GROWTH_PER_S)
                        spread(level, p);
                }
            }
        }
        if (out > 0) level.playSound(null, center, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 1.0f);
        return out;
    }

    /** Брызги горящей жидкости: огонь на соседнюю свободную клетку с опорой. */
    static void spread(ServerLevel level, BlockPos p) {
        for (int k = 0; k < 4; k++) {
            BlockPos q = p.offset(level.random.nextInt(3) - 1, 0, level.random.nextInt(3) - 1);
            if (level.getBlockState(q).isAir() && level.getBlockState(q.below()).isFaceSturdy(level, q.below(), net.minecraft.core.Direction.UP)) {
                level.setBlock(q, net.minecraft.world.level.block.BaseFireBlock.getState(level, q), 3);
                return;
            }
        }
    }

    /**
     * Пена из ствола или пенокамеры, {@code lpm} л/мин раствора на очаги в радиусе {@code r} (§13.5.4). Пролив класса
     * B гаснет, только если интенсивность r = Q/A не ниже 4,1 л/(мин·м²): площадь сокращается за 300 с · 4,1 / r
     * (каждая клетка за секунду гаснет с вероятностью 1/t); слабее — пена разрушается пламенем. Очаги других классов —
     * по таблице классов, как у огнетушителя.
     *
     * @return погашено очагов
     */
    public static int foam(ServerLevel level, BlockPos center, int r, double lpm) {
        List<BlockPos> pool = new ArrayList<>(), other = new ArrayList<>();
        for (BlockPos p : fires(level, center, r)) (classAt(level, p) == FireClass.B ? pool : other).add(p);
        int out = 0;
        if (!pool.isEmpty()) {
            double t = FireSuppression.foamExtinctionS(lpm / pool.size());
            if (Double.isFinite(t))
                for (BlockPos p : pool)
                    if (level.random.nextDouble() < 1 / Math.max(1, t)) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                        TankFires.foamed(level, p);
                        out++;
                    }
        }
        if (!other.isEmpty()) {
            for (BlockPos p : other) {
                if (FireSuppression.effect(classAt(level, p), Agent.FOAM, false) == Effect.EFFECTIVE) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    out++;
                }
            }
        }
        if (out > 0) level.playSound(null, center, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 0.8f);
        return out;
    }
}

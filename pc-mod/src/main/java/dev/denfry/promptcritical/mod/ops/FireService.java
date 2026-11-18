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
 * быстрого реактора — D (металл), у устройства электросети — E (электроустановка), иначе A (твёрдые горючие).
 * Пожаров классов B и C (пролив, струя газа) в мире пока нет: их источники (резервуары 09, газопроводы) не горят.
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

    /** Класс очага у клетки. */
    public static FireClass classAt(Level level, BlockPos p) {
        if (level instanceof ServerLevel sl)
            for (SfrReactorBlockEntity s : Ironworks.blockEntities(sl, new AABB(p).inflate(SODIUM_RADIUS), SfrReactorBlockEntity.class))
                if (s.reactor().sodiumFireW() > 0) return FireClass.D;
        if (electricalNear(level, p)) return FireClass.E;
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
                }
            }
        }
        if (out > 0) level.playSound(null, center, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 1.0f);
        return out;
    }
}

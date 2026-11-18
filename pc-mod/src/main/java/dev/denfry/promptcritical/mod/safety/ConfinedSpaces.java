package dev.denfry.promptcritical.mod.safety;

import dev.denfry.promptcritical.sim.ops.AtmosphereHazard;
import dev.denfry.promptcritical.sim.ops.ConfinedSpaceAtmosphere;
import dev.denfry.promptcritical.sim.ops.ConfinedSpaceAtmosphere.Gas;
import dev.denfry.promptcritical.sim.ops.Contaminant;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Замкнутые пространства станции (13 §13.2.6): объёмы за люками {@code manway} и действие их атмосферы на игрока.
 * Объём — заливка воздуха за люком не больше {@value ConfinedSpaceAtmosphere#MAX_VOLUME} блоков; больше — это помещение,
 * а не замкнутое пространство.
 */
public final class ConfinedSpaces {
    public static final ResourceKey<DamageType> ASPHYXIATION =
            ResourceKey.create(Registries.DAMAGE_TYPE, dev.denfry.promptcritical.mod.PromptCritical.id("asphyxiation"));
    private static final Map<Level, Set<ManwayBlockEntity>> MANWAYS = new WeakHashMap<>();
    static final String TAG = "promptcritical_confined";

    private ConfinedSpaces() {
    }

    static void add(ManwayBlockEntity m) {
        MANWAYS.computeIfAbsent(m.getLevel(), k -> new HashSet<>()).add(m);
    }

    static void remove(ManwayBlockEntity m) {
        Set<ManwayBlockEntity> s = MANWAYS.get(m.getLevel());
        if (s != null) s.remove(m);
    }

    /** Люк, за которым лежит точка; {@code null} — точка не в замкнутом пространстве. */
    @Nullable
    public static ManwayBlockEntity at(Level level, BlockPos pos) {
        Set<ManwayBlockEntity> s = MANWAYS.get(level);
        if (s == null) return null;
        long key = pos.asLong();
        for (ManwayBlockEntity m : s) if (!m.isRemoved() && m.contains(key)) return m;
        return null;
    }

    /** Пробу берут изнутри или через открытый люк, стоя не дальше 2 блоков от него; {@code null} — пробы нет. */
    @Nullable
    public static ManwayBlockEntity sampledFrom(Level level, net.minecraft.world.entity.player.Player p) {
        ManwayBlockEntity in = at(level, p.blockPosition());
        if (in != null) return in;
        Set<ManwayBlockEntity> s = MANWAYS.get(level);
        if (s == null) return null;
        for (ManwayBlockEntity m : s)
            if (!m.isRemoved() && m.confined() && m.hatchOpen() && m.getBlockPos().getCenter().distanceToSqr(p.position()) <= 2.5 * 2.5) return m;
        return null;
    }

    /**
     * Заливка воздуха от {@code start}: множество блоков или {@code null}, если объём больше
     * {@value ConfinedSpaceAtmosphere#MAX_VOLUME} (не замкнутое пространство) или стартовый блок не воздух.
     */
    @Nullable
    static Set<Long> flood(ServerLevel level, BlockPos start) {
        if (!open(level, start)) return null;
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start.asLong());
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos q = p.relative(d);
                if (!level.isLoaded(q) || !seen.add(q.asLong())) continue;
                if (!open(level, q)) {
                    seen.remove(q.asLong());
                    continue;
                }
                if (seen.size() > ConfinedSpaceAtmosphere.MAX_VOLUME) return null;
                queue.add(q);
            }
        }
        return seen;
    }

    private static boolean open(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.isAir() || (s.getCollisionShape(level, p).isEmpty() && s.getFluidState().isEmpty());
    }

    // ------------------------------------------------------------------ человек в атмосфере

    /**
     * Секунда дыхания атмосферой {@code a} (§13.2.6). Изолирующий аппарат отрезает всё; противогаз снимает H₂S и CO по
     * своему фильтру, но не кислородное голодание.
     *
     * @return стадии {o2, h2s, cohb} для испытаний
     */
    public static int[] breathe(ServerPlayer p, ConfinedSpaceAtmosphere a, long now) {
        CompoundTag d = p.getPersistentData().getCompound(TAG);
        double cohb = d.getDouble("cohb");
        long last = d.getLong("tick");
        if (last > 0 && now - last > 20) cohb = AtmosphereHazard.cohbStep(cohb, 0, 0.209, (now - last - 20) / 20.0); // вне пространства
        boolean isolated = PpeService.isolated(p);
        double o2 = isolated ? 0.209 : a.fraction(Gas.O2);
        double h2s = isolated ? 0 : PpeService.inhaled(p, Contaminant.INORGANIC_GAS, a.ppm(Gas.H2S), o2, 1);
        double co = isolated ? 0 : PpeService.inhaled(p, Contaminant.CARBON_MONOXIDE, a.ppm(Gas.CO), o2, 1);
        cohb = AtmosphereHazard.cohbStep(cohb, co * 1e-6, o2, 1);
        int so2 = AtmosphereHazard.o2Stage(o2), sh = AtmosphereHazard.h2sStage(h2s), sc = AtmosphereHazard.cohbStage(cohb);
        int lowO2 = so2 >= 4 ? d.getInt("low_o2") + 1 : 0, highH2s = sh >= 3 ? d.getInt("h2s") + 1 : 0;
        d.putDouble("cohb", cohb);
        d.putLong("tick", now);
        d.putInt("low_o2", lowO2);
        d.putInt("h2s", highH2s);
        p.getPersistentData().put(TAG, d);
        if (p.isCreative() || p.isSpectator()) return new int[] {so2, sh, sc};
        boolean faint = (so2 >= 4 && lowO2 >= dev.denfry.promptcritical.sim.ops.AtmosphereHazard.O2_FAINT_S) || sh >= 4
                || (sh == 3 && highH2s >= AtmosphereHazard.H2S_FAINT_S) || sc >= 3;
        float damage = 0;
        if (so2 >= 1) effect(p, MobEffects.MOVEMENT_SLOWDOWN, so2 >= 2 ? 1 : 0);
        if (so2 >= 2) effect(p, MobEffects.CONFUSION, 0);
        if (so2 >= 3) {
            effect(p, MobEffects.BLINDNESS, 0);
            effect(p, MobEffects.WEAKNESS, 0);
            damage = Math.max(damage, so2 >= 4 ? 2 : 1);
        }
        if (sh >= 1) effect(p, MobEffects.CONFUSION, 0);
        if (sh >= 2) effect(p, MobEffects.POISON, 0);
        if (sh >= 4) damage = Math.max(damage, 4);
        if (sc >= 1) effect(p, MobEffects.CONFUSION, 0);
        if (sc >= 2) effect(p, MobEffects.MOVEMENT_SLOWDOWN, 0);
        if (faint) {
            // без сознания: не может двигаться, ничего не видит; вытащить — только треногой или в аппарате
            effect(p, MobEffects.MOVEMENT_SLOWDOWN, 9);
            effect(p, MobEffects.BLINDNESS, 0);
            effect(p, MobEffects.DIG_SLOWDOWN, 4);
            p.setDeltaMovement(0, Math.min(0, p.getDeltaMovement().y), 0);
            if (d.getInt("faint_msg") == 0) p.displayClientMessage(SafetyText.msg("faint").withStyle(ChatFormatting.DARK_RED), true);
            d.putInt("faint_msg", 1);
        } else {
            d.putInt("faint_msg", 0);
        }
        if (sc >= 4) damage = Math.max(damage, 4);
        if (damage > 0) p.hurt(asphyxiation(p.serverLevel()), damage);
        return new int[] {so2, sh, sc};
    }

    /** Переставить игрока на блок (через люк). Поддельный игрок испытаний не принимает пакет телепорта. */
    static void move(ServerPlayer p, BlockPos to) {
        p.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
        if (p instanceof net.neoforged.neoforge.common.util.FakePlayer) p.setPos(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
    }

    /** Карбоксигемоглобин игрока (доля). */
    public static double cohb(ServerPlayer p) {
        return p.getPersistentData().getCompound(TAG).getDouble("cohb");
    }

    private static void effect(ServerPlayer p, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> e, int amp) {
        p.addEffect(new MobEffectInstance(e, 40, amp, false, false));
    }

    static DamageSource asphyxiation(ServerLevel level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ASPHYXIATION));
    }
}

package dev.denfry.promptcritical.mod.body;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.grid.GridWorld;
import dev.denfry.promptcritical.sim.body.BodyProfile;
import dev.denfry.promptcritical.sim.body.ElectricShock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Касание голой шины под напряжением ({@link ElectricShock}). Ток идёт, только если тело заземлено: стоит на земле,
 * в воде или держится за лестницу. Шерстяной ковёр или блок шерсти под ногами — диэлектрический коврик до 1 кВ.
 * Проверка — раз в {@link #PERIOD_TICKS} тиков.
 */
public final class ShockService {
    public static final ResourceKey<DamageType> ELECTROCUTION =
            ResourceKey.create(Registries.DAMAGE_TYPE, PromptCritical.id("electrocution"));
    static final int PERIOD_TICKS = 2;
    private static final double DT_S = PERIOD_TICKS / 20.0;
    /** Выше этого тока касание — ещё и ожог дугой, А. */
    private static final double ARC_BURN_A = 1.0;

    private static final Map<UUID, ElectricShock.Contact> CONTACTS = new HashMap<>();

    private ShockService() {
    }

    public static void tick(ServerPlayer p) {
        if (p.tickCount % PERIOD_TICKS == 0) check(p);
    }

    /** Один шаг касания длиной {@link #PERIOD_TICKS} тиков; что почувствовал игрок. */
    public static ElectricShock.Effect check(ServerPlayer p) {
        ElectricShock.Contact c = CONTACTS.computeIfAbsent(p.getUUID(), k -> new ElectricShock.Contact());
        double touchV = 0;
        if (ServerConfig.BODY_ELECTRIC_SHOCK.get() && !p.isCreative() && !p.isSpectator() && p.isAlive()) {
            touchV = touchVoltageV(p);
        }
        double current = touchV > 0 ? ElectricShock.bodyCurrentA(touchV, p.isInWaterOrRain()) : 0;
        BodyProfile body = BodyService.profile(p);
        boolean started = c.seconds() == 0;
        ElectricShock.Effect effect = c.step(body, current, DT_S);
        switch (effect) {
            case NONE -> {
            }
            case PERCEPTIBLE -> {
                if (started) message(p, "promptcritical.body.shock.felt", ChatFormatting.YELLOW, current);
            }
            case CANNOT_LET_GO -> {
                // мышцы руки сведены: отойти нельзя, пока ток идёт
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, PERIOD_TICKS + 4, 9, false, false));
                p.setDeltaMovement(Vec3.ZERO);
                p.hurtMarked = true;
                if (started) message(p, "promptcritical.body.shock.held", ChatFormatting.RED, current);
                p.hurt(source(p.serverLevel()), 1.0f);
            }
            case FIBRILLATION -> {
                CONTACTS.remove(p.getUUID());
                p.hurt(source(p.serverLevel()), Float.MAX_VALUE);
            }
        }
        if (current >= ARC_BURN_A) p.igniteForSeconds(3);
        return effect;
    }

    public static void forget(ServerPlayer p) {
        CONTACTS.remove(p.getUUID());
    }

    /** Фазное напряжение самой «горячей» шины, которой касается заземлённый игрок, В. */
    static double touchVoltageV(ServerPlayer p) {
        SimLevelBinding b = SimBindings.get(p.serverLevel());
        GridWorld grids = b == null ? null : b.grids();
        if (grids == null || !grids.hasBusbars()) return 0;
        boolean earthed = p.onGround() || p.isInWater() || p.onClimbable();
        if (!earthed) return 0;
        AABB box = p.getBoundingBox().inflate(0.05);
        double lineV = 0;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            lineV = Math.max(lineV, grids.busbarVoltageV(pos));
        }
        double touchV = ElectricShock.touchVoltageV(lineV);
        if (touchV > 0 && touchV <= ElectricShock.MAT_RATED_V && onMat(p)) return 0;
        return touchV;
    }

    private static boolean onMat(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        return l.getBlockState(p.blockPosition()).is(BlockTags.WOOL_CARPETS) || l.getBlockState(p.getOnPos()).is(BlockTags.WOOL);
    }

    private static void message(ServerPlayer p, String key, ChatFormatting color, double currentA) {
        p.displayClientMessage(Component.translatable(key, Math.round(currentA * 1000)).withStyle(color), true);
    }

    private static DamageSource source(ServerLevel level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ELECTROCUTION));
    }
}

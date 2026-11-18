package dev.denfry.promptcritical.mod.safety;

import dev.denfry.promptcritical.mod.acoustics.HearingProtectionItem;
import dev.denfry.promptcritical.sim.ops.Contaminant;
import dev.denfry.promptcritical.sim.ops.FilterType;
import dev.denfry.promptcritical.sim.ops.GasMask;
import dev.denfry.promptcritical.sim.ops.Ppe;
import dev.denfry.promptcritical.sim.ops.Scba;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Действие СИЗ 13 §13.7 на игрока. Дыхание: изолирующий аппарат с воздухом делит любую концентрацию на
 * {@value Ppe#SCBA_FACTOR}, противогаз (на голове или вставкой в каске) — на {@value Ppe#GAS_MASK_FACTOR} по газу своего
 * фильтра, если O₂ не ниже {@value Ppe#MASK_MIN_O2}, и нагружает фильтр. Это используют токсичные облака
 * ({@code ToxicReleaseService}), рудничный газ ({@code MineWorld}), смог ({@code AirQualityService}), аэрозоли
 * радиоактивного облака и загрязнения ({@code RadiationService}, {@code ContaminationService}) и атмосфера замкнутых
 * пространств. Каска ослабляет падающие блоки, костюм — огонь и излучение факела, привязь с фалом — падение, наушники
 * во вставке каски — шум ({@code NoiseService}).
 */
public final class PpeService {
    static final String TAG = "promptcritical_ppe";

    private PpeService() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PpeService::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(PpeService::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(PpeService::onFall);
    }

    // ------------------------------------------------------------------ дыхание

    /** Надетый изолирующий аппарат с воздухом. */
    public static boolean isolated(Player p) {
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        return PpeItem.is(chest, PpeItem.Kind.SCBA) && PpeItem.air(chest) > 0;
    }

    /** Противогаз игрока: на голове или вставкой в каске; {@code null} — нет. */
    @Nullable
    static ItemStack maskStack(Player p) {
        ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);
        if (PpeItem.is(head, PpeItem.Kind.GAS_MASK)) return head;
        if (PpeItem.is(head, PpeItem.Kind.HARD_HAT)) {
            ItemStack insert = SafetyComponents.insert(head);
            if (insert != null && PpeItem.is(insert, PpeItem.Kind.GAS_MASK)) return insert;
        }
        return null;
    }

    private static void storeMask(Player p, ItemStack mask, GasMask m) {
        if (m.filter() == null) return;
        mask.set(SafetyComponents.FILTER_TYPE.get(), new SafetyComponents.Filter(m.filter(), m.loaded()));
        ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);
        if (head != mask) SafetyComponents.setInsert(head, mask); // вставка хранится копией в компоненте каски
    }

    /**
     * Концентрация примеси {@code c} во вдыхаемом воздухе за {@code dtS} секунд при доле кислорода {@code o2}: газ — ppm,
     * частицы — мкг/м³. Фильтр противогаза нагружается.
     */
    public static double inhaled(Player p, Contaminant c, double concentration, double o2, double dtS) {
        if (concentration <= 0) return concentration;
        if (isolated(p)) return Scba.inhaled(concentration);
        ItemStack mask = maskStack(p);
        if (mask == null) return concentration;
        GasMask m = PpeItem.mask(mask);
        double in = m.inhale(dtS, c, concentration, o2);
        if (in != concentration) storeMask(p, mask, m);
        return in;
    }

    /** Доля аэрозоля, попадающая под маску (радиоактивное облако, пыль загрязнения): аппарат, фильтр P или всё. */
    public static double aerosolFraction(Player p) {
        if (isolated(p)) return 1 / Ppe.SCBA_FACTOR;
        ItemStack mask = maskStack(p);
        if (mask == null) return 1;
        GasMask m = PpeItem.mask(mask);
        return m.filter() == FilterType.P && !m.spent() ? 1 / Ppe.GAS_MASK_FACTOR : 1;
    }

    /** Органы дыхания закрыты от смога: аппарат или противогаз с несработанным фильтром E или P. */
    public static boolean breathingProtected(Player p) {
        if (isolated(p)) return true;
        ItemStack mask = maskStack(p);
        if (mask == null) return false;
        GasMask m = PpeItem.mask(mask);
        return !m.spent() && (m.filter() == FilterType.E || m.filter() == FilterType.P);
    }

    /** Противошумные наушники вставлены в каску. */
    public static boolean earsProtected(Player p) {
        ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);
        if (!PpeItem.is(head, PpeItem.Kind.HARD_HAT)) return false;
        ItemStack insert = SafetyComponents.insert(head);
        return insert != null && insert.getItem() instanceof HearingProtectionItem;
    }

    /** Множитель теплового излучения на игрока (факел 13 §13.4.3). */
    public static double radiantFactor(Player p) {
        return PpeItem.is(p.getItemBySlot(EquipmentSlot.CHEST), PpeItem.Kind.FIRE_SUIT) ? Ppe.FIRE_SUIT_RADIANT : 1;
    }

    // ------------------------------------------------------------------ раз в секунду

    private static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.isSpectator()) return;
        if ((p.level().getGameTime() + p.getId()) % 20 != 0) return;
        tick(p, 1);
    }

    /** Секунда СИЗ: расход воздуха аппарата, свисток, замедление и перегрев в огнезащитном костюме. */
    public static void tick(ServerPlayer p, double dtS) {
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        if (PpeItem.is(chest, PpeItem.Kind.SCBA)) {
            Scba s = new Scba(PpeItem.air(chest));
            boolean whistled = s.whistle(), had = !s.empty();
            if (s.breathe(dtS)) {
                chest.set(SafetyComponents.AIR_REMAINING.get(), s.airL());
                if (s.whistle() && !whistled) {
                    p.level().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 1.0f, 2.0f);
                    p.displayClientMessage(SafetyText.msg("scba_whistle").withStyle(ChatFormatting.GOLD), true);
                }
                if (s.empty() && had) p.displayClientMessage(SafetyText.msg("scba_empty").withStyle(ChatFormatting.RED), true);
            }
        }
        CompoundTag d = p.getPersistentData().getCompound(TAG);
        if (PpeItem.is(chest, PpeItem.Kind.FIRE_SUIT)) {
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false));
            boolean working = p.isSprinting() || p.swinging || p.getDeltaMovement().horizontalDistanceSqr() > 1e-3;
            double heat = Math.max(0, d.getDouble("heat") + (working ? dtS : -dtS));
            if (heat >= Ppe.FIRE_SUIT_OVERHEAT_S) {
                if (d.getDouble("heat") < Ppe.FIRE_SUIT_OVERHEAT_S)
                    p.displayClientMessage(SafetyText.msg("fire_suit_hot").withStyle(ChatFormatting.GOLD), true);
                p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.HUNGER, 40, 0, false, false));
            }
            d.putDouble("heat", Math.min(heat, Ppe.FIRE_SUIT_OVERHEAT_S * 2));
        } else {
            d.putDouble("heat", Math.max(0, d.getDouble("heat") - dtS));
        }
        p.getPersistentData().put(TAG, d);
    }

    // ------------------------------------------------------------------ урон

    private static void onIncomingDamage(LivingIncomingDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        e.setAmount((float) (e.getAmount() * damageFactor(p, e.getSource())));
    }

    /**
     * Множитель урона от СИЗ. Каска: падающие блоки ×{@value Ppe#HARD_HAT_FALLING} вместе с ванильными ×0,75 за любой
     * предмет на голове (поэтому здесь ×0,4). Костюм: огонь ×{@value Ppe#FIRE_SUIT_BURN}.
     */
    public static double damageFactor(Player p, net.minecraft.world.damagesource.DamageSource src) {
        double f = 1;
        if (src.is(DamageTypeTags.DAMAGES_HELMET) && PpeItem.is(p.getItemBySlot(EquipmentSlot.HEAD), PpeItem.Kind.HARD_HAT))
            f *= Ppe.HARD_HAT_FALLING / 0.75;
        if (src.is(DamageTypeTags.IS_FIRE) && PpeItem.is(p.getItemBySlot(EquipmentSlot.CHEST), PpeItem.Kind.FIRE_SUIT))
            f *= Ppe.FIRE_SUIT_BURN;
        return f;
    }

    private static void onFall(LivingFallEvent e) {
        if (!(e.getEntity() instanceof Player p) || !arrested(p)) return;
        e.setDamageMultiplier(0);
        if (e.getDistance() > Ppe.ARRESTED_FALL_M && !p.level().isClientSide)
            p.displayClientMessage(SafetyText.msg("fall_arrested").withStyle(ChatFormatting.GREEN), true);
    }

    /** Привязь надета, фал в инвентаре закреплён не дальше длины фала. */
    public static boolean arrested(Player p) {
        if (!PpeItem.is(p.getItemBySlot(EquipmentSlot.LEGS), PpeItem.Kind.SAFETY_HARNESS)) return false;
        for (ItemStack s : p.getInventory().items) {
            GlobalPos a = s.getItem() instanceof LifelineItem ? s.get(SafetyComponents.LIFELINE_ANCHOR.get()) : null;
            if (a != null && a.dimension() == p.level().dimension()
                    && a.pos().getCenter().distanceTo(p.position()) <= Ppe.LIFELINE_M) return true;
        }
        return false;
    }
}

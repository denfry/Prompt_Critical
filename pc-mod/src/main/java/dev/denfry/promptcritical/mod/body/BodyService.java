package dev.denfry.promptcritical.mod.body;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.body.BodyProfile;
import dev.denfry.promptcritical.sim.body.Physiology;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Тело игрока на сервере: хранение, подтверждение выбора, атрибуты. Рост — масштаб модели (хитбокс, глаза) и
 * шаг, масса — сопротивление отбрасыванию ударом и взрывной волной. Атрибуты — временные модификаторы: они
 * не пишутся в сохранение игрока и ставятся заново при входе и возрождении, поэтому смена формул не оставляет
 * в мире старых значений.
 */
public final class BodyService {
    static final ResourceLocation SCALE_ID = PromptCritical.id("body_height");
    static final ResourceLocation SPEED_ID = PromptCritical.id("body_stride");
    static final ResourceLocation KNOCKBACK_ID = PromptCritical.id("body_mass");
    static final ResourceLocation BLAST_ID = PromptCritical.id("body_mass_blast");

    private BodyService() {
    }

    public static PlayerBody body(Player p) {
        return p.getData(BodyAttachments.BODY.get());
    }

    /** Действующий профиль: выбранный или опорный, пока выбора не было. */
    public static BodyProfile profile(Player p) {
        return body(p).profile();
    }

    /** Масса игрока со снаряжением — для клети подъёма, кг. */
    public static double loadedMassKg(Player p) {
        return Physiology.loadedMassKg(profile(p));
    }

    /**
     * Принять выбор игрока. Тело выбирается один раз на мир: подтверждённый профиль меняет только оператор
     * ({@code /pc body reset}). {@code false} — выбор уже был, пакет проигнорирован.
     */
    public static boolean submit(ServerPlayer p, BodyProfile profile) {
        if (body(p).confirmed()) return false;
        p.setData(BodyAttachments.BODY.get(), new PlayerBody(profile, true));
        apply(p);
        return true;
    }

    /** Сбросить выбор (команда оператора): опорное тело и снова экран выбора. */
    public static void reset(ServerPlayer p) {
        BodyProfile was = profile(p);
        p.setData(BodyAttachments.BODY.get(), new PlayerBody(was, false));
        apply(p);
        request(p);
    }

    /** При входе: атрибуты по профилю, и экран выбора, если тело ещё не выбрано. */
    public static void onLogin(ServerPlayer p) {
        apply(p);
        if (!body(p).confirmed()) request(p);
    }

    static void request(ServerPlayer p) {
        if (p instanceof net.neoforged.neoforge.common.util.FakePlayer || p.connection == null
                || !p.connection.hasChannel(BodyPayloads.Request.TYPE)) return;
        PacketDistributor.sendToPlayer(p, new BodyPayloads.Request(profile(p)));
    }

    /** Поставить атрибуты тела. */
    public static void apply(Player p) {
        BodyProfile b = profile(p);
        set(p, Attributes.SCALE, SCALE_ID, Physiology.modelScale(b) - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(p, Attributes.MOVEMENT_SPEED, SPEED_ID, Physiology.strideSpeedFactor(b) - 1,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        double kb = Physiology.knockbackResistance(b);
        set(p, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, kb, AttributeModifier.Operation.ADD_VALUE);
        set(p, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, BLAST_ID, kb, AttributeModifier.Operation.ADD_VALUE);
    }

    private static void set(Player p, Holder<Attribute> attr, ResourceLocation id, double amount,
            AttributeModifier.Operation op) {
        AttributeInstance a = p.getAttribute(attr);
        if (a == null) return;
        if (amount == 0) {
            a.removeModifier(id);
            return;
        }
        a.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
    }
}

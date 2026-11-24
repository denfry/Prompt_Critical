package dev.denfry.promptcritical.mod.gear;

import dev.denfry.promptcritical.mod.body.BodyService;
import dev.denfry.promptcritical.mod.environment.EnvironmentService;
import dev.denfry.promptcritical.mod.item.ElectricGearData;
import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import dev.denfry.promptcritical.sim.body.BodyProfile;
import dev.denfry.promptcritical.sim.body.Physiology;
import dev.denfry.promptcritical.sim.environment.Environment;
import dev.denfry.promptcritical.sim.gear.HeatedSuit;
import dev.denfry.promptcritical.sim.gear.PoweredRespirator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Электроснаряжение на игроке (§15.8) и тепловой баланс тела на холоде.
 *
 * <p>Часы — те же, что у отравления газом и токсичных облаков: раз в секунду, секунда за секунду. Заряд на стеллаже
 * ламповой идёт по игровым часам ламп (тик — 3,6 с), то есть в 72 раза быстрее разряда [BALANCE].
 *
 * <p>Холод: каждую секунду {@link HeatedSuit#deficitW} по воздуху и ветру слоя environment у игрока, нагрузке (стоит,
 * идёт, бежит) и одежде — рабочая {@value HeatedSuit#WORK_CLOTHES_CLO} кло или костюм {@value HeatedSuit#SUIT_CLO} кло с
 * обогревом. Под крышей ветра нет; в выработках ниже уровня моря воздух не холоднее 10 °C (порода) [упрощение].
 * Температура ядра живёт в памяти сервера (после перезапуска — норма). Стадии 35 / 32 / 28 °C — замедление, слабость,
 * затем урон от холода.
 */
public final class ElectricGearService {
    static final double DT_S = 1;
    static final double MINE_FLOOR_C = 10;

    private static final class Body {
        double coreC = HeatedSuit.CORE_C;
        double x = Double.NaN, z;
        int stage;
    }

    private static final Map<UUID, Body> BODIES = new HashMap<>();

    private ElectricGearService() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ElectricGearService::onPlayerTick);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> BODIES.remove(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> BODIES.remove(e.getEntity().getUUID()));
    }

    private static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(p.level() instanceof ServerLevel level)) return;
        if ((level.getGameTime() + p.getId()) % 20 != 0 || p.isSpectator()) return;
        tick(level, p);
    }

    /** Одна секунда снаряжения и тела игрока. */
    public static void tick(ServerLevel level, Player p) {
        double[] air = air(level, p.blockPosition());
        long now = level.getGameTime();
        ItemStack chest = worn(p, ElectricGearItem.Kind.RESPIRATOR);
        if (chest != null) {
            PoweredRespirator r = ElectricGearItem.respirator(ElectricGearItem.data(chest));
            r.breathe(DT_S, 0, air[0]);
            chest.set(PcDataComponents.ELECTRIC_GEAR.get(), ElectricGearItem.of(r.pack(), r.filterMol(), now));
        }
        // радиологическая маска: вентилятор садит ту же батарею; фильтр P3 + йодный — без химической нагрузки
        ItemStack head = worn(p, ElectricGearItem.Kind.PAPR);
        if (head != null) {
            PoweredRespirator r = ElectricGearItem.respirator(ElectricGearItem.data(head));
            r.breathe(DT_S, 0, air[0]);
            head.set(PcDataComponents.ELECTRIC_GEAR.get(), ElectricGearItem.of(r.pack(), 0, now));
        }
        if (p.isCreative()) return;
        if (dev.denfry.promptcritical.mod.compat.CompatSwitch.bodyTemperatureIsExternal()) {
            // 16 §16.10.1, R60: температурой тела владеет Cold Sweat или Tough As Nails
            BODIES.remove(p.getUUID());
            return;
        }
        Body b = BODIES.computeIfAbsent(p.getUUID(), k -> new Body());
        BodyProfile prof = BodyService.profile(p);
        double met = activity(p, b).met;
        ItemStack legs = worn(p, ElectricGearItem.Kind.HEATED_SUIT);
        double clo = legs != null ? HeatedSuit.SUIT_CLO : HeatedSuit.WORK_CLOTHES_CLO;
        double deficit = HeatedSuit.deficitW(Physiology.bodySurfaceM2(prof), met, air[0], air[1], clo);
        if (legs != null) {
            HeatedSuit s = ElectricGearItem.suit(ElectricGearItem.data(legs));
            deficit -= s.heat(DT_S, deficit, air[0]);
            legs.set(PcDataComponents.ELECTRIC_GEAR.get(), ElectricGearItem.of(s.pack(), 0, now));
        }
        b.coreC = HeatedSuit.coreAfter(b.coreC, deficit, prof.massKg(), DT_S);
        apply(level, p, b);
    }

    /** Температура воздуха и ветер у игрока: {°C, м/с}. */
    static double[] air(ServerLevel level, BlockPos pos) {
        Environment.Sample s = EnvironmentService.sample(level, pos, 2);
        boolean sky = level.canSeeSky(pos.above());
        double t = s.airTempC();
        if (!sky && pos.getY() < level.getSeaLevel()) t = Math.max(t, MINE_FLOOR_C);
        return new double[] {t, sky ? s.meanWindMs() : 0.2};
    }

    private static Physiology.Activity activity(Player p, Body b) {
        double moved = Double.isNaN(b.x) ? 0 : Math.hypot(p.getX() - b.x, p.getZ() - b.z);
        b.x = p.getX();
        b.z = p.getZ();
        if (p.isSwimming() || p.isInWater()) return Physiology.Activity.SWIM;
        if (p.isSprinting()) return Physiology.Activity.SPRINT;
        return moved >= 1.0 ? Physiology.Activity.WALK : Physiology.Activity.REST;
    }

    private static void apply(ServerLevel level, Player p, Body b) {
        int stage = HeatedSuit.hypothermiaStage(b.coreC);
        if (stage > b.stage)
            p.displayClientMessage(Component.translatable("promptcritical.gear.cold.stage" + stage).withStyle(ChatFormatting.AQUA), true);
        b.stage = stage;
        if (stage >= 1) {
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, stage - 1, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, stage - 1, false, false));
        }
        if (stage >= 2) p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, stage - 2, false, false));
        if (stage >= 3) p.hurt(level.damageSources().freeze(), 1);
    }

    /** Надетый предмет этого вида или {@code null}. */
    public static ItemStack worn(Player p, ElectricGearItem.Kind kind) {
        ItemStack s = p.getItemBySlot(kind.slot);
        return s.getItem() instanceof ElectricGearItem g && g.kind() == kind ? s : null;
    }

    /**
     * Концентрация под маской для токсичного облака ({@code ToxicReleaseService}): надетый респиратор нагружает
     * фильтр и делит концентрацию на коэффициент защиты; вентилятор работает, если батарея держала его на прошлом
     * шаге.
     */
    public static double inhaledPpm(Player p, double ppm, double dtS) {
        ItemStack chest = worn(p, ElectricGearItem.Kind.RESPIRATOR);
        if (chest == null || ppm <= 0) return ppm;
        ElectricGearData d = ElectricGearItem.data(chest);
        PoweredRespirator r = ElectricGearItem.respirator(d);
        double in = r.inhale(dtS, ppm, d.powerW10() > 0);
        chest.set(PcDataComponents.ELECTRIC_GEAR.get(), d.withFilter(r.filterMol()));
        return in;
    }

    /** Температура ядра игрока, °C (для испытаний и подсказок). */
    public static double coreC(UUID player) {
        Body b = BODIES.get(player);
        return b == null ? HeatedSuit.CORE_C : b.coreC;
    }
}

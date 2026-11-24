package dev.denfry.promptcritical.mod.radiation;

import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.safety.PpeItem;
import dev.denfry.promptcritical.sim.radiation.GeigerCounter;
import dev.denfry.promptcritical.sim.radiation.PersonalDosimeter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Секунда радиационных СИЗ и индивидуальной дозиметрии игрока (04b §4b.7): фартук замедляет, шланг пневмокостюма рвётся
 * дальше {@value dev.denfry.promptcritical.sim.radiation.RadRespirator#HOSE_M} м от поста, ЭПД и ТЛД копят дозу внешнего
 * облучения (первый прибор каждого вида в инвентаре — второй на том же человеке ничего нового не меряет), ЭПД пищит по
 * уставкам, счётчик Гейгера в руке показывает поле с насыщением трубки.
 */
public final class RadGearService {
    private RadGearService() {
    }

    /** Вызывается раз в секунду из {@code PpeService.tick}. */
    public static void tick(ServerPlayer p) {
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        if (PpeItem.is(chest, PpeItem.Kind.LEAD_APRON)) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false));
        hose(p, chest);
        if (p.isCreative() || p.isSpectator()) return;
        double ext = RadiationService.externalRateSvH(p);
        ItemStack epd = first(p, PcItems.PERSONAL_DOSIMETER_EPD.get());
        if (epd != null) epd(p, epd, ext);
        ItemStack tld = first(p, PcItems.TLD_BADGE.get());
        if (tld != null) {
            RadGear.Tld t = tld.getOrDefault(RadGear.TLD.get(), RadGear.Tld.FRESH);
            tld.set(RadGear.TLD.get(), new RadGear.Tld(t.doseSv() + ext / 3600, t.issuedDay() < 0 ? RadiationService.day(p) : t.issuedDay()));
        }
        for (ItemStack hand : new ItemStack[] {p.getMainHandItem(), p.getOffhandItem()})
            if (hand.is(PcItems.GEIGER_COUNTER.get())) {
                double shown = GeigerCounter.SBM20.readingUSvH(ext * 1e6);
                hand.set(RadGear.GEIGER_READING.get(), shown);
                p.displayClientMessage(RadGearText.msg("geiger", String.format(java.util.Locale.ROOT, "%.2f", shown))
                        .withStyle(shown >= 20 ? ChatFormatting.GOLD : ChatFormatting.GREEN), true);
                break;
            }
    }

    /** Есть ли у игрока в инвентаре предмет {@code item} (ЭПД на входе в зону). */
    public static boolean carries(ServerPlayer p, net.minecraft.world.item.Item item) {
        return first(p, item) != null;
    }

    private static ItemStack first(ServerPlayer p, net.minecraft.world.item.Item item) {
        for (ItemStack s : p.getInventory().items) if (s.is(item)) return s;
        for (ItemStack s : p.getInventory().offhand) if (s.is(item)) return s;
        return null;
    }

    /** Секунда ЭПД: доза, сигнал мощности (пока поле выше уставки) и сигнал дозы (держится до сброса). */
    private static void epd(ServerPlayer p, ItemStack stack, double rateSvH) {
        RadGear.Epd e = stack.getOrDefault(RadGear.EPD.get(), RadGear.Epd.FRESH);
        PersonalDosimeter.Preset preset = e.presetValue();
        double dose = e.doseSv() + rateSvH / 3600;
        PersonalDosimeter.Alarm a = PersonalDosimeter.alarm(preset, dose, rateSvH);
        boolean doseAlarm = e.doseAlarm() || a == PersonalDosimeter.Alarm.DOSE || a == PersonalDosimeter.Alarm.BOTH;
        boolean rateAlarm = a == PersonalDosimeter.Alarm.RATE || a == PersonalDosimeter.Alarm.BOTH;
        stack.set(RadGear.EPD.get(), new RadGear.Epd(dose, rateSvH, e.preset(), doseAlarm));
        if (rateAlarm || doseAlarm) {
            // мощность — частый писк, доза — двойной тон: по звуку понятно, уходить или считать время
            p.level().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.8f, rateAlarm ? 2.0f : 1.2f);
            p.displayClientMessage(RadGearText.msg(rateAlarm ? "epd_rate_alarm" : "epd_dose_alarm", RadGearText.rate(rateSvH),
                    RadGearText.dose(dose)).withStyle(ChatFormatting.RED), true);
        }
    }

    /** Шланг пневмокостюма: пост должен стоять на месте и не дальше длины шланга. */
    private static void hose(ServerPlayer p, ItemStack chest) {
        GlobalPos port = chest.get(RadGear.AIR_HOSE.get());
        if (port == null) return;
        ServerLevel level = p.serverLevel();
        boolean gone = port.dimension() != level.dimension() || (level.isLoaded(port.pos()) && !level.getBlockState(port.pos()).is(PcBlocks.AIR_SUPPLY_PORT.get()));
        if (!gone && RadGear.hoseConnected(p)) return;
        chest.remove(RadGear.AIR_HOSE.get());
        level.playSound(null, p.blockPosition(), SoundEvents.LEASH_KNOT_BREAK, SoundSource.PLAYERS, 1.0f, 0.7f);
        p.displayClientMessage(RadGearText.msg("hose_torn").withStyle(ChatFormatting.RED), false);
    }
}

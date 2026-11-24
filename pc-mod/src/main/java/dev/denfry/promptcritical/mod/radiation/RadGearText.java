package dev.denfry.promptcritical.mod.radiation;

import dev.denfry.promptcritical.mod.environment.DosimeterItem;
import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.sim.radiation.PersonalDosimeter;
import dev.denfry.promptcritical.sim.radiation.RadZone;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Названия, подсказки и сообщения радиационных СИЗ, дозиметрии и санпропускника (04b §4b.7, §4b.8.3, §4b.12), en и ru. */
public final class RadGearText {
    private static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        item("geiger_counter", "Geiger Counter", "Бытовой счётчик Гейгера",
                "Honest at background, lies low in a strong field: the tube saturates and reads less, down to zero",
                "На фоне честный, в сильном поле врёт вниз: трубка насыщается и показывает меньше, вплоть до нуля");
        item("personal_dosimeter_epd", "Electronic Personal Dosimeter", "Электронный индивидуальный дозиметр",
                "Carry it to count your dose and alarm on rate and dose · right-click: reading · sneak + right-click: next preset and reset",
                "Носить с собой: считает дозу и пищит по мощности и дозе · ПКМ — показание · Shift+ПКМ — следующие уставки и сброс");
        item("tld_badge", "TLD Badge", "Термолюминесцентный дозиметр",
                "Shows nothing: carry it, then read it at a TLD reader — that is your official dose",
                "Ничего не показывает: носить с собой и считать на считывателе ТЛД — это официальная доза");
        item("potassium_iodide", "Potassium Iodide Tablets", "Таблетки йодистого калия",
                "130 mg KI: blocks radioiodine uptake by the thyroid for a day. Best taken before the release, still helps within hours after",
                "130 мг KI: на сутки блокирует захват радиоактивного йода щитовидной железой. Лучше до выброса, помогает и в первые часы после");
        item("iodine_cartridge", "Iodine Cartridge", "Йодный патрон",
                "Charcoal cartridge for the full-face respirator: stops gaseous iodine for 8 hours of breathing it",
                "Угольный патрон к полнолицевой маске: держит газообразный йод 8 часов дыхания в нём");
        LANG.put("item.promptcritical.papr", new String[] {"Powered Air-Purifying Respirator Hood (radiological)",
                "Шлем-капюшон с принудительной подачей воздуха (радиационный)"});
        LANG.put("item.promptcritical.papr.apf", new String[] {"Aerosols and iodine ×%s while the blower runs, ×%s on a flat battery. Charge on the lamp room rack",
                "Аэрозоли и йод ×%s, пока работает вентилятор, ×%s с севшей батареей. Заряжается на стеллаже ламповой"});
        item("respirator", "Half-Mask Respirator P3", "Респиратор-полумаска P3",
                "Aerosols ×10. Gaseous iodine passes the particle filter. Fits into a hard hat",
                "Аэрозоли ×10. Газообразный йод через фильтр P3 проходит. Вставляется в каску");
        item("full_face_respirator", "Full-Face Respirator", "Полнолицевая маска",
                "Aerosols ×50; with an iodine cartridge (drop it on the mask) also iodine ×50",
                "Аэрозоли ×50; с йодным патроном (положить на маску) и йод ×50");
        item("pneumosuit_helmet", "Pneumosuit Helmet", "Шлем пневмокостюма", "Part of the air-fed suit", "Часть пневмокостюма");
        item("pneumosuit_jacket", "Pneumosuit Jacket", "Куртка пневмокостюма",
                "Full suit on a hose: breathing ×2000, skin 99 %. Right-click an air supply port to clip the hose (16 m)",
                "Весь костюм на шланге: дыхание ×2000, кожа 99 %. ПКМ по посту подачи воздуха — пристегнуть шланг (16 м)");
        item("pneumosuit_trousers", "Pneumosuit Trousers", "Брюки пневмокостюма", "Part of the air-fed suit", "Часть пневмокостюма");
        item("pneumosuit_boots", "Pneumosuit Boots", "Боты пневмокостюма", "Part of the air-fed suit", "Часть пневмокостюма");
        item("protective_overalls", "Protective Overalls", "Спецодежда",
                "Zone clothing: take it off at the dirty locker and the contamination stays on it",
                "Одежда зоны: снять в «грязном» шкафу — загрязнение уходит вместе с ней");
        item("boot_covers", "Boot Covers", "Бахилы",
                "Take the floor's contamination instead of your shoes (90 %)", "Берут загрязнение пола на себя вместо обуви (90 %)");
        item("lead_apron", "Lead Apron 0.5 mm", "Просвинцованный фартук 0,5 мм",
                "Stops X-rays and soft gamma, almost nothing of Cs-137 or Co-60. Heavy: slows you",
                "Держит рентген и мягкое γ, почти не держит Cs-137 и Co-60. Тяжёлый: замедляет");

        block("instrument_dose_rate", "Gamma Dose Rate Detector", "Блок детектирования мощности дозы γ");
        block("zone_controller", "Radiation Zone Controller", "Контроллер радиационной зоны");
        block("zone_access_door", "Controlled Area Door", "Дверь зоны контролируемого доступа");
        block("clean_locker", "Clean Locker", "Шкаф чистой одежды");
        block("dirty_locker", "Dirty Locker", "Шкаф грязной одежды");
        block("tld_reader", "TLD Reader", "Считыватель ТЛД");
        block("air_supply_port", "Breathing Air Supply Port", "Пост подачи воздуха");

        msg("geiger", "Geiger: %s µSv/h", "Счётчик: %s мкЗв/ч");
        msg("epd", "EPD [%s]: %s · %s", "ЭПД [%s]: %s · %s");
        msg("epd_preset", "EPD preset %s: dose %s, rate %s. Dose reset", "Уставки ЭПД %s: доза %s, мощность %s. Доза сброшена");
        msg("epd_rate_alarm", "EPD RATE ALARM: %s · dose %s", "ЭПД: ТРЕВОГА ПО МОЩНОСТИ: %s · доза %s");
        msg("epd_dose_alarm", "EPD DOSE ALARM: dose %s (rate %s)", "ЭПД: ТРЕВОГА ПО ДОЗЕ: доза %s (мощность %s)");
        msg("preset.routine", "routine", "плановая");
        msg("preset.emergency_100", "emergency 100 mSv", "аварийная 100 мЗв");
        msg("preset.lifesaving_500", "life-saving 500 mSv", "спасение жизни 500 мЗв");
        msg("cartridge_fitted", "Iodine cartridge fitted", "Йодный патрон вставлен");
        msg("cartridge_spent", "Iodine cartridge spent: iodine passes the mask", "Йодный патрон выработан: йод проходит маску");
        msg("cartridge", "Iodine cartridge: %s h of breathing iodine left", "Йодный патрон: осталось %s ч дыхания в йоде");
        msg("no_cartridge", "No iodine cartridge: iodine passes", "Без йодного патрона: йод проходит");
        msg("hose", "Hose clipped to the port at %s", "Шланг пристёгнут к посту у %s");
        msg("no_hose", "No air hose: the suit does not protect breathing", "Шланг не пристёгнут: костюм не защищает дыхание");
        msg("hose_on", "Hose clipped: stay within %s m of the port", "Шланг пристёгнут: не уходите от поста дальше %s м");
        msg("hose_off", "Hose unclipped", "Шланг отстёгнут");
        msg("hose_no_suit", "Put on the pneumosuit jacket to clip the hose", "Чтобы пристегнуть шланг, наденьте куртку пневмокостюма");
        msg("hose_torn", "The air hose tore off: no air in the suit", "Шланг оборвался: воздух в костюм не идёт");
        msg("iodide_taken", "Potassium iodide taken: the thyroid is blocked for a day", "Йодистый калий принят: щитовидная железа заблокирована на сутки");
        msg("iodide_refund", "Potassium iodide taken: %s of the radioiodine dose averted", "Йодистый калий принят: предотвращено %s дозы от радиойода");
        msg("instrument", "Dose rate here: %s", "Мощность дозы здесь: %s");
        msg("zone", "%s · %s cells · %s (design up to %s) · %s detectors", "%s · %s клеток · %s (проект до %s) · датчиков: %s");
        msg("zone_open", "%s · the room is not closed: walls and doors must enclose it (at most %s cells)",
                "%s · помещение не замкнуто: его должны закрывать стены и двери (не больше %s клеток)");
        msg("zone_exceeded", "DOSE RATE OVER THE DESIGN OF THE CLASS", "МОЩНОСТЬ ДОЗЫ ВЫШЕ ПРОЕКТНОЙ ДЛЯ КЛАССА");
        msg("zone.free", "Free access area", "Зона свободного доступа");
        msg("zone.controlled_serviced", "Controlled area, serviced rooms", "ЗКД, обслуживаемые помещения");
        msg("zone.controlled_periodic", "Controlled area, periodically serviced", "ЗКД, периодически обслуживаемые");
        msg("zone.controlled_unserviced", "Controlled area, unserviced boxes (work permit)", "ЗКД, необслуживаемые боксы (по наряду)");
        msg("door.no_dosimeter", "%s: no electronic dosimeter on you", "%s: при себе нет электронного дозиметра");
        msg("door.over_annual_limit", "%s: your annual dose is over the limit", "%s: годовая доза выше предела");
        msg("door.contaminated", "%s: hands or shoes contaminated — back to the shower", "%s: руки или обувь загрязнены — обратно в душ");
        msg("door.let_through", " (let through, violation logged)", " (пропуск с нарушением)");
        msg("locker_swapped", "Changed clothes", "Переоделись");
        msg("locker_dirty_in", "Took off %s zone items · in the locker: %s", "Снято зонных вещей: %s · в шкафу: %s");
        msg("locker_dirty_none", "Nothing to take off (%s) · in the locker: %s", "Снимать нечего (%s) · в шкафу: %s");
        msg("locker_dirty_left", "In the locker: %s", "В шкафу: %s");
        msg("tld", "TLD read: %s. Official dose this year: %s of %s", "ТЛД считан: %s. Официальная доза за год: %s из %s");
        msg("tld_none", "Official dose this year: %s of %s. Hold a TLD badge to read it",
                "Официальная доза за год: %s из %s. Чтобы считать ТЛД, держите его в руке");
    }

    private RadGearText() {
    }

    private static void item(String id, String en, String ru, String tipEn, String tipRu) {
        LANG.put("item.promptcritical." + id, new String[] {en, ru});
        LANG.put("item.promptcritical." + id + (isPpe(id) ? ".hint" : ".tip"), new String[] {tipEn, tipRu});
    }

    /** У СИЗ подсказка — {@code .hint} ({@code PpeItem}), у приборов — {@code .tip}. */
    private static boolean isPpe(String id) {
        return switch (id) {
            case "respirator", "full_face_respirator", "pneumosuit_helmet", "pneumosuit_jacket", "pneumosuit_trousers", "pneumosuit_boots",
                    "protective_overalls", "boot_covers", "lead_apron" -> true;
            default -> false;
        };
    }

    private static void block(String id, String en, String ru) {
        LANG.put("block.promptcritical." + id, new String[] {en, ru});
    }

    private static void msg(String key, String en, String ru) {
        LANG.put("message.promptcritical.radgear." + key, new String[] {en, ru});
    }

    public static Map<String, String[]> lang() {
        return LANG;
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable("message.promptcritical.radgear." + key, args);
    }

    public static String rate(double svH) {
        return DosimeterItem.rate(svH);
    }

    public static String dose(double sv) {
        return DosimeterItem.dose(sv);
    }

    public static Component presetName(PersonalDosimeter.Preset p) {
        return msg("preset." + p.name().toLowerCase(Locale.ROOT));
    }

    public static MutableComponent epdLine(RadGear.Epd e) {
        String r = PersonalDosimeter.overRange(e.rateSvH()) ? ">10 Sv/h" : rate(e.rateSvH());
        return msg("epd", presetName(e.presetValue()), r, dose(e.doseSv()));
    }

    public static Component zoneName(RadZone z) {
        return msg("zone." + z.name().toLowerCase(Locale.ROOT));
    }

    public static Component zoneLine(ZoneControllerBlockEntity z) {
        if (!z.enclosed()) return msg("zone_open", zoneName(z.zone()), ZoneControllerBlockEntity.MAX_CELLS).withStyle(ChatFormatting.GOLD);
        String design = Double.isInfinite(z.zone().designSvH) ? "∞" : rate(z.zone().designSvH);
        MutableComponent line = msg("zone", zoneName(z.zone()), z.volume(), rate(z.meanSvH()), design, z.instruments());
        if (z.exceeded()) return line.append(" · ").append(msg("zone_exceeded")).withStyle(ChatFormatting.RED);
        return line.withStyle(ChatFormatting.AQUA);
    }

    public static MutableComponent doorLine(RadZone.Verdict v, RadZone zone, boolean enforced) {
        MutableComponent m = msg("door." + v.name().toLowerCase(Locale.ROOT), zoneName(zone));
        return enforced ? m : m.append(msg("door.let_through"));
    }

    public static Component officialLine(ServerPlayer p, double readSv, boolean read) {
        double year = RadiationService.officialAnnualSv(p), limit = SanitaryBlocks.limitSv();
        ChatFormatting c = year >= limit ? ChatFormatting.RED : year >= 0.5 * limit ? ChatFormatting.GOLD : ChatFormatting.GREEN;
        return (read ? msg("tld", dose(readSv), dose(year), dose(limit)) : msg("tld_none", dose(year), dose(limit))).withStyle(c);
    }

    /** Строки подсказки полнолицевой маски (йодный патрон) и куртки пневмокостюма (шланг). */
    public static void gearLine(ItemStack stack, List<Component> tooltip) {
        if (stack.has(RadGear.AIR_HOSE.get()) || stack.getItem() instanceof dev.denfry.promptcritical.mod.safety.PpeItem p
                && p.kind() == dev.denfry.promptcritical.mod.safety.PpeItem.Kind.PNEUMOSUIT_JACKET) {
            GlobalPos hose = stack.get(RadGear.AIR_HOSE.get());
            tooltip.add(hose == null ? msg("no_hose").withStyle(ChatFormatting.RED)
                    : msg("hose", hose.pos().toShortString()).withStyle(ChatFormatting.AQUA));
            return;
        }
        double left = RadGear.cartridge(stack);
        // ресурс — в игровых часах дыхания в йоде (игровые сутки = 24 физ. ч, R9)
        tooltip.add(left > 0 ? msg("cartridge", String.format(Locale.ROOT, "%.1f", left / 3600)).withStyle(ChatFormatting.AQUA)
                : msg("no_cartridge").withStyle(ChatFormatting.RED));
    }
}

package dev.denfry.promptcritical.mod.safety;

import dev.denfry.promptcritical.mod.PromptCritical;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Тексты промышленной безопасности 13 (СИЗ, замкнутые пространства, обходы, факел, знаки): ключ → {en, ru} для datagen. */
public final class SafetyText {
    private static final String P = "message." + PromptCritical.MOD_ID + ".safety.";
    public static final Map<String, String[]> LANG = new LinkedHashMap<>();

    static {
        // СИЗ 13 §13.7
        m("insert_added", "%s fitted into the hard hat", "%s вставлен(ы) в каску");
        m("insert_removed", "%s taken out of the hard hat", "%s вынут(ы) из каски");
        m("filter_fitted", "Filter %s fitted", "Фильтр %s установлен");
        m("scba_filled", "Cylinder fitted: %s L of air", "Баллон подключён: %s л воздуха");
        m("scba_whistle", "SCBA whistle: 20 %% of air left — leave the area now", "Свисток аппарата: осталось 20 %% воздуха — немедленно выходите");
        m("scba_empty", "SCBA cylinder empty: you are breathing the air around you", "Баллон аппарата пуст: вы дышите окружающим воздухом");
        m("fire_suit_hot", "Overheating in the fire suit: take a rest out of the heat", "Перегрев в огнезащитном костюме: отдохните вне жары");
        m("lifeline_clipped", "Lifeline clipped to %s (16 m)", "Фал закреплён: %s (16 м)");
        m("lifeline_unclipped", "Lifeline unclipped", "Фал отстёгнут");
        m("fall_arrested", "The lifeline caught your fall", "Фал удержал при падении");
        t("item.promptcritical.hard_hat.hint", "Falling objects ×0.3. One insert: click a gas mask or ear muffs onto it; right-click empty-handed to take it out",
                "Падающие предметы ×0,3. Одна вставка: наложите противогаз или наушники; ПКМ пустой рукой — вынуть");
        t("item.promptcritical.hard_hat.insert", "Insert: %s", "Вставка: %s");
        t("item.promptcritical.gas_mask.hint", "×50 against the gas of its filter. No protection below 17 %% O₂ or against N₂, Ar, CO₂, CH₄",
                "×50 по газу своего фильтра. Не защищает при O₂ ниже 17 %% и от N₂, Ar, CO₂, CH₄");
        t("item.promptcritical.gas_mask.filter", "Filter %s: %s %% used", "Фильтр %s: израсходовано %s %%");
        t("item.promptcritical.gas_mask.no_filter", "No filter: click a gas mask filter onto it", "Без фильтра: наложите фильтр");
        t("item.promptcritical.gas_mask_filter.typed", "Gas Mask Filter %s", "Фильтр противогаза %s");
        t("item.promptcritical.gas_mask_filter.stops", "Stops: %s", "Задерживает: %s");
        t("item.promptcritical.scba.hint", "Isolates completely at any O₂ (×10 000) while air lasts; worn on the chest", "Изолирует полностью при любом O₂ (×10 000), пока есть воздух; носится на груди");
        t("item.promptcritical.scba.air", "Air: %s L, %s min", "Воздух: %s л, %s мин");
        t("item.promptcritical.scba_cylinder.hint", "Click onto an SCBA to fill it with 2000 L", "Наложите на аппарат: 2000 л воздуха");
        t("item.promptcritical.fire_suit.hint", "Radiant heat ×0.1, burning ×0.2; slows you, overheats after 5 min of work",
                "Тепловое излучение ×0,1, горение ×0,2; замедляет, перегрев через 5 мин работы");
        t("item.promptcritical.safety_harness.hint", "With a clipped lifeline in your inventory a fall within 16 m of the anchor does no harm",
                "С закреплённым фалом в инвентаре падение в 16 м от опоры безвредно");
        t("item.promptcritical.lifeline.hint", "Right-click a support or rescue tripod to clip it; sneak + right-click in the air to unclip",
                "ПКМ по опоре или треноге — закрепить; Shift + ПКМ в воздух — отстегнуть");
        t("item.promptcritical.lifeline.anchor", "Clipped at %s", "Закреплён: %s");
        // замкнутые пространства 13 §13.2.6
        m("faint", "You are losing consciousness: the air here does not keep you alive", "Вы теряете сознание: этим воздухом дышать нельзя");
        m("not_confined", "No confined space behind this manway (more than 2048 blocks of air or none)",
                "За люком нет замкнутого пространства (воздуха больше 2048 блоков или нет вовсе)");
        m("hatch_closed", "The hatch is closed: open it on the panel", "Люк закрыт: откройте его на щите");
        m("no_permit", "No active confined space permit on this manway", "Нет действующего наряда на замкнутое пространство по этому люку");
        m("no_attendant", "No attendant at the entry: another player must stay within 4 m outside",
                "Нет наблюдающего у входа: другой игрок должен стоять снаружи не дальше 4 м");
        m("entered", "Inside. Sneak + right-click the manway to climb out", "Вы внутри. Выход — Shift + ПКМ по люку");
        m("entered_unsafe", "Inside an atmosphere unfit to breathe — get out or put on an SCBA",
                "Вы в атмосфере, непригодной для дыхания: выходите или наденьте аппарат");
        m("tripod_no_manway", "No manway with a confined space within 3 blocks of the tripod", "Нет люка с замкнутым пространством в 3 блоках от треноги");
        m("tripod_from_outside", "The winch is worked from outside", "Лебёдкой работают снаружи");
        m("tripod_winching", "Winching %s out: 5 s", "Вытягиваем %s: 5 с");
        m("tripod_nobody", "Nobody inside is in a harness with a lifeline on this tripod", "Внутри нет никого в привязи с фалом на этой треноге");
        m("tripod_rescued", "Pulled out through the manway", "Вытянут через люк");
        m("ventilator_on", "Ventilator running: 0.5 m³/s into an open manway within 3 blocks", "Вентилятор работает: 0,5 м³/с в открытый люк в 3 блоках");
        m("ventilator_off", "Ventilator stopped", "Вентилятор остановлен");
        t("death.attack.promptcritical.asphyxiation", "%1$s suffocated in a confined space", "%1$s задохнулся в замкнутом пространстве");
        t("death.attack.promptcritical.asphyxiation.player", "%1$s suffocated in a confined space while fighting %2$s",
                "%1$s задохнулся в замкнутом пространстве, спасаясь от %2$s");
        // обходы 13 §13.3.1
        m("checkpoint_hint", "Right-click with a patrol reader to stamp the visit; sneak + right-click to add it to the route",
                "ПКМ считывателем обходчика — отметить посещение; Shift + ПКМ — добавить метку в маршрут");
        m("patrol_already", "This checkpoint is already point %s of the route", "Эта метка уже пункт %s маршрута");
        m("patrol_added", "Added to the route as point %s", "Добавлена в маршрут пунктом %s");
        m("patrol_unknown", "This checkpoint is not on the route of the reader", "Этой метки нет в маршруте считывателя");
        m("patrol_out_of_order", "Out of order: the route waits for point %s", "Не по порядку: маршрут ждёт пункт %s");
        m("patrol_complete", "Round complete", "Обход пройден");
        m("patrol_point", "Point %s of %s stamped", "Пункт %s из %s отмечен");
        m("patrol_status_new", "Route of %s points, no round walked yet", "Маршрут из %s пунктов, обходов ещё не было");
        m("patrol_status", "Route of %s points, next %s, %s min left until the round is overdue",
                "Маршрут из %s пунктов, следующий %s, до просрочки обхода %s мин");
        m("patrol_card", "Point card: %s running machines nearby", "Карточка пункта: работающих машин рядом %s");
        m("patrol_ok", "Nothing wrong seen or heard", "Замечаний нет");
        m("patrol.bearing_noise", "Listen: bearing noise (%s) — order a bearing repair", "Послушать: шум подшипника (%s) — нужен ремонт подшипника");
        m("patrol.hum", "Listen: hum and beating (%s) — balance or align", "Послушать: гул и биение (%s) — балансировка или центровка");
        m("patrol.locked_point", "Inspect: locked isolation points (%s)", "Осмотр: запертые точки изоляции (%s)");
        m("patrol.open_permit", "Inspect: open work permit nearby", "Осмотр: рядом открытый наряд-допуск");
        m("patrol.bad_air", "Inspect: confined space unfit to enter (%s)", "Осмотр: замкнутое пространство непригодно для входа (%s)");
        t("item.promptcritical.patrol_reader.hint", "Sneak + right-click checkpoints to build the route; right-click them in order on the round",
                "Shift + ПКМ по меткам — составить маршрут; на обходе — ПКМ по ним по порядку");
        t("item.promptcritical.patrol_reader.route", "Route: %s points, next %s", "Маршрут: %s пунктов, следующий %s");
        // факел 13 §13.4.3
        m("flare_no_fuel", "No fuel gas for the pilots: pipe sales gas into the panel", "Нет топливного газа для горелок: подайте товарный газ трубой в щит");
        m("ko_level", "Condensate %s of %s L", "Конденсат %s из %s л");
        m("seal_level", "Seal water %s of %s mB", "Вода затвора %s из %s мБ");
        t("death.attack.promptcritical.thermal_radiation", "%1$s was scorched by the flare", "%1$s обожжён излучением факела");
        t("death.attack.promptcritical.thermal_radiation.player", "%1$s was scorched by the flare while fighting %2$s",
                "%1$s обожжён излучением факела, спасаясь от %2$s");
        // валоповорот 03a §3a.7.6
        m("turning_no_turbine", "No steam turbine next to the turning gear", "Рядом с валоповоротом нет паровой турбины");
        m("turning_running", "Turbine running · bow %s %% · vibration ×%s", "Турбина в работе · прогиб %s %% · вибрация ×%s");
        m("turning_on", "Turning the rotor at 3 rpm · bow %s %% · vibration ×%s", "Ротор проворачивается 3 об/мин · прогиб %s %% · вибрация ×%s");
        m("turning_hot_idle", "Hot rotor standing still: it bows! Bow %s %% · vibration ×%s — put a Create shaft of 16 rpm or more on the gear",
                "Горячий ротор стоит: он выгибается! Прогиб %s %% · вибрация ×%s — дайте на валоповорот вал Create от 16 об/мин");
        m("turning_cold", "Rotor cold · bow %s %% · vibration ×%s", "Ротор остыл · прогиб %s %% · вибрация ×%s");
        for (String[] c : new String[][] {{"organic_vapour", "organic vapours (A)", "органические пары (A)"},
                {"low_boiling_organic", "low-boiling organics (AX)", "низкокипящие органические (AX)"},
                {"inorganic_gas", "Cl₂, H₂S (B)", "Cl₂, H₂S (B)"}, {"acid_gas", "SO₂, HCl, HF (E)", "SO₂, HCl, HF (E)"},
                {"ammonia", "ammonia (K)", "аммиак (K)"}, {"carbon_monoxide", "carbon monoxide (CO, hopcalite)", "угарный газ (CO, гопкалит)"},
                {"particles", "dust, smoke, aerosols (P3)", "пыль, дым, аэрозоли (P3)"}, {"asphyxiant", "nothing", "ничего"}})
            t("contaminant.promptcritical." + c[0], c[1], c[2]);
    }

    private SafetyText() {
    }

    static void m(String key, String en, String ru) {
        LANG.put(P + key, new String[] {en, ru});
    }

    static void t(String key, String en, String ru) {
        LANG.put(key, new String[] {en, ru});
    }

    public static MutableComponent msg(String key, Object... args) {
        return Component.translatable(P + key, args);
    }
}

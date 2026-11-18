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

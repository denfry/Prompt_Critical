package dev.denfry.promptcritical.mod.nuclear;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.mod.registry.PcItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Щит A0 пульта каскада газовых центрифуг: скорость ротора, U-235 в десублиматоре против безопасной массы,
 * обогащения питания, отбора и отвала, ЕРР в год, ступени и машины; слоты баллонов питания, отбора и отвала.
 * Доли изотопов в каналах — в тысячных долях процента (x·10⁵).
 */
public final class EnrichmentPanels {
    public static final int SPEED = 0, TRAP_PCT = 1, TRAP_G = 2, SAFE_G = 3, XF = 4, XP = 5, XW = 6, SWU_YEAR = 7, KG_DAY_X10 = 8,
            STAGES = 9, MACHINES = 10, POWERED = 11, ALPHA_X1000 = 12, POWER_KW = 13, DESIGN = 14, CIRC = 15, X234_P = 16, FLAGS = 17,
            CHANNELS = 18;
    public static final int F_RUN = 0, F_LOCKED = 1, F_EXCURSION = 2, F_FEW = 3, F_NO_INTERLOCK = 4, F_NO_FEED = 5, F_SUPERCRITICAL = 6;
    public static final int B_RUN = 0, B_DESIGN = 1, B_XP_DOWN = 2, B_XP_UP = 3, B_XW_DOWN = 4, B_XW_UP = 5, B_SPEED_DOWN = 6, B_SPEED_UP = 7,
            B_CIRC_DOWN = 8, B_CIRC_UP = 9, B_INTERLOCK = 10;
    public static final int SLOT_FEED = 0, SLOT_PRODUCT = 1, SLOT_TAILS = 2;

    private static final Tr MS = PanelText.tr("unit.ms", "m/s", "м/с"),
            ROTOR = PanelText.tr("cascade.rotor", "Rotor", "Ротор"),
            TRAP = PanelText.tr("cascade.trap", "Desubl.", "Десубл."),
            RUN = PanelText.tr("cascade.run", "Run", "Пуск/стоп"),
            WORKING = PanelText.tr("cascade.working", "Working", "Работа"),
            LOCKED = PanelText.tr("cascade.locked", "SCR lock", "Блок. СЦР"),
            EXCURSION = PanelText.tr("cascade.excursion", "SCR!", "СЦР!"),
            FEW = PanelText.tr("cascade.few", "Few mach.", "Мало машин"),
            NO_INTERLOCK = PanelText.tr("cascade.no_interlock", "Lock off", "Защ. откл."),
            NO_FEED = PanelText.tr("cascade.no_feed", "No feed", "Нет питания"),
            SUPERCRITICAL = PanelText.tr("cascade.supercritical", "Supercrit.", "Надкрит."),
            DESIGN_BTN = PanelText.tr("cascade.design_btn", "Rotor", "Ротор"),
            DESIGN_TIP = PanelText.tr("cascade.design_tip", "Next rotor design (with the hall stopped)", "Следующая конструкция ротора (на остановленном цехе)"),
            XP_LABEL = PanelText.tr("cascade.xp_label", "Prod.", "Отбор"),
            XW_LABEL = PanelText.tr("cascade.xw_label", "Tails", "Отвал"),
            SPEED_LABEL = PanelText.tr("cascade.speed_label", "Speed", "Скор."),
            CIRC_LABEL = PanelText.tr("cascade.circ_label", "Circ.", "Цирк."),
            XP_DOWN = PanelText.tr("cascade.xp_down", "Product enrichment down", "Обогащение отбора ниже"),
            XP_UP = PanelText.tr("cascade.xp_up", "Product enrichment up", "Обогащение отбора выше"),
            XW_DOWN = PanelText.tr("cascade.xw_down", "Tails assay −0.05 %", "Отвал −0,05 %"),
            XW_UP = PanelText.tr("cascade.xw_up", "Tails assay +0.05 %", "Отвал +0,05 %"),
            SPEED_DOWN = PanelText.tr("cascade.speed_down", "Rotor speed −1 % of burst", "Скорость ротора −1 % от разрывной"),
            SPEED_UP = PanelText.tr("cascade.speed_up", "Rotor speed +1 % of burst: separative power grows as v⁴, so does the risk",
                    "Скорость ротора +1 % от разрывной: разделительная мощность растёт как v⁴, риск тоже"),
            CIRC_DOWN = PanelText.tr("cascade.circ_down", "Countercurrent circulation −10 %", "Противоточная циркуляция −10 %"),
            CIRC_UP = PanelText.tr("cascade.circ_up", "Countercurrent circulation +10 %", "Противоточная циркуляция +10 %"),
            INTERLOCK_BTN = PanelText.tr("cascade.interlock_btn", "SCR lock", "Защита СЦР"),
            INTERLOCK_TIP = PanelText.tr("cascade.interlock_tip", "Criticality interlock on or off: off, the desublimer can be overloaded",
                    "Блокировка СЦР вкл/откл: без неё десублиматор можно перегрузить"),
            FEED_SLOT = PanelText.tr("cascade.feed_slot", "Feed: UF₆ cylinder", "Питание: баллон UF₆"),
            PRODUCT_SLOT = PanelText.tr("cascade.product_slot", "Product: 30B up to 5 %, 5A any", "Отбор: 30B до 5 %, 5A — любое"),
            TAILS_SLOT = PanelText.tr("cascade.tails_slot", "Tails: empty 48Y", "Отвал: пустой 48Y"),
            SPEED_TIP = PanelText.tr("cascade.speed_tip", "Rotor %s m/s · %s · α = %s · %s of %s machines powered",
                    "Ротор %s м/с · %s · α = %s · питание у %s из %s машин"),
            TRAP_TIP = PanelText.tr("cascade.trap_tip", "U-235 in the desublimer %s g of a safe %s g", "U-235 в десублиматоре %s г из безопасных %s г"),
            STATUS = PanelText.tr("cascade.status", "Feed %s %% → product %s %% (U-234 %s %%), tails %s %% · %s SWU/yr · %s kg U/day · %s stages · %s kW",
                    "Питание %s %% → отбор %s %% (U-234 %s %%), отвал %s %% · %s ЕРР/год · %s кг U/сут · ступеней %s · %s кВт");
    private static final Tr[] DESIGNS = {
            PanelText.tr("cascade.design.0", "aluminium 0.6 m", "алюминий 0,6 м"),
            PanelText.tr("cascade.design.1", "maraging steel 1.5 m", "мартенситностареющая сталь 1,5 м"),
            PanelText.tr("cascade.design.2", "carbon fibre 1 m", "углепластик 1 м"),
    };

    public static final PanelLayout CASCADE = PanelLayout.builder("centrifuge_cascade", 248, 150, CHANNELS)
            .dial(8, 24, false, SPEED, 1, 800, Double.NaN, 0, MS.get(), ROTOR.get(),
                    v -> SPEED_TIP.get(v.raw(SPEED), DESIGNS[Math.floorMod(v.raw(DESIGN), DESIGNS.length)].get(), v.num(ALPHA_X1000, 1000, 3),
                            v.raw(POWERED), v.raw(MACHINES)))
            .bar(64, 30, 60, TRAP_PCT, 10, 100, 0xFFD4E04A, TRAP.get(), v -> TRAP_TIP.get(v.raw(TRAP_G), v.raw(SAFE_G)))
            .lamp(132, 22, FLAGS, F_RUN, LampColor.GREEN, WORKING.get())
            .lamp(132, 36, FLAGS, F_LOCKED, LampColor.AMBER, LOCKED.get())
            .lamp(132, 50, FLAGS, F_EXCURSION, LampColor.RED, EXCURSION.get())
            .lamp(132, 64, FLAGS, F_NO_INTERLOCK, LampColor.RED, NO_INTERLOCK.get())
            .lamp(64, 50, FLAGS, F_FEW, LampColor.AMBER, FEW.get())
            .lamp(64, 64, FLAGS, F_NO_FEED, LampColor.AMBER, NO_FEED.get())
            .lamp(132, 78, FLAGS, F_SUPERCRITICAL, LampColor.AMBER, SUPERCRITICAL.get())
            .slot(208, 24, SLOT_FEED, s -> s.getItem() instanceof Uf6CylinderItem, FEED_SLOT.get())
            .slot(208, 44, SLOT_PRODUCT, EnrichmentPanels::productCylinder, PRODUCT_SLOT.get())
            .slot(208, 64, SLOT_TAILS, s -> s.is(PcItems.UF6_CYLINDER_48Y.get()), TAILS_SLOT.get())
            .button(8, 96, 44, B_RUN, RUN.get(), null)
            .button(56, 96, 44, B_DESIGN, DESIGN_BTN.get(), DESIGN_TIP.get())
            .caption(104, 86, XP_LABEL.get())
            .button(104, 96, 20, B_XP_DOWN, Component.literal("−"), XP_DOWN.get())
            .button(126, 96, 20, B_XP_UP, Component.literal("+"), XP_UP.get())
            .caption(152, 86, XW_LABEL.get())
            .button(152, 96, 20, B_XW_DOWN, Component.literal("−"), XW_DOWN.get())
            .button(174, 96, 20, B_XW_UP, Component.literal("+"), XW_UP.get())
            .caption(200, 86, SPEED_LABEL.get())
            .button(200, 96, 20, B_SPEED_DOWN, Component.literal("−"), SPEED_DOWN.get())
            .button(222, 96, 20, B_SPEED_UP, Component.literal("+"), SPEED_UP.get())
            .caption(104, 112, CIRC_LABEL.get())
            .button(104, 120, 20, B_CIRC_DOWN, Component.literal("−"), CIRC_DOWN.get())
            .button(126, 120, 20, B_CIRC_UP, Component.literal("+"), CIRC_UP.get())
            .button(8, 120, 92, B_INTERLOCK, INTERLOCK_BTN.get(), INTERLOCK_TIP.get())
            .readout(8, 138, 232, EnrichmentPanels::status, null)
            .build();

    private EnrichmentPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(CASCADE);
    }

    /** Баллоны, которые пульт принимает в слот отбора и отвала. */
    static boolean productCylinder(net.minecraft.world.item.ItemStack s) {
        return s.is(PcItems.UF6_CYLINDER_30B.get()) || s.is(PcItems.UF6_CYLINDER_5A.get());
    }

    private static String pct(PanelValues v, int ch) {
        return v.num(ch, 1000, 3);
    }

    private static Component status(PanelValues v) {
        Component c = STATUS.get(pct(v, XF), pct(v, XP), pct(v, X234_P), pct(v, XW), v.raw(SWU_YEAR), v.num(KG_DAY_X10, 10, 1),
                v.raw(STAGES), v.raw(POWER_KW));
        return v.bit(FLAGS, F_EXCURSION) || v.bit(FLAGS, F_NO_INTERLOCK) ? c.copy().withStyle(ChatFormatting.RED)
                : v.bit(FLAGS, F_LOCKED) || v.bit(FLAGS, F_FEW) ? c.copy().withStyle(ChatFormatting.GOLD) : c;
    }
}

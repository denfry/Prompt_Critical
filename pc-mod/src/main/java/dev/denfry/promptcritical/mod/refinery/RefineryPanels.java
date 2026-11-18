package dev.denfry.promptcritical.mod.refinery;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.registry.PcItems;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Щиты A0 гидроочистки, установки Клауса и замедленного коксования: температура, четыре величины, бак продукта, флаги. */
public final class RefineryPanels {
    public static final int T_C = 0, A = 1, B = 2, C = 3, D = 4, TANK = 5, FLAGS = 6, CHANNELS = 7;
    public static final int F_ON = 0, F_RUN = 1, F_WARN = 2, F_AUX = 3;
    public static final int B_RUN = 0, B_T_DOWN = 1, B_T_UP = 2, B_AIR_DOWN = 3, B_AIR_UP = 4;

    private static final Tr RUN = PanelText.tr("refinery.run", "Run", "Пуск/стоп"), ON = PanelText.tr("refinery.on", "On", "Вкл."),
            WORKING = PanelText.tr("refinery.working", "Working", "Работа"),
            T_DOWN = PanelText.tr("refinery.t_down", "−5 °C", "−5 °C"), T_UP = PanelText.tr("refinery.t_up", "+5 °C", "+5 °C"),
            BED = PanelText.tr("refinery.bed", "Bed", "Слой"), PPM = PanelText.tr("refinery.ppm", "ppm", "ppm"),
            SULFUR = PanelText.tr("refinery.sulfur", "S out", "S в ДТ"), PCT = PanelText.tr("refinery.pct", "%", "%"),
            ACTIVITY = PanelText.tr("refinery.activity", "Catalyst", "Катализатор"),
            PRODUCT = PanelText.tr("refinery.product", "Treated diesel", "Очищенное ДТ"),
            CRACKING = PanelText.tr("refinery.cracking", "Bed above 420 °C: hydrocracking", "Слой выше 420 °C: гидрокрекинг"),
            SOUR = PanelText.tr("refinery.sour", "H₂S holdup full: no Claus unit", "Сероводород некуда сдать: нет Клауса"),
            CATALYST = PanelText.tr("refinery.catalyst", "CoMo catalyst", "Катализатор CoMo"),
            HDT_STATUS = PanelText.tr("refinery.hdt_status", "Diesel %s l/h · H₂S in recycle %s %% · activity %s %%",
                    "ДТ %s л/ч · H₂S в рецикле %s %% · активность %s %%"),
            RECOVERY = PanelText.tr("refinery.recovery", "Recovery", "Извлечение"),
            AIR_DOWN = PanelText.tr("refinery.air_down", "Air −", "Воздух −"), AIR_UP = PanelText.tr("refinery.air_up", "Air +", "Воздух +"),
            DEW = PanelText.tr("refinery.dew", "Bed below sulphur dew point + 10 K", "Слой ниже точки росы серы + 10 К"),
            RATIO = PanelText.tr("refinery.ratio", "H₂S : SO₂ off 2 : 1", "H₂S : SO₂ не 2 : 1"),
            SULFUR_ITEM = PanelText.tr("refinery.sulfur_item", "Sulphur", "Сера"),
            COIL = PanelText.tr("refinery.coil", "Coil outlet", "Выход печи"),
            COKE_YIELD = PanelText.tr("refinery.coke_yield", "Coke yield", "Выход кокса"),
            DRUM_FILL = PanelText.tr("refinery.drum_fill", "Drum fill", "Заполнение камеры"),
            GAS_OIL = PanelText.tr("refinery.gas_oil", "Coker gas oil", "Газойль коксования"),
            TUBE_COKING = PanelText.tr("refinery.tube_coking", "Coil above 505 °C: tubes coking", "Печь выше 505 °C: коксуются трубы"),
            SOFT_COKE = PanelText.tr("refinery.soft_coke", "Soft coke or H₂S holdup full", "Мягкий кокс или некуда сдать H₂S"),
            PET_COKE = PanelText.tr("refinery.pet_coke", "Petroleum coke", "Нефтяной кокс"),
            COKER_STATUS = PanelText.tr("refinery.coker_status", "Fuel oil %s l/h · second drum: %s",
                    "Мазут %s л/ч · вторая камера: %s"),
            CLAUS_STATUS = PanelText.tr("refinery.claus_status", "Sulphur %s kg/h · SO₂ to stack %s kg/h · air error %s %%",
                    "Сера %s кг/ч · SO₂ в трубу %s кг/ч · ошибка воздуха %s %%");

    public static final PanelLayout HYDROTREATER = PanelLayout.builder("hydrotreater", 232, 140, CHANNELS)
            .dial(8, 24, false, T_C, 1, 450, 420, 0, PanelText.CELSIUS.get(), BED.get(), null)
            .dial(64, 24, false, A, 1, 500, 50, 0, PPM.get(), SULFUR.get(), null)
            .dial(120, 24, false, B, 10, 100, Double.NaN, 0, PCT.get(), ACTIVITY.get(), null)
            .glass(176, 24, 56, TANK, 0xFFE8D890, Double.NaN, Double.NaN, PRODUCT.get(), null)
            .lamp(200, 22, FLAGS, F_ON, LampColor.GREEN, ON.get()).lamp(200, 36, FLAGS, F_RUN, LampColor.GREEN, WORKING.get())
            .lamp(200, 50, FLAGS, F_WARN, LampColor.RED, CRACKING.get()).lamp(200, 64, FLAGS, F_AUX, LampColor.AMBER, SOUR.get())
            .button(8, 92, 44, B_RUN, RUN.get(), null)
            .button(56, 92, 20, B_T_DOWN, Component.literal("−"), T_DOWN.get()).button(78, 92, 20, B_T_UP, Component.literal("+"), T_UP.get())
            .slot(150, 92, 0, s -> s.is(PcItems.CATALYST_HYDROTREATING.get()), CATALYST.get())
            .readout(8, 118, 216, v -> HDT_STATUS.get(v.raw(D), v.num(C, 100, 2), v.num(B, 10, 1)), null).build();

    public static final PanelLayout CLAUS = PanelLayout.builder("claus_unit", 232, 140, CHANNELS)
            .dial(8, 24, false, T_C, 1, 350, Double.NaN, 0, PanelText.CELSIUS.get(), BED.get(), null)
            .dial(64, 24, false, A, 10, 100, Double.NaN, 1, PCT.get(), RECOVERY.get(), null)
            .lamp(200, 22, FLAGS, F_ON, LampColor.GREEN, ON.get()).lamp(200, 36, FLAGS, F_RUN, LampColor.GREEN, WORKING.get())
            .lamp(200, 50, FLAGS, F_WARN, LampColor.AMBER, DEW.get()).lamp(200, 64, FLAGS, F_AUX, LampColor.AMBER, RATIO.get())
            .button(8, 92, 44, B_RUN, RUN.get(), null)
            .button(56, 92, 20, B_T_DOWN, Component.literal("−"), T_DOWN.get()).button(78, 92, 20, B_T_UP, Component.literal("+"), T_UP.get())
            .button(102, 92, 44, B_AIR_DOWN, AIR_DOWN.get(), null).button(150, 92, 44, B_AIR_UP, AIR_UP.get(), null)
            .output(120, 24, 0, SULFUR_ITEM.get()).output(138, 24, 1, SULFUR_ITEM.get()).output(120, 42, 2, SULFUR_ITEM.get())
            .output(138, 42, 3, SULFUR_ITEM.get())
            .readout(8, 118, 216, v -> CLAUS_STATUS.get(v.raw(B), v.num(C, 10, 1), v.raw(D)), null).build();

    private static final Tr[] PHASES = {PanelText.tr("refinery.phase.fill", "standby", "резерв"),
            PanelText.tr("refinery.phase.steam", "steaming", "пропарка"), PanelText.tr("refinery.phase.quench", "water quench", "охлаждение водой"),
            PanelText.tr("refinery.phase.decoke", "hydraulic decoking", "гидрорезка")};

    public static final PanelLayout COKER = PanelLayout.builder("delayed_coker", 232, 140, CHANNELS)
            .dial(8, 24, false, T_C, 1, 550, 505, 0, PanelText.CELSIUS.get(), COIL.get(), null)
            .dial(64, 24, false, A, 10, 50, Double.NaN, 1, PCT.get(), COKE_YIELD.get(), null)
            .dial(120, 24, false, B, 1, 100, Double.NaN, 0, PCT.get(), DRUM_FILL.get(), null)
            .glass(176, 24, 56, TANK, 0xFF5A4A30, Double.NaN, Double.NaN, GAS_OIL.get(), null)
            .lamp(200, 22, FLAGS, F_ON, LampColor.GREEN, ON.get()).lamp(200, 36, FLAGS, F_RUN, LampColor.GREEN, WORKING.get())
            .lamp(200, 50, FLAGS, F_WARN, LampColor.RED, TUBE_COKING.get()).lamp(200, 64, FLAGS, F_AUX, LampColor.AMBER, SOFT_COKE.get())
            .button(8, 92, 44, B_RUN, RUN.get(), null)
            .button(56, 92, 20, B_T_DOWN, Component.literal("−"), T_DOWN.get()).button(78, 92, 20, B_T_UP, Component.literal("+"), T_UP.get())
            .output(104, 92, 0, PET_COKE.get()).output(122, 92, 1, PET_COKE.get()).output(140, 92, 2, PET_COKE.get())
            .output(158, 92, 3, PET_COKE.get())
            .readout(8, 118, 216, v -> COKER_STATUS.get(v.raw(D), PHASES[Math.clamp(v.raw(C), 0, PHASES.length - 1)].get()), null).build();

    private RefineryPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(HYDROTREATER, CLAUS, COKER);
    }
}

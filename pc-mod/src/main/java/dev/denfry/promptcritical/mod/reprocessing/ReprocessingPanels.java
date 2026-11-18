package dev.denfry.promptcritical.mod.reprocessing;

import dev.denfry.promptcritical.mod.environment.ActivityData;
import dev.denfry.promptcritical.mod.environment.DosimeterItem;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

/**
 * Щиты A0 радиохимии: каньон PUREX (кислотность питания, восстановитель, буфер Pu с красной чертой безопасной массы,
 * лампы аппаратов линии, отсутствие иодного фильтра) и хранилище стекла.
 */
public final class ReprocessingPanels {
    public static final int ACID_X10 = 0, RED_X10 = 1, PU_TANK_G = 2, PU_SAFE_G = 3, DF_LOG10 = 4, U_REC = 5, PU_REC = 6, GLASS = 7,
            PUBLIC_NSV_H = 8, FLAGS = 9, CHANNELS = 10;
    public static final int F_ON = 0, F_RUN = 1, F_HEAD = 2, F_EXTRACT = 3, F_OXALATE = 4, F_DENITR = 5, F_MELTER = 6, F_PU_SAFE = 7,
            F_NO_FILTER = 8;
    public static final int B_RUN = 0, B_ACID_DOWN = 1, B_ACID_UP = 2, B_RED_DOWN = 3, B_RED_UP = 4;
    public static final int V_HEAT_W = 0, V_ACT_TBQ = 1, V_FLAGS = 2, V_CHANNELS = 3;
    public static final int VF_HOT = 0;

    private static final Tr ACID = PanelText.tr("purex.acid", "HNO₃", "HNO₃"),
            RED = PanelText.tr("purex.red", "Reductant", "Восстан."),
            PU_TANK = PanelText.tr("purex.pu_tank", "Pu buffer", "Буфер Pu"),
            M = PanelText.tr("unit.molar", "M", "М"), G = PanelText.tr("unit.g", "g", "г"),
            RUN = PanelText.tr("purex.run", "Run", "Пуск/стоп"),
            WORKING = PanelText.tr("purex.working", "Working", "Работа"),
            HEAD = PanelText.tr("purex.head", "Head end", "Растворение"),
            EXTRACT = PanelText.tr("purex.extract", "Extraction", "Экстракция"),
            OXALATE = PanelText.tr("purex.oxalate", "Pu oxalate", "Оксалат Pu"),
            DENITR = PanelText.tr("purex.denitr", "UO₃", "UO₃"),
            MELTER = PanelText.tr("purex.melter", "Glass", "Стекло"),
            PU_SAFE = PanelText.tr("purex.pu_safe", "Pu > safe", "Pu > безоп."),
            NO_FILTER = PanelText.tr("purex.no_filter", "No I filter", "Нет фильтра I"),
            ACID_DOWN = PanelText.tr("purex.acid_down", "Feed acidity −0.5 M", "Кислотность питания −0,5 М"),
            ACID_UP = PanelText.tr("purex.acid_up", "Feed acidity +0.5 M", "Кислотность питания +0,5 М"),
            RED_DOWN = PanelText.tr("purex.red_down", "Reductant −0.25 of Pu", "Восстановитель −0,25 от Pu"),
            RED_UP = PanelText.tr("purex.red_up", "Reductant +0.25 of Pu", "Восстановитель +0,25 от Pu"),
            FUEL = PanelText.tr("purex.fuel", "Irradiated fuel", "Облучённое топливо"),
            CHARCOAL = PanelText.tr("purex.charcoal", "Charcoal for the iodine filter", "Уголь для иодного фильтра"),
            PRODUCT = PanelText.tr("purex.product", "UO₃, PuO₂, glass canisters", "UO₃, PuO₂, стеклоблоки"),
            STATUS = PanelText.tr("purex.status", "U %s %% · Pu %s %% recovered · DF 10^%s · HLW glass %s kg · public at 500 m %s",
                    "Извлечено U %s %% · Pu %s %% · очистка 10^%s · стекло ВАО %s кг · население на 500 м %s"),
            HEAT = PanelText.tr("vault.heat", "Decay heat %s kW · activity %s TBq", "Тепловыделение %s кВт · активность %s ТБк"),
            HOT = PanelText.tr("vault.hot", "Ventilation", "Вентиляция"),
            CANISTER = PanelText.tr("vault.canister", "Glass canister or active item", "Стеклоблок или активный предмет");

    public static final PanelLayout PUREX;
    public static final PanelLayout VAULT;

    static {
        PanelLayout.Builder b = PanelLayout.builder("purex_cell", 248, 150, CHANNELS)
                .dial(8, 24, false, ACID_X10, 10, 6, Double.NaN, 1, M.get(), ACID.get(), null)
                .dial(56, 24, false, RED_X10, 10, 3, Double.NaN, 2, Component.literal("×"), RED.get(), null)
                .dial(104, 24, false, PU_TANK_G, 1, 600, 230, 0, G.get(), PU_TANK.get(), null)
                .lamp(156, 20, FLAGS, F_RUN, LampColor.GREEN, WORKING.get())
                .lamp(156, 32, FLAGS, F_HEAD, LampColor.GREEN, HEAD.get())
                .lamp(156, 44, FLAGS, F_EXTRACT, LampColor.GREEN, EXTRACT.get())
                .lamp(156, 56, FLAGS, F_OXALATE, LampColor.GREEN, OXALATE.get())
                .lamp(204, 20, FLAGS, F_DENITR, LampColor.GREEN, DENITR.get())
                .lamp(204, 32, FLAGS, F_MELTER, LampColor.GREEN, MELTER.get())
                .lamp(204, 44, FLAGS, F_PU_SAFE, LampColor.RED, PU_SAFE.get())
                .lamp(204, 56, FLAGS, F_NO_FILTER, LampColor.AMBER, NO_FILTER.get())
                .button(8, 80, 44, B_RUN, RUN.get(), null)
                .button(56, 80, 20, B_ACID_DOWN, Component.literal("−"), ACID_DOWN.get())
                .button(78, 80, 20, B_ACID_UP, Component.literal("+"), ACID_UP.get())
                .button(104, 80, 20, B_RED_DOWN, Component.literal("−"), RED_DOWN.get())
                .button(126, 80, 20, B_RED_UP, Component.literal("+"), RED_UP.get());
        for (int i = 0; i < PurexCellBlockEntity.IN_SLOTS; i++) b.slot(8 + 18 * i, 102, i, PurexCellBlockEntity::spentFuel, FUEL.get());
        b.slot(86, 102, PurexCellBlockEntity.CHARCOAL_SLOT, s -> s.is(Items.CHARCOAL), CHARCOAL.get());
        for (int i = 0; i < PurexCellBlockEntity.OUT_SLOTS; i++) b.output(120 + 18 * i, 102, PurexCellBlockEntity.IN_SLOTS + 1 + i, PRODUCT.get());
        PUREX = b.readout(8, 136, 232, ReprocessingPanels::purex, null).build();

        PanelLayout.Builder v = PanelLayout.builder("hlw_vault", 200, 100, V_CHANNELS)
                .lamp(8, 22, V_FLAGS, VF_HOT, LampColor.RED, HOT.get());
        for (int i = 0; i < HlwVaultBlockEntity.SLOTS; i++)
            v.slot(80 + 18 * (i % 6), 22 + 18 * (i / 6), i, s -> s.has(ActivityData.ACTIVITY.get()), CANISTER.get());
        VAULT = v.readout(8, 86, 184, x -> HEAT.get(x.num(V_HEAT_W, 1000, 1), x.raw(V_ACT_TBQ)), null).build();
    }

    private ReprocessingPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(PUREX, VAULT);
    }

    private static Component purex(PanelValues v) {
        return STATUS.get(v.num(U_REC, 100, 2), v.num(PU_REC, 100, 2), v.num(DF_LOG10, 10, 1), v.raw(GLASS),
                DosimeterItem.rate(v.raw(PUBLIC_NSV_H) * 1e-9));
    }
}

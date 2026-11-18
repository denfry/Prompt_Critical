package dev.denfry.promptcritical.mod.heavywater;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.sim.heavywater.GirdlerSulfidePlant;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Щиты тяжёлой воды: сероводородный завод (забор озёрной воды, температура горячей колонны, извлечение дейтерия,
 * бак продукта 15 %) и вакуумная колонна доводки (куб, его чистота, извлечение, бак реакторной D₂O). Каналы — целые.
 */
public final class HeavyWaterPanels {
    // GS-завод
    public static final int G_INTAKE_PERMIL = 0, G_HOT_C = 1, G_RECOVERY_X100 = 2, G_TANK = 3, G_D2O_KG = 4, G_LAKE = 5, G_FLAGS = 6,
            G_CHANNELS = 7;
    public static final int GF_RUN = 0, GF_NO_LAKE = 1, GF_FULL = 2;
    public static final int GB_RUN = 0, GB_HOT_DOWN = 1, GB_HOT_UP = 2;
    // колонна доводки
    public static final int C_STILL = 0, C_PURITY_X100 = 1, C_RECOVERY_X100 = 2, C_TANK = 3, C_MADE_L = 4, C_FLAGS = 5, C_STILL_L = 6,
            C_CHANNELS = 7;
    public static final int CF_RUN = 0, CF_TOO_LEAN = 1, CF_FULL = 2;
    public static final int CB_RUN = 0;

    private static final Tr PCT = PanelText.PERCENT, CELSIUS = PanelText.CELSIUS,
            INTAKE = PanelText.tr("gs.intake", "Lake intake", "Забор воды"),
            HOT = PanelText.tr("gs.hot", "Hot tower", "Горячая"),
            PRODUCT = PanelText.tr("gs.product", "15 % D", "15 % D"),
            RUN = PanelText.tr("gs.run", "Run", "Пуск/стоп"),
            RUNNING = PanelText.tr("gs.running", "Exchange", "Обмен"),
            NO_LAKE = PanelText.tr("gs.no_lake", "No lake", "Нет озера"),
            FULL = PanelText.tr("gs.full", "Tank full", "Бак полон"),
            HOT_DOWN = PanelText.tr("gs.hot_down", "Hot tower −5 °C", "Горячая колонна −5 °C"),
            HOT_UP = PanelText.tr("gs.hot_up", "Hot tower +5 °C", "Горячая колонна +5 °C"),
            INTAKE_TIP = PanelText.tr("gs.intake_tip",
                    "%s water blocks of lake around the base out of %s for the full 1 t/s feed. The depleted water goes back to the lake",
                    "%s блоков озёрной воды вокруг основания из %s для полного питания 1 т/с. Обеднённая вода уходит обратно в озеро"),
            HOT_TIP = PanelText.tr("gs.hot_tip",
                    "Hot tower %s °C, cold 30 °C · the hotter, the weaker H₂S holds deuterium back and the more is extracted (up to 140 °C)",
                    "Горячая колонна %s °C, холодная 30 °C · чем горячее, тем слабее H₂S удерживает дейтерий и тем больше извлечение (до 140 °C)"),
            GS_STATUS = PanelText.tr("gs.status", "Deuterium extracted %s %% · made %s kg of D₂O as 15 %% water",
                    "Извлечено дейтерия %s %% · выработано %s кг D₂O в воде 15 %%"),
            GS_IDLE = PanelText.tr("gs.idle", "Stopped: the H₂S charge circulates idle", "Остановлен: сероводород циркулирует вхолостую");

    private static final Tr STILL = PanelText.tr("d2o.still", "Still", "Куб"),
            REACTOR_GRADE = PanelText.tr("d2o.reactor_grade", "99.75 %", "99,75 %"),
            COL_RUN = PanelText.tr("d2o.run", "Run", "Пуск/стоп"),
            COL_RUNNING = PanelText.tr("d2o.running", "Boil-up", "Кипение"),
            TOO_LEAN = PanelText.tr("d2o.too_lean", "Too lean", "Бедное"),
            STILL_TIP = PanelText.tr("d2o.still_tip", "%s L in the still at %s %% D",
                    "%s л в кубе, %s %% D"),
            COL_STATUS = PanelText.tr("d2o.status", "Still %s %% D · recovery %s %% · made %s L of reactor-grade D₂O",
                    "Куб %s %% D · извлечение %s %% · выработано %s л реакторной D₂O"),
            COL_LEAN = PanelText.tr("d2o.lean", "Feed too lean for 400 plates at α 1.05: send it through the GS plant first",
                    "Питание слишком бедное для 400 тарелок при α 1,05: сначала через сероводородный завод");

    public static final PanelLayout GS = PanelLayout.builder("gs_exchange_tower", 232, 124, G_CHANNELS)
            .dial(8, 24, false, G_INTAKE_PERMIL, 10, 100, Double.NaN, 0, PCT.get(), INTAKE.get(),
                    v -> INTAKE_TIP.get(v.raw(G_LAKE), GsExchangeTowerBlockEntity.LAKE_BLOCKS))
            .dial(64, 24, false, G_HOT_C, 1, 150, GirdlerSulfidePlant.HOT_MAX_K - 273.15, 0, CELSIUS.get(), HOT.get(),
                    v -> HOT_TIP.get(v.raw(G_HOT_C)))
            .glass(124, 24, 60, G_TANK, 0xFF9FC6E8, Double.NaN, Double.NaN, PRODUCT.get(), null)
            .lamp(160, 22, G_FLAGS, GF_RUN, LampColor.GREEN, RUNNING.get())
            .lamp(160, 36, G_FLAGS, GF_NO_LAKE, LampColor.RED, NO_LAKE.get())
            .lamp(160, 50, G_FLAGS, GF_FULL, LampColor.AMBER, FULL.get())
            .button(8, 88, 44, GB_RUN, RUN.get(), null)
            .button(56, 88, 20, GB_HOT_DOWN, Component.literal("−"), HOT_DOWN.get())
            .button(78, 88, 20, GB_HOT_UP, Component.literal("+"), HOT_UP.get())
            .readout(8, 108, 216, HeavyWaterPanels::gs, null)
            .noInventory().build();

    public static final PanelLayout COLUMN = PanelLayout.builder("heavy_water_column", 200, 108, C_CHANNELS)
            .glass(8, 24, 60, C_STILL, 0xFF7FA8C8, Double.NaN, Double.NaN, STILL.get(),
                    v -> STILL_TIP.get(v.raw(C_STILL_L), v.num(C_PURITY_X100, 100, 2)))
            .glass(40, 24, 60, C_TANK, 0xFF9FC6E8, Double.NaN, Double.NaN, REACTOR_GRADE.get(), null)
            .lamp(80, 22, C_FLAGS, CF_RUN, LampColor.GREEN, COL_RUNNING.get())
            .lamp(80, 36, C_FLAGS, CF_TOO_LEAN, LampColor.RED, TOO_LEAN.get())
            .lamp(80, 50, C_FLAGS, CF_FULL, LampColor.AMBER, FULL.get())
            .button(8, 88, 44, CB_RUN, COL_RUN.get(), null)
            .readout(8, 100, 184, HeavyWaterPanels::column, null)
            .noInventory().build();

    private HeavyWaterPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(GS, COLUMN);
    }

    private static Component gs(PanelValues v) {
        if (!v.bit(G_FLAGS, GF_RUN)) return GS_IDLE.get().withStyle(ChatFormatting.GRAY);
        Component c = GS_STATUS.get(v.num(G_RECOVERY_X100, 100, 1), v.raw(G_D2O_KG));
        return v.bit(G_FLAGS, GF_NO_LAKE) ? c.copy().withStyle(ChatFormatting.GOLD) : c;
    }

    private static Component column(PanelValues v) {
        if (v.bit(C_FLAGS, CF_TOO_LEAN)) return COL_LEAN.get().withStyle(ChatFormatting.GOLD);
        return COL_STATUS.get(v.num(C_PURITY_X100, 100, 2), v.num(C_RECOVERY_X100, 100, 1), v.raw(C_MADE_L));
    }
}

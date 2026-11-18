package dev.denfry.promptcritical.mod.waste;

import dev.denfry.promptcritical.mod.environment.ActivityData;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.mod.registry.PcItems;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/**
 * Щиты обращения с РАО: выпарная установка с отверждением, суперпресс, пункты захоронения, очистка теплоносителя.
 */
public final class WastePanels {
    public static final int E_SUMP_L = 0, E_SALT_GL = 1, E_POOL = 2, E_COND = 3, E_COND_MBQ_L = 4, E_CAT = 5, E_DRUMS = 6, E_FLAGS = 7,
            E_CHANNELS = 8;
    public static final int EF_ON = 0, EF_RUN = 1, EF_READY = 2, EF_CEMENT = 3, EF_BITUMEN = 4, EF_MELTER = 5, EF_HLW = 6;
    public static final int C_RAW_L = 0, C_PUCKS = 1, C_PUCKS_L = 2, C_VRF = 3, C_PRESSURE = 4, C_FLAGS = 5, C_CHANNELS = 6;
    public static final int CF_ON = 0, CF_RUN = 1, CF_NO_DRUM = 2;
    public static final int R_PACKAGES = 0, R_ACT_GBQ = 1, R_HEAT_W = 2, R_DOSE_LOG = 3, R_YEARS_LOG = 4, R_NUCLIDE = 5, R_VERDICT = 6,
            R_FLAGS = 7, R_CHANNELS = 8;
    public static final int RF_RELEASE = 0, RF_OVER = 1, RF_REJECT = 2;
    public static final int W_MW = 0, W_RESIN_GBQ = 1, W_FILTER_GBQ = 2, W_RESIN_USE = 3, W_FILTER_USE = 4, W_LRW = 5, W_FLAGS = 6,
            W_CHANNELS = 7;
    public static final int WF_LINK = 0, WF_RESIN = 1, WF_FILTER = 2;
    public static final int B_RUN = 0, B_CLOSE = 1, B_RESIN = 2, B_FILTER = 3;

    private static final Tr RUN = PanelText.tr("waste.run", "Run", "Пуск/стоп"),
            WORKING = PanelText.tr("waste.working", "Working", "Работа"),
            SUMP = PanelText.tr("waste.sump", "Sump", "Куб"), SALT = PanelText.tr("waste.salt", "Salts", "Соли"),
            L = PanelText.tr("unit.l", "l", "л"), GL = PanelText.tr("unit.gl", "g/l", "г/л"),
            POOL = PanelText.tr("waste.pool", "LRW tank", "Сборник ЖРО"), COND = PanelText.tr("waste.cond", "Condensate", "Конденсат"),
            READY = PanelText.tr("waste.ready", "Concentrate", "Куб упарен"),
            CEMENT = PanelText.tr("waste.cement", "Cement", "Цемент"), BITUMEN = PanelText.tr("waste.bitumen", "Bitumen", "Битум"),
            MELTER = PanelText.tr("waste.melter", "Glass", "Стекло"),
            HLW = PanelText.tr("waste.hlw", "HLW: melter only", "ВАО: только стекло"),
            LOAD = PanelText.tr("waste.load", "Cement, empty drums, spent resin", "Цемент, пустые бочки, отработанная смола"),
            PRODUCT = PanelText.tr("waste.product", "Waste packages", "Упаковки РАО"),
            EVAP = PanelText.tr("waste.evap_status", "Concentrate %s · condensate %s Bq/l · %s drums",
                    "Куб: %s · конденсат %s Бк/л · бочек %s"),
            NONE = PanelText.tr("waste.none", "empty", "пусто"),
            PRESS = PanelText.tr("waste.press", "Press %s MPa · last reduction ×%s · in the drum %s l raw", "Пресс %s МПа · сокращение ×%s · в бочке %s л"),
            PUCKS = PanelText.tr("waste.pucks", "Overpack: %s pucks, %s l of 180", "Контейнер: таблеток %s, %s л из 180"),
            CLOSE = PanelText.tr("waste.close", "Close", "Закрыть"),
            CLOSE_TIP = PanelText.tr("waste.close_tip", "Seal the overpack with what is pressed", "Закрыть контейнер с тем, что спрессовано"),
            NO_DRUM = PanelText.tr("waste.no_drum", "No drums", "Нет бочек"),
            COMPACT_LOAD = PanelText.tr("waste.compact_load", "Contaminated items, filters, empty drums",
                    "Загрязнённые предметы, патроны фильтров, пустые бочки"),
            INTAKE = PanelText.tr("waste.intake", "Waste package", "Упаковка РАО"),
            OVERPACK = PanelText.tr("waste.overpack", "Copper canisters", "Медные контейнеры"),
            INVENTORY = PanelText.tr("waste.inventory", "%s packages · %s GBq · %s W", "Упаковок %s · %s ГБк · %s Вт"),
            DOSE = PanelText.tr("waste.dose", "Peak dose after closure %s µSv/y in %s years (%s)",
                    "Пик дозы после закрытия %s мкЗв/год через %s лет (%s)"),
            NO_RELEASE = PanelText.tr("waste.no_release", "Nothing reaches the river within 10 million years",
                    "За 10 млн лет до реки ничего не доходит"),
            RELEASE = PanelText.tr("waste.release", "Release", "Выход"),
            OVER = PanelText.tr("waste.over", "> 0.3 mSv/y", "> 0,3 мЗв/год"),
            REJECT = PanelText.tr("waste.reject", "Rejected", "Отказ"),
            LINK = PanelText.tr("waste.link", "Reactor", "Реактор"),
            RESIN = PanelText.tr("waste.resin", "Resin bed", "Ионит"), FILTER = PanelText.tr("waste.filter", "Filter", "Фильтр"),
            CHANGE_RESIN = PanelText.tr("waste.change_resin", "Resin", "Ионит"),
            CHANGE_RESIN_TIP = PanelText.tr("waste.change_resin_tip", "Sluice the resin out now", "Выгрузить ионит сейчас"),
            CHANGE_FILTER = PanelText.tr("waste.change_filter", "Filter", "Патрон"),
            CHANGE_FILTER_TIP = PanelText.tr("waste.change_filter_tip", "Change the filter cartridge now", "Сменить патрон сейчас"),
            CLEANUP_STATUS = PanelText.tr("waste.cleanup", "Reactor %s MW · resin %s GBq (%s %% used) · filter %s GBq (%s %%)",
                    "Реактор %s МВт · ионит %s ГБк (выработано %s %%) · патрон %s ГБк (%s %%)"),
            FRESH = PanelText.tr("waste.fresh", "Fresh resin and cartridges", "Свежий ионит и патроны"),
            SPENT = PanelText.tr("waste.spent", "Spent resin and cartridges", "Отработанные ионит и патроны");

    public static final PanelLayout EVAPORATOR, COMPACTOR, NEAR_SURFACE, DEEP, CLEANUP;

    static {
        PanelLayout.Builder e = PanelLayout.builder("lrw_evaporator", 248, 140, E_CHANNELS)
                .dial(8, 24, false, E_SUMP_L, 1, 4000, Double.NaN, 0, L.get(), SUMP.get(), null)
                .dial(56, 24, false, E_SALT_GL, 1, 400, 300, 0, GL.get(), SALT.get(), null)
                .bar(104, 24, 60, E_POOL, 10, 100, 0xFF8A7A40, POOL.get(), null)
                .bar(104, 40, 60, E_COND, 10, 100, 0xFF4080D0, COND.get(), null)
                .lamp(172, 20, E_FLAGS, EF_RUN, LampColor.GREEN, WORKING.get())
                .lamp(172, 32, E_FLAGS, EF_READY, LampColor.GREEN, READY.get())
                .lamp(172, 44, E_FLAGS, EF_HLW, LampColor.RED, HLW.get())
                .lamp(210, 20, E_FLAGS, EF_CEMENT, LampColor.WHITE, CEMENT.get())
                .lamp(210, 32, E_FLAGS, EF_BITUMEN, LampColor.WHITE, BITUMEN.get())
                .lamp(210, 44, E_FLAGS, EF_MELTER, LampColor.WHITE, MELTER.get())
                .button(8, 70, 44, B_RUN, RUN.get(), null);
        for (int i = 0; i < LrwEvaporatorBlockEntity.IN_SLOTS; i++)
            e.slot(8 + 18 * i, 94, i, LrwEvaporatorBlockEntity::loadable, LOAD.get());
        for (int i = 0; i < LrwEvaporatorBlockEntity.OUT_SLOTS; i++)
            e.output(122 + 18 * (i % 6), 94, LrwEvaporatorBlockEntity.IN_SLOTS + i, PRODUCT.get());
        EVAPORATOR = e.readout(8, 124, 232, WastePanels::evaporator, null).build();

        PanelLayout.Builder c = PanelLayout.builder("waste_compactor", 248, 120, C_CHANNELS)
                .lamp(8, 22, C_FLAGS, CF_RUN, LampColor.GREEN, WORKING.get())
                .lamp(8, 34, C_FLAGS, CF_NO_DRUM, LampColor.AMBER, NO_DRUM.get())
                .button(80, 22, 44, B_RUN, RUN.get(), null)
                .button(130, 22, 44, B_CLOSE, CLOSE.get(), CLOSE_TIP.get());
        for (int i = 0; i < WasteCompactorBlockEntity.IN_SLOTS; i++)
            c.slot(8 + 18 * i, 56, i, WasteCompactorBlockEntity::loadable, COMPACT_LOAD.get());
        for (int i = 0; i < WasteCompactorBlockEntity.OUT_SLOTS; i++)
            c.output(158 + 18 * i, 56, WasteCompactorBlockEntity.IN_SLOTS + i, PRODUCT.get());
        COMPACTOR = c.readout(8, 84, 232, x -> PRESS.get(x.raw(C_PRESSURE), x.num(C_VRF, 10, 1), x.raw(C_RAW_L)), null)
                .readout(8, 98, 232, x -> PUCKS.get(x.raw(C_PUCKS), x.raw(C_PUCKS_L)), null).build();

        NEAR_SURFACE = repository("near_surface_repository", false);
        DEEP = repository("deep_geological_repository", true);

        CLEANUP = PanelLayout.builder("reactor_water_cleanup", 248, 110, W_CHANNELS)
                .lamp(8, 22, W_FLAGS, WF_LINK, LampColor.GREEN, LINK.get())
                .lamp(8, 34, W_FLAGS, WF_RESIN, LampColor.GREEN, RESIN.get())
                .lamp(8, 46, W_FLAGS, WF_FILTER, LampColor.GREEN, FILTER.get())
                .bar(80, 22, 80, W_RESIN_USE, 10, 100, 0xFFD8C840, RESIN.get(), null)
                .bar(80, 38, 80, W_FILTER_USE, 10, 100, 0xFFB0B0B0, FILTER.get(), null)
                .bar(80, 54, 80, W_LRW, 10, 100, 0xFF8A7A40, POOL.get(), null)
                .button(176, 22, 60, B_RESIN, CHANGE_RESIN.get(), CHANGE_RESIN_TIP.get())
                .button(176, 40, 60, B_FILTER, CHANGE_FILTER.get(), CHANGE_FILTER_TIP.get())
                .slot(8, 70, ReactorWaterCleanupBlockEntity.RESIN_SLOT, s -> s.is(PcItems.ION_EXCHANGE_RESIN_SBA.get()), FRESH.get())
                .slot(26, 70, ReactorWaterCleanupBlockEntity.FILTER_SLOT,
                        s -> s.is(PcItems.FILTER_CARTRIDGE.get()) && !s.has(ActivityData.ACTIVITY.get()), FRESH.get())
                .output(80, 70, 2, SPENT.get()).output(98, 70, 3, SPENT.get()).output(116, 70, 4, SPENT.get()).output(134, 70, 5, SPENT.get())
                .readout(8, 94, 232, x -> CLEANUP_STATUS.get(x.num(W_MW, 10, 1), x.num(W_RESIN_GBQ, 100, 2), x.num(W_RESIN_USE, 10, 0),
                        x.num(W_FILTER_GBQ, 100, 2), x.num(W_FILTER_USE, 10, 0)), null)
                .build();
    }

    private WastePanels() {
    }

    private static PanelLayout repository(String id, boolean deep) {
        PanelLayout.Builder b = PanelLayout.builder(id, 248, 100, R_CHANNELS)
                .lamp(8, 22, R_FLAGS, RF_RELEASE, LampColor.AMBER, RELEASE.get())
                .lamp(8, 34, R_FLAGS, RF_OVER, LampColor.RED, OVER.get())
                .lamp(8, 46, R_FLAGS, RF_REJECT, LampColor.RED, REJECT.get())
                .slot(120, 26, RepositoryBlockEntity.INTAKE, WastePackage::isPackage, INTAKE.get());
        if (deep) b.slot(150, 26, RepositoryBlockEntity.OVERPACK, s -> s.is(PcItems.COPPER_CANISTER.get()), OVERPACK.get());
        return b.readout(8, 66, 232, x -> INVENTORY.get(x.raw(R_PACKAGES), x.raw(R_ACT_GBQ), x.raw(R_HEAT_W)), null)
                .readout(8, 80, 232, WastePanels::dose, null).build();
    }

    public static List<PanelLayout> all() {
        return List.of(EVAPORATOR, COMPACTOR, NEAR_SURFACE, DEEP, CLEANUP);
    }

    private static Component evaporator(PanelValues v) {
        int cat = v.raw(E_CAT);
        Component c = cat == 0 ? NONE.get()
                : WasteText.msg("cat." + dev.denfry.promptcritical.sim.waste.WasteClassification.Category.values()[cat - 1].name().toLowerCase(Locale.ROOT));
        return EVAP.get(c, v.num(E_COND_MBQ_L, 1000, 3), v.raw(E_DRUMS));
    }

    private static Component dose(PanelValues v) {
        if (!v.bit(R_FLAGS, RF_RELEASE)) return NO_RELEASE.get();
        double sv = Math.pow(10, v.raw(R_DOSE_LOG) / 100.0), years = Math.pow(10, v.raw(R_YEARS_LOG) / 100.0);
        int n = v.raw(R_NUCLIDE);
        String name = n >= 0 && n < RepositoryBlockEntity.NUCLIDES.length ? RepositoryBlockEntity.NUCLIDES[n] : "?";
        return DOSE.get(PanelText.num(sv * 1e6, sv * 1e6 < 0.01 ? 5 : 2), String.format(Locale.ROOT, "%.0f", years), name);
    }
}

package dev.denfry.promptcritical.mod.isotopes;

import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Щиты изотопов (12 §12.9): канал облучения — поток, флюенс, активность продукта, температура капсулы против
 * предела, вес мишени в реактивности; РИТЭГ — тепло, электричество, спаи, остаток мощности, нагрузка.
 */
public final class IsotopePanels {
    // канал облучения
    public static final int CH_FLUX_LOG = 0, CH_TEMP_C = 1, CH_POWER_W = 2, CH_ACTIVITY_LOG = 3, CH_FLUENCE_LOG = 4, CH_WORTH_PCM = 5,
            CH_SOURCE = 6, CH_FLAGS = 7, CH_LIMIT_C = 8, CH_CHANNELS = 9;
    public static final int F_LOADED = 0, F_FAILED = 1, F_FLUX = 2, F_CONTAMINATED = 3;
    // РИТЭГ
    public static final int R_THERMAL_W = 0, R_ELECTRIC_DW = 1, R_HOT_C = 2, R_COLD_C = 3, R_REMAIN_PCT = 4, R_LOAD_DW = 5,
            R_EFF_PERMILLE = 6, R_AGE_DAYS = 7, R_FLAGS = 8, R_KIND = 9, R_CHANNELS = 10;
    public static final int RF_OVERTEMP = 0, RF_ACTIVE = 1, RF_FULL = 2;

    private static final Tr CELSIUS = PanelText.CELSIUS, W = PanelText.tr("unit.w", "W", "Вт"),
            CAPSULE_T = PanelText.tr("iso.capsule_t", "Capsule", "Капсула"),
            HEAT = PanelText.tr("iso.capsule_heat", "Capsule heat", "Тепло капсулы"),
            LOADED = PanelText.tr("iso.loaded", "Loaded", "Загружен"),
            FAILED = PanelText.tr("iso.failed", "Leak", "Разгерм."),
            FLUX = PanelText.tr("iso.flux", "Flux", "Поток"),
            DIRTY = PanelText.tr("iso.dirty", "Contam.", "Загрязн."),
            CAPSULE_SLOT = PanelText.tr("iso.capsule_slot", "Irradiation capsule with a target (cobalt, iridium, NpO₂)",
                    "Капсула облучения с мишенью (кобальт, иридий, NpO₂)"),
            CAPSULE_TIP = PanelText.tr("iso.capsule_tip", "Capsule %s °C · failure above %s °C · heat %s W",
                    "Капсула %s °C · разгерметизация выше %s °C · тепло %s Вт"),
            CHANNEL_STATUS = PanelText.tr("iso.channel_status", "%s · φ %s n/cm²s · product %s Bq · fluence %s n/cm² · worth %s pcm",
                    "%s · φ %s н/(см²·с) · продукт %s Бк · флюенс %s н/см² · вес %s pcm"),
            HOT = PanelText.tr("iso.hot", "Hot junction", "Горячий спай"),
            COLD = PanelText.tr("iso.cold", "Fins", "Рёбра"),
            ELECTRIC = PanelText.tr("iso.electric", "Electric", "Электр."),
            OVERTEMP = PanelText.tr("iso.overtemp", "Overheat", "Перегрев"),
            ACTIVE = PanelText.tr("iso.active", "Output", "Выход"),
            FULL = PanelText.tr("iso.full", "Full load", "Нагрузка"),
            SOURCE_SLOT = PanelText.tr("iso.source_slot", "Heat source: Sr-90 (SrTiO₃) or Pu-238 (PuO₂)", "Источник тепла: Sr-90 (SrTiO₃) или Pu-238 (PuO₂)"),
            ELECTRIC_TIP = PanelText.tr("iso.electric_tip", "%s W of %s W heat · efficiency %s %% · load %s W",
                    "%s Вт из %s Вт тепла · КПД %s %% · нагрузка %s Вт"),
            RTG_STATUS = PanelText.tr("iso.rtg_status", "%s · %s W heat (%s %% of new) · %s W electric · age %s days",
                    "%s · тепло %s Вт (%s %% от нового) · электричество %s Вт · наработка %s сут");
    private static final Tr[] SOURCES = {
            PanelText.tr("iso.src.none", "no reactor", "нет реактора"),
            PanelText.tr("iso.src.pile", "graphite pile T0", "графитовый котёл T0"),
            PanelText.tr("iso.src.power", "experimental channel of T1", "экспериментальный канал T1"),
            PanelText.tr("iso.src.no_slot", "T1 has no free channel", "у T1 нет свободного канала")};
    private static final Tr[] KINDS = {
            PanelText.tr("iso.kind.none", "no heat source", "нет источника тепла"),
            PanelText.tr("iso.kind.sr", "Sr-90, Beta-M type", "Sr-90, типа «Бета-М»"),
            PanelText.tr("iso.kind.pu", "Pu-238, SiGe", "Pu-238, SiGe")};

    public static final PanelLayout CHANNEL = PanelLayout.builder("irradiation_channel", 220, 120, CH_CHANNELS)
            .dial(8, 24, false, CH_TEMP_C, 1, 800, 600, 0, CELSIUS.get(), CAPSULE_T.get(),
                    v -> CAPSULE_TIP.get(v.raw(CH_TEMP_C), v.raw(CH_LIMIT_C), v.raw(CH_POWER_W)))
            .bar(64, 26, 100, CH_POWER_W, 1, 2000, 0xFFB06030, HEAT.get(), null)
            .lamp(64, 44, CH_FLAGS, F_LOADED, LampColor.GREEN, LOADED.get())
            .lamp(110, 44, CH_FLAGS, F_FLUX, LampColor.AMBER, FLUX.get())
            .lamp(64, 58, CH_FLAGS, F_FAILED, LampColor.RED, FAILED.get())
            .lamp(110, 58, CH_FLAGS, F_CONTAMINATED, LampColor.RED, DIRTY.get())
            .slot(180, 40, 0, IrradiationChannelBlockEntity::isCapsule, CAPSULE_SLOT.get())
            .readout(8, 84, 204, IsotopePanels::channelStatus, null)
            .build();

    public static final PanelLayout RTG = PanelLayout.builder("rtg_thermoelectric", 220, 120, R_CHANNELS)
            .dial(8, 24, false, R_HOT_C, 1, 1100, 900, 0, CELSIUS.get(), HOT.get(), null)
            .dial(64, 24, false, R_COLD_C, 1, 300, 200, 0, CELSIUS.get(), COLD.get(), null)
            .dial(120, 24, false, R_ELECTRIC_DW, 10, 50, Double.NaN, 1, W.get(), ELECTRIC.get(),
                    v -> ELECTRIC_TIP.get(v.num(R_ELECTRIC_DW, 10, 1), v.raw(R_THERMAL_W), v.num(R_EFF_PERMILLE, 10, 1),
                            v.num(R_LOAD_DW, 10, 1)))
            .lamp(8, 70, R_FLAGS, RF_ACTIVE, LampColor.GREEN, ACTIVE.get())
            .lamp(56, 70, R_FLAGS, RF_FULL, LampColor.AMBER, FULL.get())
            .lamp(104, 70, R_FLAGS, RF_OVERTEMP, LampColor.RED, OVERTEMP.get())
            .slot(180, 40, 0, RtgBlockEntity::isHeatSource, SOURCE_SLOT.get())
            .readout(8, 88, 204, IsotopePanels::rtgStatus, null)
            .build();

    private IsotopePanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(CHANNEL, RTG, TrigaPanels.TRIGA);
    }

    /** Величина в канал щита: 100·lg(x), 0 — пусто. */
    static int log100(double x) {
        return x > 1 ? (int) Math.round(100 * Math.log10(x)) : 0;
    }

    static String fromLog(int v) {
        return v <= 0 ? "0" : String.format(Locale.ROOT, "%.2g", Math.pow(10, v / 100.0));
    }

    private static Component channelStatus(PanelValues v) {
        Tr src = SOURCES[Math.floorMod(v.raw(CH_SOURCE), SOURCES.length)];
        Component c = CHANNEL_STATUS.get(src.get(), fromLog(v.raw(CH_FLUX_LOG)), fromLog(v.raw(CH_ACTIVITY_LOG)), fromLog(v.raw(CH_FLUENCE_LOG)),
                v.raw(CH_WORTH_PCM));
        return v.bit(CH_FLAGS, F_FAILED) || v.bit(CH_FLAGS, F_CONTAMINATED) ? c.copy().withStyle(ChatFormatting.RED) : c;
    }

    private static Component rtgStatus(PanelValues v) {
        Tr kind = KINDS[Math.floorMod(v.raw(R_KIND), KINDS.length)];
        Component c = RTG_STATUS.get(kind.get(), v.raw(R_THERMAL_W), v.num(R_REMAIN_PCT, 10, 1), v.num(R_ELECTRIC_DW, 10, 1), v.raw(R_AGE_DAYS));
        return v.bit(R_FLAGS, RF_OVERTEMP) ? c.copy().withStyle(ChatFormatting.RED) : c;
    }
}

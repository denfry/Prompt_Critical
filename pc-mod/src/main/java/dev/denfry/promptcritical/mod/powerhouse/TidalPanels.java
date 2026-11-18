package dev.denfry.promptcritical.mod.powerhouse;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.sim.hydraulics.TidalPlant;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Щиты A0 прилива: приливной станции (водомерные стёкла моря и бассейна, напор, ваттметр, лампы состояния, ключи
 * режима) и мареографа (уровень моря, фаза Луны, амплитуда M2). Каналы — целые; стёкла — ‰ шкалы ± сизигийного
 * размаха, средний уровень — середина стекла.
 */
public final class TidalPanels {
    public static final int SEA_PERMIL = 0, BASIN_PERMIL = 1, SEA_CM = 2, BASIN_CM = 3, HEAD_CM = 4, KW10 = 5, RATED_KW = 6,
            KWH = 7, AREA = 8, MODE = 9, MOON = 10, M2_CM = 11, FLAGS = 12, CHANNELS = 13;
    public static final int F_CLOSED = 0, F_EBB = 1, F_FLOOD = 2, F_SLUICE = 3, F_HOLD = 4;

    private static final int SEA_COLOR = 0xFF3A6EA5;
    /** Сизигийная величина на метр амплитуды M2 при составе прилива мода: 2·(1 + S2/M2). */
    private static final double SPRING_PER_M2 = dev.denfry.promptcritical.sim.environment.Tide.Site.semidiurnal(1).springRangeM();
    private static final Tr SEA = PanelText.tr("tidal.sea", "Sea", "Море"),
            BASIN = PanelText.tr("tidal.basin", "Basin", "Бассейн"),
            HEAD = PanelText.tr("tidal.head", "Head", "Напор"),
            POWER = PanelText.tr("tidal.power", "Output", "Мощность"),
            METRES = PanelText.tr("unit.m", "m", "м"),
            KW = PanelText.tr("unit.kw", "kW", "кВт"),
            EBB = PanelText.tr("tidal.ebb", "Ebb gen.", "Отлив"),
            FLOOD = PanelText.tr("tidal.flood", "Flood gen.", "Прилив"),
            SLUICE = PanelText.tr("tidal.sluice", "Sluices", "Затворы"),
            HOLD = PanelText.tr("tidal.hold", "Holding", "Ожидание"),
            OFF = PanelText.tr("tidal.off", "Open", "Открыто"),
            ONE_WAY = PanelText.tr("tidal.one_way", "Ebb", "Отлив"),
            TWO_WAY = PanelText.tr("tidal.two_way", "Two-way", "Двустор."),
            OFF_TIP = PanelText.tr("tidal.off_tip", "Gates open: the basin follows the sea", "Затворы открыты: бассейн ходит за морем"),
            ONE_WAY_TIP = PanelText.tr("tidal.one_way_tip", "One-way: fill through the sluices, hold, generate on the ebb",
                    "Одностороннее: наполнение через отверстия, ожидание, генерация на отливе"),
            TWO_WAY_TIP = PanelText.tr("tidal.two_way_tip", "Two-way: generate on both the ebb and the flood, sluice between them",
                    "Двустороннее: генерация и на отливе, и на приливе, между ними — выравнивание отверстиями"),
            SEA_TIP = PanelText.tr("tidal.sea_tip", "Sea %s m from mean · M2 amplitude here %s m", "Море %s м от среднего · амплитуда M2 здесь %s м"),
            BASIN_TIP = PanelText.tr("tidal.basin_tip", "Basin %s m from mean sea level", "Бассейн %s м от среднего уровня моря"),
            VALUES = PanelText.tr("tidal.values", "%s · basin %s m² · unit %s kW · %s kWh",
                    "%s · бассейн %s м² · агрегат %s кВт · %s кВт·ч"),
            NOT_CLOSED = PanelText.tr("tidal.not_closed", "No closed basin behind the dam or no sea in front of it",
                    "За плотиной нет перекрытого бассейна или перед ней нет моря"),
            MOON_TIP = PanelText.tr("tidal.moon_tip", "Moon: %s · spring tides at full and new moon, neaps at the quarters",
                    "Луна: %s · сизигийные приливы в полнолуние и новолуние, квадратурные — в четверти"),
            GAUGE = PanelText.tr("tidal.gauge", "Sea %s m · M2 %s m · spring range %s m · moon: %s",
                    "Море %s м · M2 %s м · сизигийная величина %s м · Луна: %s"),
            NO_SEA = PanelText.tr("tidal.no_sea", "No sea within reach: no tide here", "Моря поблизости нет: прилива здесь нет");
    private static final Tr[] MOON_NAMES = {
            PanelText.tr("moon.0", "full", "полнолуние"), PanelText.tr("moon.1", "waning gibbous", "убывающая"),
            PanelText.tr("moon.2", "last quarter", "последняя четверть"), PanelText.tr("moon.3", "waning crescent", "старая луна"),
            PanelText.tr("moon.4", "new", "новолуние"), PanelText.tr("moon.5", "waxing crescent", "молодая луна"),
            PanelText.tr("moon.6", "first quarter", "первая четверть"), PanelText.tr("moon.7", "waxing gibbous", "растущая"),
    };

    public static final PanelLayout UNIT = PanelLayout.builder("tidal_unit", 232, 128, CHANNELS)
            .glass(8, 24, 74, SEA_PERMIL, SEA_COLOR, Double.NaN, 0.5, SEA.get(),
                    v -> SEA_TIP.get(v.num(SEA_CM, 100, 2), v.num(M2_CM, 100, 2)))
            .glass(32, 24, 74, BASIN_PERMIL, SEA_COLOR, Double.NaN, 0.5, BASIN.get(), v -> BASIN_TIP.get(v.num(BASIN_CM, 100, 2)))
            .dial(56, 24, false, HEAD_CM, 100, 10, Double.NaN, 2, METRES.get(), HEAD.get(), null)
            .dial(112, 24, false, KW10, 10, 500, Double.NaN, 0, KW.get(), POWER.get(), null)
            .lamp(170, 22, FLAGS, F_EBB, LampColor.GREEN, EBB.get())
            .lamp(170, 36, FLAGS, F_FLOOD, LampColor.GREEN, FLOOD.get())
            .lamp(170, 50, FLAGS, F_SLUICE, LampColor.AMBER, SLUICE.get())
            .lamp(170, 64, FLAGS, F_HOLD, LampColor.WHITE, HOLD.get())
            .button(56, 84, 52, TidalPlant.Mode.OFF.ordinal(), OFF.get(), OFF_TIP.get())
            .button(112, 84, 52, TidalPlant.Mode.EBB.ordinal(), ONE_WAY.get(), ONE_WAY_TIP.get())
            .button(168, 84, 56, TidalPlant.Mode.TWO_WAY.ordinal(), TWO_WAY.get(), TWO_WAY_TIP.get())
            .readout(8, 108, 216, TidalPanels::values, v -> MOON_TIP.get(moon(v)))
            .noInventory().build();

    public static final PanelLayout GAUGE_PANEL = PanelLayout.builder("tide_gauge", 176, 108, CHANNELS)
            .glass(8, 24, 74, SEA_PERMIL, SEA_COLOR, Double.NaN, 0.5, SEA.get(),
                    v -> SEA_TIP.get(v.num(SEA_CM, 100, 2), v.num(M2_CM, 100, 2)))
            .readout(36, 30, 132, TidalPanels::gauge, v -> MOON_TIP.get(moon(v)))
            .noInventory().build();

    private TidalPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(UNIT, GAUGE_PANEL);
    }

    /** Уровень в ‰ стекла: средний уровень — 500, ± {@code spanM} — края. */
    public static int glass(double levelM, double spanM) {
        return (int) Math.round(Math.max(0, Math.min(1000, 500 + 500 * levelM / spanM)));
    }

    private static Component moon(PanelValues v) {
        return MOON_NAMES[Math.floorMod(v.raw(MOON), MOON_NAMES.length)].get();
    }

    private static Component values(PanelValues v) {
        if (!v.bit(FLAGS, F_CLOSED)) return NOT_CLOSED.get().withStyle(ChatFormatting.GOLD);
        Tr mode = switch (Math.floorMod(v.raw(MODE), 3)) {
            case 0 -> OFF;
            case 1 -> ONE_WAY;
            default -> TWO_WAY;
        };
        return VALUES.get(mode.get(), v.raw(AREA), v.raw(RATED_KW), v.raw(KWH));
    }

    private static Component gauge(PanelValues v) {
        if (v.raw(M2_CM) == 0) return NO_SEA.get().withStyle(ChatFormatting.GOLD);
        return GAUGE.get(v.num(SEA_CM, 100, 2), v.num(M2_CM, 100, 2), PanelText.num(SPRING_PER_M2 * v.raw(M2_CM) / 100, 1), moon(v));
    }
}

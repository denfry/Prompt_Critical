package dev.denfry.promptcritical.sim.manufacturing;

import dev.denfry.promptcritical.sim.metallurgy.SteelGrade;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Сессия ковки: огонь, удары, закалка и отпуск как решения игрока на детерминированных часах. Каждый шаг
 * возвращает новую сессию; {@code lastEvents} хранит события только этого шага. Ручной ГУИ, силовой молот и
 * автоматика ведут одну и ту же сессию (план MF1, задача 7).
 *
 * @param clockS игровое время сессии, с
 */
public record ForgingSession(Workpiece piece, Place place, double clockS, List<ForgeEvent> lastEvents) {
    /** Где заготовка сейчас. */
    public enum Place { FIRE, ANVIL, COOLED }

    /** Перенос из горна на наковальню, с [BALANCE]: три секунды от огня до первого удара. */
    public static final double TRANSFER_S = 3;
    /** Температура воздуха для переноса и остывания в серии, К [моё]: 20 °C. */
    public static final double AMBIENT_K = 293.15;
    /** Ra кованой поверхности, мкм: «Справочник технолога-машиностроителя», Ra 25–100 [сверить]. */
    public static final double FORGED_RA_UM = 50;

    public ForgingSession {
        Objects.requireNonNull(piece, "piece");
        Objects.requireNonNull(place, "place");
        lastEvents = List.copyOf(lastEvents);
    }

    /** Заготовка на наковальне, часы в нуле. */
    public static ForgingSession start(Workpiece w) {
        return new ForgingSession(w, Place.ANVIL, 0, List.of());
    }

    /** Нагрев в огне при дутье {@code blast} 0..1 за {@code seconds}; BURNT, если нагрев впервые пережёг. */
    public ForgingSession inFire(double blast, double seconds) {
        if (!Double.isFinite(blast) || blast < 0 || blast > 1) {
            throw new IllegalArgumentException("blast out of 0..1: " + blast);
        }
        requireSeconds(seconds);
        Workpiece heated = ForgeHeating.heat(piece, ForgeHeating.fireTempK(blast), seconds);
        boolean newlyBurnt = piece.severity(PartDefect.BURNT) <= 0 && heated.severity(PartDefect.BURNT) > 0;
        return new ForgingSession(heated, Place.FIRE, clockS + seconds,
                newlyBurnt ? List.of(ForgeEvent.BURNT) : List.of());
    }

    /** Серия ручных ударов; из огня сначала {@link #TRANSFER_S} остывания на воздухе. */
    public ForgingSession strike(Forging.Op op) {
        Objects.requireNonNull(op, "op");
        Workpiece w = piece;
        double dt = Forging.HAND_SERIES_S;
        if (place == Place.FIRE) {
            w = ForgeHeating.cool(w, AMBIENT_K, TRANSFER_S);
            dt += TRANSFER_S;
        }
        Forging.Result r = Forging.series(w, op, Forging.HAND_BLOW_J, Forging.HAND_BLOWS,
                Forging.HAND_STRAIN_RATE, Forging.HAND_SERIES_S, AMBIENT_K);
        return new ForgingSession(r.piece(), Place.ANVIL, clockS + dt, r.events());
    }

    /** Закалка: вода и масло мгновенны в игровом времени, воздух добавляет {@link Quenching#AIR_COOL_S}. */
    public ForgingSession quench(Quenching.Medium m) {
        Objects.requireNonNull(m, "medium");
        Forging.Result r = Quenching.quench(piece, m);
        double dt = m == Quenching.Medium.AIR ? Quenching.AIR_COOL_S : 0;
        return new ForgingSession(r.piece(), Place.COOLED, clockS + dt, r.events());
    }

    /** Отпуск при {@code tK} в течение {@code seconds}. */
    public ForgingSession temper(double tK, double seconds) {
        requireSeconds(seconds);
        return new ForgingSession(Tempering.temper(piece, tK, seconds), Place.COOLED, clockS + seconds, List.of());
    }

    /** Паспорт кованой детали: маршрут FORGED, состояние по марке, квалитет 0, Ra {@link #FORGED_RA_UM}. */
    /** Id материала детали из стали: имя семейства строчными ({@code medium_carbon}, {@code wrought_iron}). */
    public static String materialId(dev.denfry.promptcritical.sim.metallurgy.SteelGrade g) {
        return g.family().name().toLowerCase(java.util.Locale.ROOT);
    }

    public PartSpec toPart(String kind) {
        SteelGrade g = piece.grade();
        // материал — id семейства стали, а не марка: марка кириллицей («кричное железо», «40Х») вошла бы в ключ стака,
        // а название переводит подсказка по ключу семейства
        return PartSpec.of(kind, materialId(g), PartRoute.FORGED, g.state(), piece.hardnessHrc(), 0,
                FORGED_RA_UM, Map.copyOf(piece.defects()));
    }

    private static void requireSeconds(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0) {
            throw new IllegalArgumentException("seconds must be finite and >= 0: " + seconds);
        }
    }
}

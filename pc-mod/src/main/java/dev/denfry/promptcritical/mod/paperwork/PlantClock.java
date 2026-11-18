package dev.denfry.promptcritical.mod.paperwork;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;

/**
 * Время записи в документах завода: сутки мира (день 1 — первый) и время суток часы:минуты. Сутки Minecraft
 * начинаются в 06:00 (dayTime 0 — восход), поэтому к dayTime прибавляется четверть суток.
 */
public final class PlantClock {
    private PlantClock() {
    }

    /** Сутки, с первого. */
    public static long day(Level level) {
        return level.getDayTime() / 24_000 + 1;
    }

    /** Минута суток 0…1439. */
    public static int minute(Level level) {
        long t = (level.getDayTime() % 24_000 + 6_000) % 24_000;
        return (int) (t * 1440 / 24_000);
    }

    /** «день 3, 08:15». */
    public static MutableComponent stamp(long day, int minute) {
        return Component.translatable(PaperworkText.STAMP, day,
                String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60));
    }
}

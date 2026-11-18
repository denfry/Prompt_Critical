package dev.denfry.promptcritical.sim.alarm;

import java.util.Map;

/**
 * Метрики сигнализации EEMUA 191 / ISA-18.2 (03c §3c.6.5, {@code getStatistics()} 03b §3b.2.14). Частоты считают
 * только приоритеты 1–3 и только возникновения (NORMAL/RTNUN → UNACK) в скользящем окне 10 мин физического времени.
 *
 * @param rate10min  возникновений за последние 10 мин
 * @param peak10min  наибольшее {@code rate10min} за всё время движка
 * @param standing   стоящих: условие держится дольше 24 ч
 * @param stale      сигнализаций без значения (датчик {@code BAD})
 * @param chattering номер → срабатываний за последние 60 с у дребезжащих
 * @param floodSince начало текущей лавины, NaN — лавины нет
 * @param shelved    в шельфинге (счётчик на L1, 03c §3c.6.4)
 * @param unacked    неквитированных (UNACK и RTNUN)
 * @param active     с условием в показанных состояниях (UNACK, ACKED)
 */
public record AlarmStatistics(int rate10min, int peak10min, int standing, int stale, Map<Integer, Integer> chattering, boolean flood,
        double floodSince, int shelved, int suppressed, int unacked, int active) {
}

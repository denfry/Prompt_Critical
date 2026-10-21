package dev.denfry.promptcritical.sim.ops;

/**
 * Строка {@code getFaults()} (13 §13.6.5).
 *
 * @param index      номер источника на панели
 * @param severity   видимая тяжесть 0–3 (по известному значению); у неизвестной — 0 и знак «?»
 * @param known      значение известно (счётчик, паспорт или замер)
 * @param measuredAt тик последнего замера; −1 — не измерялась (у {@code ALWAYS}/{@code PASSPORT} — −1)
 * @param since      тик, с которого видимая тяжесть &gt; 0; −1 — норма
 */
public record FaultView(int index, MaintenanceFaultKind kind, int severity, boolean known, long measuredAt,
        long since) {
}

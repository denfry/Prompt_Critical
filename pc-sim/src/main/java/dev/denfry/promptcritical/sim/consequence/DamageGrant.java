package dev.denfry.promptcritical.sim.consequence;

/**
 * Решение бюджета по запросу (04d §4d.2.2).
 *
 * @param blocksGranted разрешено блоков (поставлено в очередь)
 * @param blocksQueued  из них ждут применения на момент решения
 * @param blocksDenied  отказано на этапе решения
 * @param reason        основная причина отказа ({@link DenyReason#NONE}, если отказов нет)
 */
public record DamageGrant(long requestId, int blocksGranted, int blocksQueued, int blocksDenied, DenyReason reason) {
}

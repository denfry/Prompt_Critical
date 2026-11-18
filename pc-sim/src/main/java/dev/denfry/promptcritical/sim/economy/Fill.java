package dev.denfry.promptcritical.sim.economy;

/** Исполнение заявки (14 §14.11): {@code qty} со знаком заявки, {@code totalCents} — сумма сделки. */
public record Fill(long ticket, double qty, long avgPriceCents, long totalCents, FillStatus status) {
    public boolean executed() {
        return status == FillStatus.FILLED || status == FillStatus.PARTIAL;
    }
}

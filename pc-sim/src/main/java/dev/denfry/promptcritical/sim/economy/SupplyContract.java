package dev.denfry.promptcritical.sim.economy;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Форвардный контракт поставки {@code SUPPLY} (14 §14.6.6): продавец обязуется сдавать {@code qtyPerPeriod} единиц
 * товара за период по цене, зафиксированной при подписании; {@code buyer} = null — покупатель NPC. Депозит продавца —
 * 20 % стоимости периода (market.contract_margin).
 */
public record SupplyContract(long id, UUID seller, UUID buyer, String commodityId, double qtyPerPeriod, long priceCents,
                             int periods, long depositCents, PenaltyClause penalty, ContractState state) {
    /** market.contract_margin = 0,2 (§14.6.6, §14.13). */
    public static final double DEFAULT_MARGIN = 0.2;
    /** Порог недопоставки для дефолта: 3 периода подряд < 50 % (автомат §14.6.6). */
    public static final double DEFAULT_THRESHOLD = 0.5;
    public static final int DEFAULT_STREAK = 3;

    public SupplyContract {
        Objects.requireNonNull(seller);
        Objects.requireNonNull(commodityId);
        Objects.requireNonNull(penalty);
        Objects.requireNonNull(state);
        if (!(qtyPerPeriod > 0) || priceCents <= 0 || periods <= 0 || depositCents < 0)
            throw new IllegalArgumentException("invalid contract terms");
    }

    /** Ошибки подписания (§14.10, §14.8.2). */
    public enum Error { E_SAME_TEAM }

    /** Итог периода: оплата поставленного, неустойка, серия недопоставок, новое состояние, депозит покупателю. */
    public record Settlement(long paymentCents, long penaltyCents, int shortStreak, ContractState state,
                             long depositToBuyerCents) {}

    /** Новый контракт в состоянии {@code PROPOSED} с депозитом 20 % стоимости периода и неустойкой §14.6.6. */
    public static SupplyContract propose(long id, UUID seller, UUID buyer, String commodityId, double qtyPerPeriod,
                                         long priceCents, int periods) {
        long deposit = Math.round(DEFAULT_MARGIN * qtyPerPeriod * priceCents);
        return new SupplyContract(id, seller, buyer, commodityId, qtyPerPeriod, priceCents, periods, deposit,
                PenaltyClause.standard(deposit), ContractState.PROPOSED);
    }

    /** Проверка сторон: контракт со своей командой запрещён ({@code E_SAME_TEAM}). */
    public static Optional<Error> validateParties(UUID sellerTeam, UUID buyerTeam) {
        return buyerTeam != null && buyerTeam.equals(sellerTeam) ? Optional.of(Error.E_SAME_TEAM) : Optional.empty();
    }

    /** PROPOSED → ACTIVE при подписании второй стороной (депозит снимается со счёта продавца вызывающим). */
    public SupplyContract accept() {
        if (state != ContractState.PROPOSED) throw new IllegalStateException("contract " + id + " is " + state);
        return withState(ContractState.ACTIVE);
    }

    /** Расторжение по согласию: PROPOSED → CANCELLED, ACTIVE → CLOSED (депозит возвращается). */
    public SupplyContract close() {
        return switch (state) {
            case PROPOSED -> withState(ContractState.CANCELLED);
            case ACTIVE, SUSPENDED -> withState(ContractState.CLOSED);
            default -> throw new IllegalStateException("contract " + id + " is " + state);
        };
    }

    public SupplyContract withState(ContractState s) {
        return new SupplyContract(id, seller, buyer, commodityId, qtyPerPeriod, priceCents, periods, depositCents,
                penalty, s);
    }

    /**
     * Срок периода: поставлено {@code delivered} с опозданием {@code daysLate} суток (0 — в срок). Неустойка
     * 0,2·p·q_недопоставки плюс пени 1 % стоимости недопоставки в сутки, не больше депозита; три периода подряд ниже
     * 50 % — {@code DEFAULTED}, депозит переходит покупателю.
     */
    public Settlement settlePeriod(double delivered, int shortStreakBefore, double daysLate) {
        if (state != ContractState.ACTIVE) throw new IllegalStateException("contract " + id + " is " + state);
        double paidQty = Math.min(Math.max(0, delivered), qtyPerPeriod);
        long payment = Math.round(paidQty * priceCents);
        double shortfall = qtyPerPeriod - paidQty;
        double raw = penalty.shortfallRate() * priceCents * shortfall
                + penalty.dailyLateRate() * priceCents * shortfall * Math.max(0, daysLate);
        long pen = Math.min(Math.round(raw), penalty.capCents());
        int streak = paidQty < DEFAULT_THRESHOLD * qtyPerPeriod ? shortStreakBefore + 1 : 0;
        if (streak >= DEFAULT_STREAK) return new Settlement(payment, pen, streak, ContractState.DEFAULTED, depositCents);
        return new Settlement(payment, pen, streak, ContractState.ACTIVE, 0);
    }
}

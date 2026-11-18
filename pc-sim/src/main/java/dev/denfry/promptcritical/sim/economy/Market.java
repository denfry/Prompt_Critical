package dev.denfry.promptcritical.sim.economy;

import java.util.UUID;

/** Биржа (14 §14.11): котировки, оценка и исполнение заявок, шаг цен. Вызывается из серверного потока. */
public interface Market {
    Quote quote(String commodityId);

    long estimateSaleCents(String commodityId, double qty);

    Fill execute(UUID team, Order order);

    void step(double dtPhysS);
}

package dev.denfry.promptcritical.sim.economy;

import java.util.UUID;

/** Счёт команды в CR (14 §14.8.1), суммы в центах; {@code salesTodayCents} — выручка за текущие сутки (§14.8.2). */
public record Account(UUID team, long balanceCents, long reservedCents, long salesTodayCents) {}

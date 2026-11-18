package dev.denfry.promptcritical.sim.economy;

/** Итог заявки (14 §14.11); {@code REJECTED_FUNDS} — на счёте не хватает CR на покупку. */
public enum FillStatus { FILLED, PARTIAL, REJECTED_LIMIT, REJECTED_EPOCH, REJECTED_RESTRICTED, REJECTED_CAP, REJECTED_FUNDS,
    REJECTED_PERISHED, REJECTED_UNKNOWN }

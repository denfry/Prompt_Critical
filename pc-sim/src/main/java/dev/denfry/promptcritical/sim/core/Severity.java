package dev.denfry.promptcritical.sim.core;

/** Важность записи журнала (04d §4d.8.1); общая для {@link SimEvent.Journal} и журнала станции. */
public enum Severity {
    INFO,
    NOTICE,
    WARNING,
    CRITICAL
}

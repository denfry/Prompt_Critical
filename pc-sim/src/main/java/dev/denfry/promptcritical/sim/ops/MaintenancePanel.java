package dev.denfry.promptcritical.sim.ops;

import dev.denfry.promptcritical.sim.core.SimClock;
import dev.denfry.promptcritical.sim.core.SimId;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code maintenance_panel} установки (13 §13.6.5): собирает переменные износа владельцев, показывает то, что
 * диагностируемо (P2), и считает η_eff = η·∏(1 − e·s), λ_trip = λ₀·(1 + Σ c·s²) по истинному износу. Событие
 * {@code pc_maintenance_required} — при росте видимой тяжести, не чаще раза в 10 мин на неисправность.
 */
public final class MaintenancePanel {
    public static final long DEBOUNCE_TICKS = Math.round(600 / SimClock.FAST_DT);

    private static final class Entry {
        final FaultSource source;
        double measured = Double.NaN;
        long measuredAt = -1, since = -1, lastEventTick = Long.MIN_VALUE / 2;
        int reported;

        Entry(FaultSource source) {
            this.source = source;
        }
    }

    private final SimId unit;
    private final OpsConfig cfg;
    private final OpsEvents events;
    private final List<Entry> entries = new ArrayList<>();
    private boolean passport;

    public MaintenancePanel(SimId unit, OpsConfig cfg, OpsEvents events) {
        this.unit = unit;
        this.cfg = cfg;
        this.events = events;
    }

    public int add(FaultSource source) {
        entries.add(new Entry(source));
        return entries.size() - 1;
    }

    public int size() {
        return entries.size();
    }

    public FaultSource source(int index) {
        return entries.get(index).source;
    }

    /** Есть паспорт установки: видны тренды против базовых показателей ({@code PASSPORT}). */
    public void setPassport(boolean available) {
        passport = available;
    }

    /**
     * Замер прибором. Первое обнаружение (тяжесть выше сообщённой) даёт событие сразу, минуя дебаунс.
     *
     * @return false — прибор к этому виду не применим
     */
    public boolean measure(int index, Probe probe, long tick) {
        Entry e = entries.get(index);
        double r = probe.reading(e.source.kind(), e.source.value());
        if (Double.isNaN(r)) return false;
        e.measured = r;
        e.measuredAt = tick;
        notify(e, tick, true);
        return true;
    }

    /** Раз в SLOW: события роста видимой тяжести. */
    public void update(long tick) {
        for (Entry e : entries) notify(e, tick, false);
    }

    private void notify(Entry e, long tick, boolean measurement) {
        int s = visibleSeverity(e);
        if (s > 0 && e.since < 0) e.since = tick;
        if (s == 0) e.since = -1;
        if (s < e.reported) e.reported = s;
        if (s > e.reported && (measurement || tick - e.lastEventTick >= DEBOUNCE_TICKS)) {
            events.maintenanceRequired(unit, e.source.kind(), s, e.reported);
            e.reported = s;
            e.lastEventTick = tick;
        }
    }

    private boolean known(Entry e) {
        return switch (e.source.kind().detection()) {
            case ALWAYS -> true;
            case PASSPORT -> passport;
            case MEASURED -> e.measuredAt >= 0;
        };
    }

    private double knownValue(Entry e) {
        return switch (e.source.kind().detection()) {
            case ALWAYS -> e.source.value();
            case PASSPORT -> passport ? e.source.value() : Double.NaN;
            case MEASURED -> e.measured;
        };
    }

    private int visibleSeverity(Entry e) {
        return e.source.kind().severity(knownValue(e));
    }

    public List<FaultView> faults() {
        List<FaultView> out = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            out.add(new FaultView(i, e.source.kind(), visibleSeverity(e), known(e), e.measuredAt, e.since));
        }
        return out;
    }

    /** Тег {@code <установка>.maint.severity}: наибольшая видимая тяжесть. */
    public int maxSeverity() {
        int m = 0;
        for (Entry e : entries) m = Math.max(m, visibleSeverity(e));
        return m;
    }

    public double efficiency(double eta) {
        double r = eta;
        for (Entry e : entries) {
            MaintenanceFaultKind k = e.source.kind();
            r *= 1 - cfg.coefficients(k).eff() * k.normalized(e.source.value());
        }
        return r;
    }

    public double tripRate(double lambda0) {
        double sum = 0;
        for (Entry e : entries) {
            MaintenanceFaultKind k = e.source.kind();
            double s = k.normalized(e.source.value());
            sum += cfg.coefficients(k).trip() * s * s;
        }
        return lambda0 * (1 + sum);
    }

    /** {@code MAINTENANCE_DONE}: сброс у владельца; прошлый замер больше не действует. */
    public void repair(int index, long tick) {
        Entry e = entries.get(index);
        e.source.repair();
        if (e.measuredAt >= 0) {
            e.measured = 0;
            e.measuredAt = tick;
        }
        e.reported = 0;
        e.since = -1;
    }
}

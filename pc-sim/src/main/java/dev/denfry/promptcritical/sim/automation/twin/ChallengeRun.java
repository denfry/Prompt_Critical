package dev.denfry.promptcritical.sim.automation.twin;

import dev.denfry.promptcritical.sim.automation.twin.Challenge.Metric;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Попытка задачи наладчика (03d §3d.11.2) на серверном двойнике: {@code RUNNING} → {@code FAILED} (нарушено жёсткое
 * условие или не выполнен критерий — причина в {@link #reason()}) или {@code PASSED} с метриками. Попытка всегда идёт
 * с эталонным зерном {@link Challenge#SEED}, поэтому {@code PASSED} — сразу и {@code SUBMITTED}: отдельный повторный
 * прогон 03d §3d.11.2 не нужен (испытание на клиенте в моде не предусмотрено).
 */
public final class ChallengeRun implements AutoCloseable {
    private final Challenge challenge;
    private final ScenarioRun run;

    private ChallengeRun(Challenge challenge, ScenarioRun run) {
        this.challenge = challenge;
        this.run = run;
    }

    public static ChallengeRun start(Challenge c, String playerProgram) {
        return new ChallengeRun(c, ScenarioRun.start(c.scenario(), playerProgram, Challenge.SEED));
    }

    public Challenge challenge() {
        return challenge;
    }

    public ScenarioRun run() {
        return run;
    }

    public ScenarioRun.Status status() {
        return run.status();
    }

    public String reason() {
        return run.reason();
    }

    public void step() {
        run.step();
    }

    /** Прогнать до итога (тесты, серверная проверка без пульта). */
    public ChallengeRun finish() {
        while (run.status() == ScenarioRun.Status.RUNNING) run.step();
        return this;
    }

    /** Значения метрик прошедшей попытки в порядке задачи; у проваленной — пусто. */
    public Map<String, Double> metrics() {
        Map<String, Double> out = new LinkedHashMap<>();
        if (run.status() != ScenarioRun.Status.PASSED) return out;
        for (Metric m : challenge.metrics()) out.put(m.id(), value(m));
        return out;
    }

    private double value(Metric m) {
        TwinSession s = run.session();
        LoopMetrics loop = s.loops().isEmpty() ? null : s.loops().getFirst();
        return switch (m.source()) {
            case SIGNAL -> s.plant().read(m.signal());
            case SETTLED_S -> run.settledS();
            case IAE -> loop == null ? Double.NaN : loop.iae();
            case ISE -> loop == null ? Double.NaN : loop.ise();
            case ITAE -> loop == null ? Double.NaN : loop.itae();
            case OVERSHOOT -> loop == null ? Double.NaN : loop.overshoot();
            case SETTLING_S -> loop == null ? Double.NaN : loop.settlingTimeS();
            case VALVE_TRAVEL -> loop == null ? Double.NaN : loop.valveTravel();
            case SIZE -> run.program() == null ? Double.NaN : run.program().code().size();
        };
    }

    /** Попытка прошла и первая метрика укладывается в критерий достижения (если он задан). */
    public boolean meetsThreshold() {
        if (run.status() != ScenarioRun.Status.PASSED) return false;
        if (Double.isNaN(challenge.threshold())) return true;
        Double v = metrics().get(challenge.metrics().getFirst().id());
        return v != null && v <= challenge.threshold();
    }

    @Override
    public void close() {
        run.close();
    }
}

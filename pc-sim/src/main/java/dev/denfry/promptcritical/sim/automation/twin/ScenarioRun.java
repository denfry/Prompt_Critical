package dev.denfry.promptcritical.sim.automation.twin;

import dev.denfry.promptcritical.sim.automation.plc.IlProgram;

/**
 * Прогон сценария на двойнике (11 §11.3.3, 03d §3d.9.1 {@code evaluate}): сеанс, построенный по сценарию, проверка
 * критериев после каждого шага, балл и итог. Программа игрока ({@code fat}) загружается в ПЛК двойника вместо
 * программы установки.
 */
public final class ScenarioRun implements AutoCloseable {
    public enum Status { RUNNING, PASSED, FAILED }

    private final Scenario scenario;
    private final TwinSession session;
    private final IlProgram program;
    private Status status = Status.RUNNING;
    private String reason = "";
    private double lastUnsettledS, minMargin = Double.NaN, margin0 = Double.NaN;

    private ScenarioRun(Scenario scenario, TwinSession session, IlProgram program) {
        this.scenario = scenario;
        this.session = session;
        this.program = program;
    }

    /** Новый прогон. {@code playerProgram} — проект игрока для испытания FAT, иначе не нужен. */
    public static ScenarioRun start(Scenario s, String playerProgram, long seed) {
        TwinPlant plant = TwinPlants.create(s.plant(), s.preset(), seed);
        TwinSession session = new TwinSession(plant, s.io() != null ? s.io() : TwinPlants.defaultIo(s.plant()), seed);
        String src = s.fat() ? playerProgram : s.program();
        IlProgram p = src == null || src.isBlank() ? null : session.loadPlc(src);
        for (Scenario.Timed t : s.timeline()) session.schedule(t.tS(), t.action());
        if (s.loop() != null) session.track(s.loop().pv(), s.loop().sp(), s.loop().mv());
        ScenarioRun run = new ScenarioRun(s, session, p);
        if (s.fat() && (p == null || !p.ok())) run.finish(Status.FAILED, "program_not_loaded");
        return run;
    }

    public Scenario scenario() {
        return scenario;
    }

    public TwinSession session() {
        return session;
    }

    /** Результат компиляции загруженной программы, {@code null} — без ПЛК. */
    public IlProgram program() {
        return program;
    }

    public Status status() {
        return status;
    }

    /** Причина провала: авария установки, нарушенное условие или невыполненный критерий успеха. */
    public String reason() {
        return reason;
    }

    /** Момент двойника, после которого критерии успеха выполнялись без перерыва, с. */
    public double settledS() {
        return lastUnsettledS;
    }

    public double remainingS() {
        return Math.max(0, scenario.durationS() - session.timeS());
    }

    /** Один шаг двойника с проверкой критериев; после итога — ничего не делает. */
    public void step() {
        if (status != Status.RUNNING) return;
        session.step();
        TwinPlant plant = session.plant();
        if (plant.failure() != null) {
            finish(Status.FAILED, plant.failure());
            return;
        }
        for (Condition c : scenario.failure()) {
            if (c.test(plant::read)) {
                finish(Status.FAILED, c.toString());
                return;
            }
        }
        boolean ok = true;
        for (Condition c : scenario.success()) ok &= c.test(plant::read);
        if (!ok) lastUnsettledS = session.timeS();
        Scenario.Margin m = scenario.margin();
        if (m != null) {
            double gap = Math.abs(m.limit() - plant.read(m.signal()));
            if (Double.isNaN(margin0)) margin0 = gap;
            minMargin = Double.isNaN(minMargin) ? gap : Math.min(minMargin, gap);
        }
        if (session.timeS() >= scenario.durationS() - 1e-9) {
            if (ok) finish(Status.PASSED, "");
            else {
                StringBuilder unmet = new StringBuilder();
                for (Condition c : scenario.success()) if (!c.test(plant::read)) unmet.append(unmet.isEmpty() ? "" : ", ").append(c);
                finish(Status.FAILED, "unmet: " + unmet);
            }
        }
    }

    private void finish(Status s, String why) {
        status = s;
        reason = why;
    }

    /** Балл 0–1: провал — 0; иначе взвешенные доля оставшегося времени и запас до предела. */
    public double score() {
        if (status != Status.PASSED) return 0;
        double time = 1 - lastUnsettledS / scenario.durationS();
        double margin = Double.isNaN(minMargin) || !(margin0 > 0) ? 1 : Math.max(0, Math.min(1, minMargin / margin0));
        return (scenario.wTime() * time + scenario.wMargin() * margin) / (scenario.wTime() + scenario.wMargin());
    }

    @Override
    public void close() {
        session.close();
    }
}

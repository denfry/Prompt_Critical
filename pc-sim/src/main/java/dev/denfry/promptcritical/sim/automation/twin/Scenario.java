package dev.denfry.promptcritical.sim.automation.twin;

import java.util.List;

/**
 * Сценарий тренажёра или испытания двойника (11 §11.3.3, 03d §3d.9.1 FAT): установка и её начальное состояние,
 * программа ПЛК установки (ПАЗ, если есть) с привязкой, временная шкала отказов и возмущений, критерии.
 *
 * <p>Исход: {@code failure} — любое из условий хоть раз или авария установки — провал сразу; иначе к концу
 * {@code durationS} должны выполняться все {@code success}. Балл (0–1) прошедшего сценария: доля времени, оставшегося
 * после того, как критерии успеха выполнились и больше не нарушались ({@code wTime}), и наименьший запас до предела
 * {@code margin} относительно начального ({@code wMargin}). Провал — 0.
 *
 * @param fat     испытание программы игрока: ПЛК двойника исполняет загруженный проект, {@code program} не нужен
 * @param program программа ПЛК установки (IL/ST/LD/FBD/SFC) или {@code null}
 * @param margin  запас до предела или {@code null}
 * @param loop    контур для показателей или {@code null}
 */
public record Scenario(String id, String plant, String preset, double durationS, boolean fat, String program, IoMap io,
        List<Timed> timeline, List<Condition> success, List<Condition> failure, Margin margin, Loop loop, double wTime,
        double wMargin) {

    public record Timed(double tS, ScenarioAction action) {
    }

    /** Запас до предела: {@code limit} — значение, при котором запас исчерпан. */
    public record Margin(String signal, double limit) {
    }

    /** Контур, показатели которого пишутся за прогон (IAE, перерегулирование, ход механизма). */
    public record Loop(String pv, double sp, String mv) {
    }

    public Scenario {
        if (!(durationS > 0)) throw new IllegalArgumentException("duration");
        if (!(wTime >= 0 && wMargin >= 0 && wTime + wMargin > 0)) throw new IllegalArgumentException("weights");
        timeline = List.copyOf(timeline);
        success = List.copyOf(success);
        failure = List.copyOf(failure);
    }
}

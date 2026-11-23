package dev.denfry.promptcritical.sim.ops;

import java.util.Set;

/**
 * Решение о лицензии на эпоху: выдана или чего не хватает.
 *
 * @param missingEra   лицензии на предыдущую эпоху нет — её код; иначе {@code null}
 * @param missingTasks невыполненные ключевые задания маршрута предыдущей эпохи
 * @param missingKinds типы установок предыдущей эпохи, ещё не принятые командой
 */
public record LicenceVerdict(boolean granted, PlantEra era, PlantEra missingEra, Set<String> missingTasks,
        Set<UnitKind> missingKinds) {
    public LicenceVerdict {
        missingTasks = Set.copyOf(missingTasks);
        missingKinds = Set.copyOf(missingKinds);
    }
}

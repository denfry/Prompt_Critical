package dev.denfry.promptcritical.sim.ops;

import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Выдача лицензии на эпоху (спецификация профиля licensing, §1): у команды есть лицензия на предыдущую эпоху, выполнены
 * её ключевые задания маршрута и принят каждый её тип установки. Рейтинг надзора (17 §17.4.6) войдёт четвёртым условием,
 * когда проверка надзора появится в коде (веха HP5).
 */
public final class LicenceRules {
    private LicenceRules() {}

    /** @param keyTasksOfPrevious ключевые задания маршрута предыдущей эпохи (их выводит {@code pc-mod} из маршрута) */
    public static LicenceVerdict evaluate(LicenceRegistry reg, UUID team, PlantEra era, Set<String> keyTasksOfPrevious) {
        PlantEra prev = era.previous();
        if (prev == null) return new LicenceVerdict(true, era, null, Set.of(), Set.of());
        PlantEra missingEra = reg.licensed(team, prev) ? null : prev;
        Set<String> done = reg.tasksDone(team);
        Set<String> missingTasks = new TreeSet<>();
        for (String t : keyTasksOfPrevious) if (!done.contains(t)) missingTasks.add(t);
        Set<UnitKind> accepted = reg.acceptedKinds(team);
        Set<UnitKind> missingKinds = EnumSet.noneOf(UnitKind.class);
        for (UnitKind k : UnitKind.values()) if (k.era() == prev && !accepted.contains(k)) missingKinds.add(k);
        boolean granted = missingEra == null && missingTasks.isEmpty() && missingKinds.isEmpty();
        return new LicenceVerdict(granted, era, missingEra, missingTasks, missingKinds);
    }
}

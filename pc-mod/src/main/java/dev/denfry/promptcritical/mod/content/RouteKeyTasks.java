package dev.denfry.promptcritical.mod.content;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.resources.ResourceLocation;

/**
 * Ключевые задания эпохи для лицензии профиля licensing: задания эпохи, которых не требует ни одно другое задание той же
 * эпохи (концы её графа). Когда у маршрута появятся ветки дерева технологий, правило уточнится до концов каждой ветки.
 */
public final class RouteKeyTasks {
    private RouteKeyTasks() {}

    public static Set<String> of(HandbookManager.Data data, String era) {
        Set<ResourceLocation> required = new HashSet<>();
        for (RouteTask t : data.tasks().values()) if (era.equals(t.era())) required.addAll(t.requires());
        Set<String> keys = new TreeSet<>();
        for (RouteTask t : data.tasks().values())
            if (era.equals(t.era()) && !required.contains(t.id())) keys.add(t.id().toString());
        return keys;
    }
}

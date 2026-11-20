package dev.denfry.promptcritical.mod.content;

import java.util.List;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Задание «Маршрута» (11 §11.1.5, R23): отмечает шаг и объясняет его, ничего не блокирует (C2). {@code requires} —
 * рёбра графа: задание «открыто», когда выполнены все требуемые, но засчитывается и раньше — порядок у игрока свой.
 * {@code text} — «что сделать» (видно с {@code STANDARD}), {@code hint} — «как сделать» (только {@code TRAINING});
 * {@code ponder} — предмет, чьи Ponder-сцены показывает кнопка задания. {@code branch} — ветка эпохи, страница
 * дерева технологий справочника, на которой задание стоит.
 */
public record RouteTask(ResourceLocation id, String era, String branch, List<ResourceLocation> requires, Kind kind, RouteCondition condition,
        @Nullable ResourceLocation entry, @Nullable ResourceLocation ponder, int xp, ResourceLocation icon, Localized title,
        Localized text, Localized hint) {

    /** Типы заданий 11 §11.1.5; {@code scenario} и {@code program} — с тренажёром (§11.3) и ПЛК (03d). */
    public enum Kind {
        OBTAIN,
        BUILD,
        REACH,
        OPERATE,
        SCENARIO,
        PROGRAM,
        DISCOVER,
        CHOICE;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Kind of(String key) {
            return valueOf(key.toUpperCase(Locale.ROOT));
        }
    }

    /** Эпоха маршрута — файл {@code handbook/route/<era>.json}; {@code branches} — её ветки по порядку страниц дерева. */
    public record Era(ResourceLocation id, String era, Localized title, int sortIndex, List<Branch> branches, List<RouteTask> tasks) {
    }

    /** Ветка эпохи: линия производства (шахта, кокс и чугун, электросеть), страница дерева технологий. */
    public record Branch(String id, Localized title) {
    }
}

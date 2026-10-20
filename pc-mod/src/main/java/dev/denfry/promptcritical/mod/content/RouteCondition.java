package dev.denfry.promptcritical.mod.content;

import java.util.List;
import java.util.Map;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.resources.ResourceLocation;

/** Условие выполнения задания маршрута (11 §11.1.5). */
public sealed interface RouteCondition permits RouteCondition.Obtain, RouteCondition.Build, RouteCondition.Discover,
        RouteCondition.Event, RouteCondition.Choice {

    /** {@code count} предметов {@code id} (или тега, {@code tag}) в инвентаре; контейнеров станций пока нет (04d §4d.9). */
    record Obtain(ResourceLocation id, boolean tag, int count) implements RouteCondition {
    }

    /**
     * Установка поставлена игроком {@code count} раз (статистика «использовано»): крупные установки M2 ставятся целиком,
     * поэтому постановка и есть сборка.
     */
    record Build(ResourceLocation block, int count) implements RouteCondition {
    }

    /** Найден признак месторождения: игрок добыл блок {@code id} (или любой из тега). */
    record Discover(ResourceLocation id, boolean tag) implements RouteCondition {
    }

    /**
     * Событие домена с предикатами, как у триггера {@code sim_event} (11 §11.2.1). {@code durationS > 0} — условие
     * {@code reach}/{@code operate}: замеры установки рядом с игроком должны идти подряд и попадать в предикат всё это
     * время; промах или перерыв в замерах сбрасывает отсчёт.
     */
    record Event(ResourceLocation event, Map<String, MinMaxBounds.Doubles> predicate, double durationS) implements RouteCondition {
        public boolean matches(String e, String tag, double value) {
            if (!event.toString().equals(e)) return false;
            for (Map.Entry<String, MinMaxBounds.Doubles> p : predicate.entrySet()) {
                if (!p.getKey().equals(tag) || !p.getValue().matches(value)) return false;
            }
            return true;
        }
    }

    /** Развилка: выполнено любое из заданий {@code options}. */
    record Choice(List<ResourceLocation> options) implements RouteCondition {
    }
}

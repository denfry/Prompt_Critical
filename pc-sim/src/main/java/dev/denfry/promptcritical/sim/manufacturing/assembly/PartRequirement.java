package dev.denfry.promptcritical.sim.manufacturing.assembly;

import dev.denfry.promptcritical.sim.metallurgy.HeatTreatState;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Позиция спецификации машины (18 §18.8.1): какие детали подходят, сколько их и по каким признакам стенд их
 * оценивает.
 *
 * @param role      имя позиции (вал, подшипник, корпус…), уникально в спецификации
 * @param kinds     виды деталей ({@code PartSpec.kind}), хотя бы один
 * @param materials допустимые марки; пусто — любая (ворота по марке, §18.15.2)
 * @param count     сколько деталей занимает позиция
 * @param targetIt  требуемый квалитет посадочных поверхностей; 0 — не оценивается
 * @param minHrc    требуемая твёрдость; 0 — не оценивается
 * @param heatTreat требуемые состояния термообработки; пусто — любое (оценка, не ворота)
 * @param aspects   на что влияет деталь в паспорте машины
 */
public record PartRequirement(String role, Set<String> kinds, Set<String> materials, int count, int targetIt,
                              int minHrc, Set<HeatTreatState> heatTreat, Set<Aspect> aspects) {
    /** Что деталь задаёт в паспорте машины (18 §18.8.3). */
    public enum Aspect {
        /** Посадка подшипника на вал: квалитет и твёрдость шейки → износ подшипника. */
        BEARING_SEAT,
        /** Подшипник: его дефекты → износ подшипника. */
        BEARING,
        /** Ротор, колесо, шкив: балансировка → начальная вибрация. */
        ROTOR,
        /** Станина, муфта: соосность. */
        ALIGNMENT,
        /** Уплотнение вала. */
        SEAL,
        /** Прокладка, фланец. */
        GASKET,
        /** Обмотка: класс и дефект изоляции, потери в пакете. */
        WINDING
    }

    public static final int MAX_COUNT = 16;

    public PartRequirement {
        Objects.requireNonNull(role, "role");
        if (role.isBlank()) throw new IllegalArgumentException("blank role");
        kinds = Set.copyOf(kinds);
        if (kinds.isEmpty()) throw new IllegalArgumentException("no kinds for " + role);
        materials = Set.copyOf(materials);
        if (count < 1 || count > MAX_COUNT) throw new IllegalArgumentException("count " + count + " for " + role);
        if (targetIt != 0 && (targetIt < IsoFits.IT_MIN || targetIt > IsoFits.IT_MAX)) {
            throw new IllegalArgumentException("targetIt " + targetIt + " for " + role);
        }
        if (minHrc < 0 || minHrc > 70) throw new IllegalArgumentException("minHrc " + minHrc + " for " + role);
        heatTreat = heatTreat.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(heatTreat));
        aspects = aspects.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(aspects));
    }

    public static PartRequirement of(String role, String kind, int count, Aspect... aspects) {
        return new PartRequirement(role, Set.of(kind), Set.of(), count, 0, 0, Set.of(),
                aspects.length == 0 ? Set.of() : EnumSet.of(aspects[0], aspects));
    }

    public PartRequirement withMaterials(String... ids) {
        return new PartRequirement(role, kinds, Set.of(ids), count, targetIt, minHrc, heatTreat, aspects);
    }

    public PartRequirement withFit(int it, int hrc, HeatTreatState... states) {
        return new PartRequirement(role, kinds, materials, count, it, hrc, Set.of(states), aspects);
    }

    public boolean has(Aspect a) {
        return aspects.contains(a);
    }
}

package dev.denfry.promptcritical.sim.grid;

import dev.denfry.promptcritical.sim.core.SimId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Электрическая часть плана изоляции LOTO (13 §13.1.4): аппараты, питающие объект работ. Обход графа сети от выводов
 * объекта: узлы выводов, сквозь трансформаторы, коннекторы и пролёты ВЛ — до ближайших коммутационных аппаратов
 * (выключатель, автомат, предохранитель, разъединитель); за аппаратом обход не идёт. Источник, достижимый без
 * аппарата (генератор, ветроагрегат, ГАЭС), и конденсаторная батарея — накопленная энергия — попадают в план
 * отдельно: их не отсечь замком на выключателе.
 */
public final class IsolationPlanner {
    /** Точка изоляции: аппарат и его положение сейчас. */
    public record Feed(SimId device, SwitchUnit.Kind kind, boolean closed) {
    }

    /** План: аппараты на границе зоны работ и источники внутри неё. */
    public record Plan(List<Feed> feeds, List<SimId> unswitchedSources, List<SimId> storedEnergy) {
        public boolean isolable() {
            return unswitchedSources.isEmpty();
        }
    }

    private IsolationPlanner() {
    }

    /** План для устройства сети {@code target}; устройство само может быть аппаратом — тогда точка изоляции он сам. */
    public static Plan electrical(GridSubsystem grid, SimId target) {
        GridUnit start = grid.unit(target);
        if (start == null) return new Plan(List.of(), List.of(), List.of());
        if (start instanceof SwitchUnit s) return new Plan(List.of(new Feed(s.id(), s.kind(), s.closed())), List.of(), List.of());
        Map<Long, List<GridUnit>> byNode = new HashMap<>();
        for (GridUnit u : grid.units())
            for (int t = 0; t < u.terminals(); t++)
                if (u.node(t) != GridUnit.NO_NODE) byNode.computeIfAbsent(u.node(t), k -> new ArrayList<>()).add(u);
        Map<SimId, List<SimId>> spans = new HashMap<>();
        for (LineUnit l : grid.lines()) {
            if (l.broken) continue;
            spans.computeIfAbsent(l.a, k -> new ArrayList<>()).add(l.b);
            spans.computeIfAbsent(l.b, k -> new ArrayList<>()).add(l.a);
        }
        Set<SimId> seen = new HashSet<>();
        Set<Feed> feeds = new LinkedHashSet<>();
        List<SimId> sources = new ArrayList<>(), stored = new ArrayList<>();
        ArrayDeque<GridUnit> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start.id());
        while (!queue.isEmpty()) {
            GridUnit u = queue.poll();
            List<GridUnit> next = new ArrayList<>();
            for (int t = 0; t < u.terminals(); t++) {
                List<GridUnit> at = byNode.get(u.node(t));
                if (at != null) next.addAll(at);
            }
            for (SimId other : spans.getOrDefault(u.id(), List.of())) {
                GridUnit o = grid.unit(other);
                if (o != null) next.add(o);
            }
            for (GridUnit n : next) {
                if (!seen.add(n.id())) continue;
                switch (n) {
                    case SwitchUnit s -> feeds.add(new Feed(s.id(), s.kind(), s.closed()));
                    case GeneratorUnit g -> sources.add(g.id());
                    case WindTurbineUnit w -> sources.add(w.id());
                    case PumpedStorageUnit p -> sources.add(p.id());
                    case CapacitorBankUnit c -> stored.add(c.id());
                    default -> queue.add(n);
                }
            }
        }
        return new Plan(List.copyOf(feeds), List.copyOf(sources), List.copyOf(stored));
    }
}

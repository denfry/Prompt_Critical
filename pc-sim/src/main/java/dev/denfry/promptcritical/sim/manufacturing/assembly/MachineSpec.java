package dev.denfry.promptcritical.sim.manufacturing.assembly;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Спецификация машины (18 §18.8.1, §18.15.1): позиции деталей и балансировка, которой стенд собирает ротор. Задаётся
 * шаблоном класса (насос, двигатель, вентилятор…) с переопределениями на машину.
 *
 * @param id           идентификатор спецификации (обычно id предмета машины)
 * @param output       id результата: блок-машина или подсборка-деталь
 * @param requirements позиции в порядке GUI, роли уникальны
 * @param runIn        нужна ли обкатка на валу (вращающиеся машины, 18 §18.6.11)
 */
public record MachineSpec(String id, String output, List<PartRequirement> requirements, boolean runIn) {
    /** Позиций в GUI стенда (сетка 3×3). */
    public static final int MAX_POSITIONS = 9;

    public MachineSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(output, "output");
        requirements = List.copyOf(requirements);
        if (requirements.isEmpty()) throw new IllegalArgumentException("spec " + id + " has no parts");
        if (requirements.size() > MAX_POSITIONS) {
            throw new IllegalArgumentException("spec " + id + ": " + requirements.size() + " > " + MAX_POSITIONS);
        }
        Set<String> roles = new HashSet<>();
        for (PartRequirement r : requirements) {
            if (!roles.add(r.role())) throw new IllegalArgumentException("spec " + id + ": duplicate role " + r.role());
        }
    }

    /**
     * Спецификация из шаблона класса: позиции шаблона, затем замена по роли ({@code replace}), удаление
     * ({@code remove}) и новые позиции в конце ({@code add}).
     */
    public static MachineSpec fromTemplate(String id, String output, MachineSpec template, List<PartRequirement> replace,
            Set<String> remove, List<PartRequirement> add, Boolean runIn) {
        Map<String, PartRequirement> byRole = new LinkedHashMap<>();
        for (PartRequirement r : template.requirements) byRole.put(r.role(), r);
        for (PartRequirement r : replace) {
            if (!byRole.containsKey(r.role())) {
                throw new IllegalArgumentException("spec " + id + ": replace of unknown role " + r.role());
            }
            byRole.put(r.role(), r);
        }
        for (String role : remove) {
            if (byRole.remove(role) == null) throw new IllegalArgumentException("spec " + id + ": remove of unknown role " + role);
        }
        List<PartRequirement> out = new ArrayList<>(byRole.values());
        out.addAll(add);
        return new MachineSpec(id, output, out, runIn == null ? template.runIn : runIn);
    }

    public PartRequirement requirement(String role) {
        for (PartRequirement r : requirements) if (r.role().equals(role)) return r;
        throw new IllegalArgumentException("spec " + id + " has no role " + role);
    }
}

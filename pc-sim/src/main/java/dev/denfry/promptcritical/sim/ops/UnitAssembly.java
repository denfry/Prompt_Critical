package dev.denfry.promptcritical.sim.ops;

import dev.denfry.promptcritical.sim.core.SimId;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Состав установки при сборке (13 §13.6.6) и состояние, которое создал игрок: направление вала Create, пористые
 * отливки из своего литейного цеха, катаракт регулятора.
 *
 * @param workingPressurePa p_раб сосуда; у машин без давления — 0
 * @param porousCastings    литые компоненты с пористостью (08 §8.9)
 */
public record UnitAssembly(SimId unit, UnitKind kind, Map<UnitComponent, SimId> components,
        double workingPressurePa, boolean reversedRotation, Set<UnitComponent> porousCastings,
        boolean dashpotFitted) {
    public UnitAssembly {
        if (unit == null || kind == null) throw new IllegalArgumentException("unit");
        for (UnitComponent c : components.keySet())
            if (!kind.components().contains(c)) throw new IllegalArgumentException(kind + " has no " + c);
        if (!(workingPressurePa >= 0)) throw new IllegalArgumentException("workingPressurePa");
        components = components.isEmpty() ? Map.of() : Map.copyOf(new EnumMap<>(components));
        porousCastings = porousCastings.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(porousCastings));
    }

    /** Котёл I0 с ПК, манометром и стеклом. */
    public static UnitAssembly boiler(SimId unit, double workingPressurePa, SimId shell, SimId psv, SimId gauge,
            SimId glass) {
        return new UnitAssembly(unit, UnitKind.FIRE_TUBE_BOILER, Map.of(UnitComponent.SHELL, shell,
                UnitComponent.RELIEF_VALVE, psv, UnitComponent.PRESSURE_GAUGE, gauge, UnitComponent.GAUGE_GLASS,
                glass), workingPressurePa, false, Set.of(), false);
    }

    /** Та же установка с заменённым компонентом. */
    public UnitAssembly withComponent(UnitComponent role, SimId id) {
        Map<UnitComponent, SimId> m = new EnumMap<>(UnitComponent.class);
        m.putAll(components);
        m.put(role, id);
        Set<UnitComponent> porous = EnumSet.noneOf(UnitComponent.class);
        porous.addAll(porousCastings);
        porous.remove(role);
        return new UnitAssembly(unit, kind, m, workingPressurePa, reversedRotation, porous, dashpotFitted);
    }
}

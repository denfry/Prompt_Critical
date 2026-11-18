package dev.denfry.promptcritical.mod.safety;

import net.neoforged.bus.api.IEventBus;

/**
 * Привязка промышленной безопасности 13 (то, чего нет в {@code mod.ops}): СИЗ §13.7 — материалы брони, компоненты,
 * действие на игрока.
 */
public final class SafetyBinding {
    private SafetyBinding() {
    }

    public static void register(IEventBus modBus) {
        PpeItem.MATERIALS.register(modBus);
        SafetyComponents.COMPONENTS.register(modBus);
        PpeService.register();
    }
}

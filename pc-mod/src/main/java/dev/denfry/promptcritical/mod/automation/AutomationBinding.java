package dev.denfry.promptcritical.mod.automation;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimConfigs;
import dev.denfry.promptcritical.sim.automation.AutomationConfig;
import dev.denfry.promptcritical.sim.automation.AutomationSubsystem;
import net.neoforged.bus.api.IEventBus;

/**
 * Привязка автоматики A1–A2 (этап 9 M3): реестры BE и компонентов, подсистема {@link AutomationSubsystem} в каждом
 * измерении (регистрируется после сети: блок питания читает напряжение шины прошлого тика).
 */
public final class AutomationBinding {
    private AutomationBinding() {
    }

    public static void register(IEventBus modBus) {
        AutomationBlockEntities.BLOCK_ENTITIES.register(modBus);
        AutomationComponents.COMPONENTS.register(modBus);
        SimBindings.registerSubsystem(level -> new AutomationSubsystem(AutomationSubsystem.worldConfig(
                SimConfigs.fromServerConfig().section(AutomationConfig.class, AutomationConfig.defaults()))));
    }
}

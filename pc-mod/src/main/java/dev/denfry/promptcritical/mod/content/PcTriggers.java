package dev.denfry.promptcritical.mod.content;

import dev.denfry.promptcritical.mod.PromptCritical;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Триггеры достижений (11 §11.11). */
public final class PcTriggers {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<CriterionTrigger<?>, SimEventTrigger> SIM_EVENT = TRIGGERS.register("sim_event", SimEventTrigger::new);

    private PcTriggers() {
    }
}

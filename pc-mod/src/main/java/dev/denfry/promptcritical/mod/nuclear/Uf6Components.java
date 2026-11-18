package dev.denfry.promptcritical.mod.nuclear;

import dev.denfry.promptcritical.mod.PromptCritical;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Компонент {@code uf6} — содержимое баллона гексафторида урана ({@link Uf6Charge}). */
public final class Uf6Components {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Uf6Charge>> UF6 =
            COMPONENTS.registerComponentType("uf6", b -> b.persistent(Uf6Charge.CODEC).networkSynchronized(Uf6Charge.STREAM));

    private Uf6Components() {
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}

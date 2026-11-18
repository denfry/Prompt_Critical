package dev.denfry.promptcritical.mod.nonferrous;

import dev.denfry.promptcritical.mod.PromptCritical;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Компоненты предметов цветной металлургии. {@code nickel_line} — концентрат и штейн медно-никелевой линии
 * (пентландит, 08 §8.8.4): без компонента это медная линия (халькопирит). Линия решает, куда предмет пойдёт: медный
 * штейн — в конвертер, никелевый — анодами в ванну рафинирования в режиме Ni.
 */
public final class NonferrousComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PromptCritical.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> NICKEL_LINE =
            COMPONENTS.registerComponentType("nickel_line", b -> b.persistent(com.mojang.serialization.Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL));

    private NonferrousComponents() {
    }

    public static boolean nickel(ItemStack s) {
        return Boolean.TRUE.equals(s.get(NICKEL_LINE.get()));
    }

    public static ItemStack nickel(ItemStack s, boolean nickel) {
        if (nickel) s.set(NICKEL_LINE.get(), true);
        return s;
    }
}

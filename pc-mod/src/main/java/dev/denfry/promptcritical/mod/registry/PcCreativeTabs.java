package dev.denfry.promptcritical.mod.registry;

import dev.denfry.promptcritical.mod.PromptCritical;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Вкладка творческого режима: блоки, затем предметы. */
public final class PcCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PromptCritical.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + PromptCritical.MOD_ID))
            .icon(() -> new ItemStack(PcBlocks.FIRE_TUBE_BOILER.get()))
            .displayItems((params, out) -> {
                PcBlocks.withItems().forEach(b -> out.accept(b.get()));
                PcItems.plainItems().forEach(i -> out.accept(i.get()));
            })
            .build());

    private PcCreativeTabs() {
    }
}

package dev.denfry.promptcritical.mod.registry;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.loot.AssayFunction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Типы лут-функций мода. */
public final class PcLootFunctions {
    public static final DeferredRegister<LootItemFunctionType<?>> FUNCTIONS =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, PromptCritical.MOD_ID);

    public static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<AssayFunction>> ASSAY =
            FUNCTIONS.register("assay", () -> new LootItemFunctionType<>(AssayFunction.CODEC));

    private PcLootFunctions() {
    }
}

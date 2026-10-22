package dev.denfry.promptcritical.mod.ops;

import dev.denfry.promptcritical.mod.foundry.FoundryComponents;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.metallurgy.CastAlloy;
import dev.denfry.promptcritical.sim.metallurgy.CastingDefect;
import dev.denfry.promptcritical.sim.metallurgy.CastingKind;
import dev.denfry.promptcritical.sim.metallurgy.CastingSpec;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Запчасти среза из отливки литейки (13 §13.6.7, 08 §8.9): обточенная бездефектная отливка рабочего колеса — колесо
 * насоса {@code pump_impeller}; обточенный бронзовый корпус подшипника — два вкладыша {@code bronze_bearing_shell}.
 * Пористая, с недоливом или отбелом отливка в запчасть не идёт.
 */
public final class CastingPartRecipe extends CustomRecipe {
    public CastingPartRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Nullable
    private static CastingSpec single(CraftingInput input) {
        CastingSpec found = null;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) continue;
            if (found != null || !s.is(PcItems.CASTING.get())) return null;
            found = s.get(FoundryComponents.CASTING_SPEC.get());
            if (found == null) return null;
        }
        return found;
    }

    /** Запчасть из отливки; пусто — не годится. */
    public static ItemStack part(@Nullable CastingSpec c) {
        if (c == null || c.defect() != CastingDefect.NONE || (c.machinedMask() & CastingSpec.TURNED) == 0) return ItemStack.EMPTY;
        if (c.kind() == CastingKind.IMPELLER) return new ItemStack(PcItems.PUMP_IMPELLER.get());
        if (c.kind() == CastingKind.BEARING_HOUSING && CastAlloy.BRONZE.id.equals(c.alloyId()))
            return new ItemStack(PcItems.BRONZE_BEARING_SHELL.get(), 2);
        return ItemStack.EMPTY;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !part(single(input)).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return part(single(input));
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return OpsBinding.CASTING_PART.get();
    }
}

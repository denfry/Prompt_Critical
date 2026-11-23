package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Угли и коксы мода горят в печах Minecraft (data map {@code neoforge:furnace_fuels}); порода — нет. */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FuelGameTests {
    private FuelGameTests() {
    }

    @GameTest(template = "gametest/empty_3x3x3")
    public static void modCoalsBurnInAVanillaFurnace(GameTestHelper h) {
        int coal = new ItemStack(Items.COAL).getBurnTime(RecipeType.SMELTING);
        var fuels = new net.minecraft.world.item.Item[] {PcItems.RAW_COAL.get(), PcItems.WASHED_COAL.get(), PcItems.COAL_MIDDLINGS.get(),
                PcItems.COAL_DUST.get(), PcItems.COKE.get(), PcItems.COKE_BREEZE.get(), PcItems.PITCH_COKE.get()};
        for (var i : fuels) {
            int t = new ItemStack(i).getBurnTime(RecipeType.SMELTING);
            h.assertTrue(t >= coal / 2 && t <= coal * 1.1, i + " burns about like coal: " + t + " ticks");
        }
        h.assertTrue(new ItemStack(PcItems.COAL_REFUSE.get()).getBurnTime(RecipeType.SMELTING) == 0, "coal refuse is rock, not fuel");
        h.succeed();
    }
}

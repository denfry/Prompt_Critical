package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.environment.ActivityData;
import dev.denfry.promptcritical.mod.isotopes.IsotopeCraftRecipe;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Am-241 из выдержанного PuO₂ (12 §12.2.5): за пять лет выдержки в партии с Pu-241 врастает америций; ионит на
 * верстаке выделяет его в оксид, PuO₂ возвращается без америция, ионит — отработанным; оксид с железом — закрытый
 * источник.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AmericiumGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    /** Пять игровых лет в тиках (тик — 3,6 с игрового времени). */
    private static final long FIVE_YEARS_TICKS = (long) (5 * 365.25 * 86400 / ActivityData.GAME_S_PER_TICK);

    private AmericiumGameTests() {
    }

    @GameTest(template = LARGE, timeoutTicks = 100)
    public static void agedPlutoniumGivesAmericiumOnTheResin(GameTestHelper h) {
        long now = h.getLevel().getServer().overworld().getGameTime();
        // 100 г Pu-241 (1 кг Pu, 10 % Pu-241), выделен пять лет назад
        double pu241Bq = Emitter.PU241.lambda() * 100 / 241.06 * 6.02214076e23;
        ItemStack puo2 = new ItemStack(PcItems.PUO2_POWDER.get());
        puo2.set(ActivityData.ACTIVITY.get(), new ActivityData(now - FIVE_YEARS_TICKS, 1 / 0.882, 11.5, Map.of(Emitter.PU241.name(), pu241Bq)));
        CraftingInput in = CraftingInput.of(2, 1, List.of(puo2, new ItemStack(PcItems.ION_EXCHANGE_RESIN_SBA.get())));
        IsotopeCraftRecipe recipe = new IsotopeCraftRecipe(CraftingBookCategory.MISC);
        h.assertTrue(recipe.matches(in, h.getLevel()), "aged PuO2 with resin is a recipe");
        ItemStack am = recipe.assemble(in, h.getLevel().registryAccess());
        h.assertTrue(am.is(PcItems.AM241_OXIDE.get()), "americium oxide out");
        double grams = am.get(ActivityData.ACTIVITY.get()).bq().get(Emitter.AM241.name()) / Emitter.AM241.lambda() / 6.02214076e23 * 241.06;
        h.assertTrue(Math.abs(grams - 21.4) < 1, "21.4 g of Am-241 after five years (T12.11): " + grams);
        var rest = recipe.getRemainingItems(in);
        h.assertTrue(rest.get(1).is(PcItems.SPENT_ION_EXCHANGE_RESIN.get()), "the resin comes back spent");
        h.assertTrue(rest.get(0).is(PcItems.PUO2_POWDER.get())
                && !rest.get(0).get(ActivityData.ACTIVITY.get()).bq().containsKey(Emitter.AM241.name()), "PuO2 comes back without americium");
        CraftingInput src = CraftingInput.of(2, 1, List.of(am, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT)));
        h.assertTrue(recipe.assemble(src, h.getLevel().registryAccess()).is(PcItems.SEALED_SOURCE.get()), "sealed Am-241 source");
        h.succeed();
    }
}

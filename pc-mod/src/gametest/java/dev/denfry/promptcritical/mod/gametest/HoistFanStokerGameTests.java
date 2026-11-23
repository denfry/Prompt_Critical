package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.content.HandbookService;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Паровая подъёмная машина, вентилятор Гибаля, забрасыватели ланкаширского котла и тигельная горновая печь реального
 * масштаба: инструкции по эксплуатации и чертежи на верстаке.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HoistFanStokerGameTests {
    private HoistFanStokerGameTests() {}

    /** Машины реального масштаба открывают ту же инструкцию, что их малые двойники (11 §11.1.2). */
    @GameTest(template = "gametest/empty_12x12x12", timeoutTicks = 20)
    public static void realScaleMachinesOpenTheSameProcedures(GameTestHelper h) {
        var p = HandbookService.PROCEDURES;
        h.assertTrue(p.get(PcBlocks.WINDING_ENGINE.getId()) != null
                && p.get(PcBlocks.WINDING_ENGINE.getId()).equals(p.get(PcBlocks.MINE_HOIST.getId())),
                "the winding engine opens the hoist procedure");
        h.assertTrue(p.get(PcBlocks.GUIBAL_FAN.getId()) != null
                && p.get(PcBlocks.GUIBAL_FAN.getId()).equals(p.get(PcBlocks.MAIN_MINE_FAN.getId())),
                "the Guibal fan opens the ventilation procedure");
        h.succeed();
    }

    /** У каждой из четырёх машин есть рецепт чертежа: сами они строятся на стройплощадке. */
    @GameTest(template = "gametest/empty_12x12x12", timeoutTicks = 20)
    public static void realScaleMachinesHaveDrawingRecipes(GameTestHelper h) {
        var recipes = h.getLevel().getRecipeManager();
        for (String id : new String[] {"mine/winding_engine_drawing", "mine/guibal_fan_drawing",
                "boiler/lancashire_stoker_drawing", "foundry/crucible_furnace_real_drawing"})
            h.assertTrue(recipes.byKey(PromptCritical.id(id)).isPresent(), "drawing recipe " + id);
        h.succeed();
    }
}

package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Теги контрапций Create (16 §16.2.4): машины и активные зоны не едут, ёмкости едут, приборы пульта — brittle. */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ContraptionTagGameTests {
    private static final String SMALL = "gametest/empty_3x3x3";
    private static final TagKey<Block> NON_MOVABLE = create("non_movable");
    private static final TagKey<Block> BRITTLE = create("brittle");

    private ContraptionTagGameTests() {
    }

    @GameTest(template = SMALL)
    public static void contraptionTagsSplitMachinesFromVessels(GameTestHelper h) {
        for (String id : new String[] {"pwr_reactor", "reactor_controller", "fuel_rack", "centrifuge_cascade", "multiblock_part",
                "steam_drum"})
            h.assertTrue(block(id).defaultBlockState().is(NON_MOVABLE), id + " must be create:non_movable");
        for (String id : new String[] {"oil_drum", "cryogenic_tank", "gas_storage_bullet"})
            h.assertFalse(block(id).defaultBlockState().is(NON_MOVABLE), id + " must stay movable");
        h.assertTrue(block("annunciator_panel").defaultBlockState().is(BRITTLE), "annunciator_panel must be create:brittle");
        for (Block b : BuiltInRegistries.BLOCK) {
            if (!PromptCritical.MOD_ID.equals(BuiltInRegistries.BLOCK.getKey(b).getNamespace())) continue;
            h.assertFalse(b.defaultBlockState().is(NON_MOVABLE) && b.defaultBlockState().is(BRITTLE),
                    BuiltInRegistries.BLOCK.getKey(b) + " is in both non_movable and brittle");
        }
        h.succeed();
    }

    private static Block block(String path) {
        return BuiltInRegistries.BLOCK.get(PromptCritical.id(path));
    }

    private static TagKey<Block> create(String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("create", path));
    }
}

package dev.denfry.promptcritical.mod.data;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SingleItemRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * Рецепты раздела 11: справочник (потерянный — из книги и угля), пюпитр, декор I0 (11 §11.6.2) — дёшево, из ванильных
 * материалов; плиты, ступени и стенки — на верстаке и в камнерезе.
 */
public final class ContentRecipes extends RecipeProvider {
    public ContentRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    public static DataProvider provider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        DataProvider inner = new ContentRecipes(output, registries);
        return new DataProvider() {
            @Override
            public CompletableFuture<?> run(CachedOutput cache) {
                return inner.run(cache);
            }

            @Override
            public String getName() {
                return "Recipes: handbook and decor";
            }
        };
    }

    private static String id(String name) {
        return PromptCritical.id("content/" + name).toString();
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, PcItems.ENGINEERING_HANDBOOK.get())
                .requires(Items.BOOK).requires(Items.COAL)
                .unlockedBy("has_book", has(Items.BOOK)).save(out, id("engineering_handbook"));
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, PcBlocks.DOCUMENT_LECTERN.get())
                .pattern("NNN").pattern(" I ").pattern("III").define('N', Items.IRON_NUGGET).define('I', Items.IRON_INGOT)
                .unlockedBy("has_procedure", has(PcItems.OPERATING_PROCEDURE.get())).save(out, id("document_lectern"));

        square(out, PcBlocks.TILE_WHITE, Items.WHITE_TERRACOTTA);
        square(out, PcBlocks.TILE_BLUE, Items.LIGHT_BLUE_TERRACOTTA);
        square(out, PcBlocks.TILE_GREEN, Items.LIME_TERRACOTTA);
        square(out, PcBlocks.PORCELAIN_TILE, Items.SMOOTH_STONE);
        square(out, PcBlocks.PORCELAIN_TILE_BEIGE, Items.SMOOTH_SANDSTONE);
        square(out, PcBlocks.PAINTED_CONCRETE, Items.LIGHT_GRAY_CONCRETE);
        square(out, PcBlocks.PAINTED_CONCRETE_YELLOW, Items.YELLOW_CONCRETE);
        square(out, PcBlocks.PAINTED_CONCRETE_RED, Items.RED_CONCRETE);
        dyed(out, PcBlocks.PAINTED_CONCRETE_TWO_TONE, Items.WHITE_CONCRETE, Items.GREEN_DYE);
        dyed(out, PcBlocks.PAINTED_CONCRETE_TWO_TONE_BLUE, Items.WHITE_CONCRETE, Items.BLUE_DYE);
        dyed(out, PcBlocks.PAINTED_CONCRETE_TWO_TONE_BEIGE, Items.WHITE_CONCRETE, Items.BROWN_DYE);
        square(out, PcBlocks.GLASS_BLOCK, Items.GLASS);
        dyed(out, PcBlocks.GLASS_BLOCK_FROSTED, PcBlocks.GLASS_BLOCK.get(), Items.WHITE_DYE);
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, PcBlocks.CORRUGATED_STEEL.get(), 8)
                .pattern("I I").pattern("III").define('I', Items.IRON_INGOT)
                .unlockedBy("has_iron", has(Items.IRON_INGOT)).save(out, id("corrugated_steel"));
        dyed(out, PcBlocks.CORRUGATED_STEEL_BLUE, PcBlocks.CORRUGATED_STEEL.get(), Items.BLUE_DYE);
        dyed(out, PcBlocks.CORRUGATED_STEEL_GREEN, PcBlocks.CORRUGATED_STEEL.get(), Items.GREEN_DYE);
        dyed(out, PcBlocks.CORRUGATED_STEEL_RED, PcBlocks.CORRUGATED_STEEL.get(), Items.RED_DYE);
        dyed(out, PcBlocks.CORRUGATED_STEEL_WHITE, PcBlocks.CORRUGATED_STEEL.get(), Items.WHITE_DYE);
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, PcBlocks.GRATING.get(), 6)
                .pattern("BBB").define('B', Items.IRON_BARS)
                .unlockedBy("has_bars", has(Items.IRON_BARS)).save(out, id("grating"));
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, PcBlocks.GRATING_STEP.get(), 4)
                .pattern("G  ").pattern("GG ").pattern("GGG").define('G', PcBlocks.GRATING.get())
                .unlockedBy("has_grating", has(PcBlocks.GRATING.get())).save(out, id("grating_step"));
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, PcBlocks.HANDRAIL_GALVANIZED.get(), 6)
                .pattern("III").pattern("I I").define('I', Items.IRON_INGOT)
                .unlockedBy("has_iron", has(Items.IRON_INGOT)).save(out, id("handrail_galvanized"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, PcBlocks.HANDRAIL.get())
                .requires(PcBlocks.HANDRAIL_GALVANIZED.get()).requires(Items.YELLOW_DYE)
                .unlockedBy("has_handrail", has(PcBlocks.HANDRAIL_GALVANIZED.get())).save(out, id("handrail"));
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, PcBlocks.CAGE_LADDER.get(), 3)
                .pattern("B B").pattern("BLB").pattern("B B").define('B', Items.IRON_BARS).define('L', Items.LADDER)
                .unlockedBy("has_ladder", has(Items.LADDER)).save(out, id("cage_ladder"));
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, PcBlocks.INDUSTRIAL_LIGHT.get(), 2)
                .pattern(" N ").pattern("IGI").define('N', Items.IRON_NUGGET).define('I', Items.IRON_INGOT)
                .define('G', Items.GLOWSTONE)
                .unlockedBy("has_glowstone", has(Items.GLOWSTONE)).save(out, id("industrial_light"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, PcBlocks.INDUSTRIAL_LIGHT_STREET.get())
                .requires(PcBlocks.INDUSTRIAL_LIGHT.get()).requires(Items.IRON_BARS)
                .unlockedBy("has_light", has(PcBlocks.INDUSTRIAL_LIGHT.get())).save(out, id("industrial_light_street"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, PcBlocks.INDUSTRIAL_LIGHT_FLOODLIGHT.get())
                .requires(PcBlocks.INDUSTRIAL_LIGHT.get()).requires(Items.IRON_INGOT).requires(Items.GLASS_PANE)
                .unlockedBy("has_light", has(PcBlocks.INDUSTRIAL_LIGHT.get())).save(out, id("industrial_light_floodlight"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, PcBlocks.INDUSTRIAL_LIGHT_EXIT.get())
                .requires(PcBlocks.INDUSTRIAL_LIGHT.get()).requires(Items.GREEN_DYE)
                .unlockedBy("has_light", has(PcBlocks.INDUSTRIAL_LIGHT.get())).save(out, id("industrial_light_exit"));
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, PcBlocks.LAMP_FLUORESCENT.get(), 2)
                .pattern("III").pattern("GLG").define('I', Items.IRON_NUGGET).define('G', Items.GLASS_PANE)
                .define('L', Items.GLOWSTONE_DUST)
                .unlockedBy("has_glowstone", has(Items.GLOWSTONE_DUST)).save(out, id("lamp_fluorescent"));
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, PcBlocks.LAMP_BULKHEAD.get(), 2)
                .pattern(" G ").pattern("NLN").define('N', Items.IRON_NUGGET).define('G', Items.GLASS_PANE)
                .define('L', Items.GLOWSTONE_DUST)
                .unlockedBy("has_glowstone", has(Items.GLOWSTONE_DUST)).save(out, id("lamp_bulkhead"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, PcBlocks.LAMP_EXPLOSION_PROOF.get())
                .requires(PcBlocks.LAMP_BULKHEAD.get()).requires(Items.IRON_BARS).requires(Items.IRON_INGOT)
                .unlockedBy("has_light", has(PcBlocks.LAMP_BULKHEAD.get())).save(out, id("lamp_explosion_proof"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, PcBlocks.LAMP_EMERGENCY.get())
                .requires(PcBlocks.LAMP_BULKHEAD.get()).requires(Items.REDSTONE).requires(Items.COPPER_INGOT)
                .unlockedBy("has_light", has(PcBlocks.LAMP_BULKHEAD.get())).save(out, id("lamp_emergency"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, PcBlocks.LAMP_HIGH_BAY.get())
                .requires(PcBlocks.INDUSTRIAL_LIGHT.get()).requires(Items.IRON_INGOT).requires(Items.CHAIN)
                .unlockedBy("has_light", has(PcBlocks.INDUSTRIAL_LIGHT.get())).save(out, id("lamp_high_bay"));
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, PcBlocks.WARNING_BEACON.get(), 2)
                .pattern(" G ").pattern("NLN").define('N', Items.IRON_NUGGET).define('G', Items.ORANGE_STAINED_GLASS)
                .define('L', Items.GLOWSTONE_DUST)
                .unlockedBy("has_glowstone", has(Items.GLOWSTONE_DUST)).save(out, id("warning_beacon"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, PcBlocks.ROOM_SIGN.get(), 2)
                .requires(Items.IRON_NUGGET).requires(Items.PAPER).requires(Items.BLACK_DYE)
                .unlockedBy("has_paper", has(Items.PAPER)).save(out, id("room_sign"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, PcBlocks.ROOM_SIGN_EVAC.get())
                .requires(PcBlocks.ROOM_SIGN.get()).requires(Items.GREEN_DYE)
                .unlockedBy("has_sign", has(PcBlocks.ROOM_SIGN.get())).save(out, id("room_sign_evac"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, PcBlocks.WALL_CLOCK.get())
                .requires(Items.CLOCK).requires(Items.IRON_INGOT)
                .unlockedBy("has_clock", has(Items.CLOCK)).save(out, id("wall_clock"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, PcBlocks.SAFETY_POSTER.get())
                .requires(Items.PAPER).requires(Items.RED_DYE).requires(Items.BLACK_DYE)
                .unlockedBy("has_paper", has(Items.PAPER)).save(out, id("safety_poster"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, PcBlocks.SAFETY_POSTER_LARGE.get())
                .requires(PcBlocks.SAFETY_POSTER.get(), 2)
                .unlockedBy("has_poster", has(PcBlocks.SAFETY_POSTER.get())).save(out, id("safety_poster_large"));
        SingleItemRecipeBuilder.stonecutting(Ingredient.of(Items.STONE), RecipeCategory.BUILDING_BLOCKS, PcBlocks.CURB.get(), 2)
                .unlockedBy("has_stone", has(Items.STONE)).save(out, id("curb_stonecutting"));
        // декор I1
        square(out, PcBlocks.LINOLEUM_CONTROL, Items.GRAY_CARPET);
        dyed(out, PcBlocks.LINOLEUM_CONTROL_BEIGE, PcBlocks.LINOLEUM_CONTROL.get(), Items.YELLOW_DYE);
        dyed(out, PcBlocks.LINOLEUM_CONTROL_PARQUET, PcBlocks.LINOLEUM_CONTROL.get(), Items.BROWN_DYE);
        square(out, PcBlocks.CEILING_TILE, Items.WHITE_CARPET);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, PcBlocks.CEILING_LIGHT_PANEL.get())
                .requires(PcBlocks.CEILING_TILE.get()).requires(Items.GLOWSTONE_DUST)
                .unlockedBy("has_ceiling", has(PcBlocks.CEILING_TILE.get())).save(out, id("ceiling_light_panel"));
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, PcBlocks.CABLE_TRAY.get(), 6)
                .pattern("N N").pattern("NNN").define('N', Items.IRON_NUGGET)
                .unlockedBy("has_nugget", has(Items.IRON_NUGGET)).save(out, id("cable_tray"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, PcBlocks.CABLE_TRAY_CABLES.get())
                .requires(PcBlocks.CABLE_TRAY.get()).requires(PcItems.COPPER_WIRE.get())
                .unlockedBy("has_tray", has(PcBlocks.CABLE_TRAY.get())).save(out, id("cable_tray_cables"));
        for (DeferredBlock<? extends Block> t : List.of(PcBlocks.CABLE_TRAY_CORNER, PcBlocks.CABLE_TRAY_TEE, PcBlocks.CABLE_TRAY_VERTICAL))
            SingleItemRecipeBuilder.stonecutting(Ingredient.of(PcBlocks.CABLE_TRAY_CABLES.get()), RecipeCategory.DECORATIONS, t.get())
                    .unlockedBy("has_tray", has(PcBlocks.CABLE_TRAY_CABLES.get())).save(out, id(t.getId().getPath() + "_stonecutting"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, PcBlocks.ROOM_SIGN_ELECTRICAL.get())
                .requires(PcBlocks.ROOM_SIGN.get()).requires(Items.YELLOW_DYE)
                .unlockedBy("has_sign", has(PcBlocks.ROOM_SIGN.get())).save(out, id("room_sign_electrical"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, PcBlocks.ROOM_SIGN_PUMP.get())
                .requires(PcBlocks.ROOM_SIGN.get()).requires(Items.BUCKET)
                .unlockedBy("has_sign", has(PcBlocks.ROOM_SIGN.get())).save(out, id("room_sign_pump"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, PcBlocks.ROOM_SIGN_CONTROL.get())
                .requires(PcBlocks.ROOM_SIGN.get()).requires(Items.REDSTONE)
                .unlockedBy("has_sign", has(PcBlocks.ROOM_SIGN.get())).save(out, id("room_sign_control"));
        // декор завода: электрооборудование и интерьер цеха
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, PcBlocks.ELECTRICAL_CABINET.get(), 1).pattern("III").pattern("IWI").pattern("III").define('I', Items.IRON_INGOT).define('W', PcItems.COPPER_WIRE.get())
                .unlockedBy("has_item", has(PcItems.COPPER_WIRE.get())).save(out, id("electrical_cabinet"));
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, PcBlocks.JUNCTION_BOX.get(), 4).pattern(" N ").pattern("NWN").pattern(" N ").define('N', Items.IRON_NUGGET).define('W', PcItems.COPPER_WIRE.get())
                .unlockedBy("has_item", has(PcItems.COPPER_WIRE.get())).save(out, id("junction_box"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, PcBlocks.WALL_SOCKET.get(), 2).requires(PcBlocks.JUNCTION_BOX.get()).requires(Items.RED_DYE).requires(Items.BLUE_DYE)
                .unlockedBy("has_item", has(PcBlocks.JUNCTION_BOX.get())).save(out, id("wall_socket"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, PcBlocks.KNIFE_SWITCH.get(), 1).requires(Items.LEVER).requires(Items.COPPER_INGOT).requires(Items.BLACKSTONE)
                .unlockedBy("has_item", has(Items.LEVER)).save(out, id("knife_switch"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, PcBlocks.VOLTAGE_WARNING_SIGN.get(), 2).requires(Items.IRON_NUGGET).requires(Items.PAPER).requires(Items.RED_DYE)
                .unlockedBy("has_item", has(Items.PAPER)).save(out, id("voltage_warning_sign"));
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, PcBlocks.CABLE_DRUM.get(), 1).pattern("P P").pattern("PWP").pattern("P P").define('P', Items.SPRUCE_PLANKS).define('W', PcItems.COPPER_WIRE.get())
                .unlockedBy("has_item", has(PcItems.COPPER_WIRE.get())).save(out, id("cable_drum"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, PcBlocks.DIELECTRIC_MAT.get(), 4).requires(Items.BLACK_CARPET).requires(Items.DRIED_KELP)
                .unlockedBy("has_item", has(Items.BLACK_CARPET)).save(out, id("dielectric_mat"));
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, PcBlocks.CONDUIT.get(), 8).pattern("III").define('I', Items.IRON_NUGGET)
                .unlockedBy("has_item", has(Items.IRON_NUGGET)).save(out, id("conduit"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, PcBlocks.CONDUIT_VERTICAL.get(), 1).requires(PcBlocks.CONDUIT.get())
                .unlockedBy("has_item", has(PcBlocks.CONDUIT.get())).save(out, id("conduit_vertical"));
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, PcBlocks.WORKBENCH.get(), 1).pattern("III").pattern("NCN").define('I', Items.IRON_INGOT).define('N', Items.IRON_NUGGET).define('C', Items.CRAFTING_TABLE)
                .unlockedBy("has_item", has(Items.CRAFTING_TABLE)).save(out, id("workbench"));
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, PcBlocks.LOCKER.get(), 1).pattern("II").pattern("II").pattern("II").define('I', Items.IRON_INGOT)
                .unlockedBy("has_item", has(Items.IRON_INGOT)).save(out, id("locker"));
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, PcBlocks.SHELVING.get(), 2).pattern("N N").pattern("SSS").pattern("N N").define('N', Items.IRON_NUGGET).define('S', Items.SMOOTH_STONE_SLAB)
                .unlockedBy("has_item", has(Items.IRON_NUGGET)).save(out, id("shelving"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, PcBlocks.FIRE_EXTINGUISHER_WALL.get(), 1).requires(Items.IRON_INGOT).requires(Items.RED_DYE).requires(Items.SNOWBALL)
                .unlockedBy("has_item", has(Items.RED_DYE)).save(out, id("fire_extinguisher_wall"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, PcBlocks.FIRE_HOSE_CABINET.get(), 1).requires(Items.IRON_INGOT).requires(Items.RED_DYE).requires(Items.GLASS_PANE).requires(Items.STRING)
                .unlockedBy("has_item", has(Items.RED_DYE)).save(out, id("fire_hose_cabinet"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, PcBlocks.FIRST_AID_BOX.get(), 1).requires(Items.IRON_NUGGET).requires(Items.WHITE_DYE).requires(Items.RED_DYE).requires(Items.PAPER)
                .unlockedBy("has_item", has(Items.PAPER)).save(out, id("first_aid_box"));
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, PcBlocks.OIL_DRUM.get(), 2).pattern("I I").pattern("I I").pattern("III").define('I', Items.IRON_INGOT)
                .unlockedBy("has_item", has(Items.IRON_INGOT)).save(out, id("oil_drum"));
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, PcBlocks.GAS_CYLINDERS.get(), 1).pattern("III").pattern("B B").define('I', Items.IRON_INGOT).define('B', Items.IRON_BARS)
                .unlockedBy("has_item", has(Items.IRON_BARS)).save(out, id("gas_cylinders"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, PcBlocks.COLUMN_I_BEAM.get(), 2).requires(PcItems.STEEL_BEAM.get())
                .unlockedBy("has_item", has(PcItems.STEEL_BEAM.get())).save(out, id("column_i_beam"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, PcBlocks.FLOOR_MARKING.get(), 8).requires(Items.YELLOW_DYE).requires(Items.STONE)
                .unlockedBy("has_item", has(Items.YELLOW_DYE)).save(out, id("floor_marking"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, PcBlocks.FLOOR_MARKING_HAZARD.get(), 8).requires(Items.YELLOW_DYE).requires(Items.BLACK_DYE).requires(Items.STONE)
                .unlockedBy("has_item", has(Items.YELLOW_DYE)).save(out, id("floor_marking_hazard"));


        PcBlocks.decorForms().forEach((db, form) -> {
            Block base = PcBlocks.withItems().stream().filter(b -> b.getId().getPath().equals(form.base())).findFirst()
                    .orElseThrow().get();
            String name = db.getId().getPath();
            switch (form.kind()) {
                case "slab" -> slab(out, RecipeCategory.BUILDING_BLOCKS, db.get(), base);
                case "stairs" -> stairBuilder(db.get(), Ingredient.of(base)).unlockedBy("has_base", has(base)).save(out, id(name));
                default -> wall(out, RecipeCategory.DECORATIONS, db.get(), base);
            }
            SingleItemRecipeBuilder.stonecutting(Ingredient.of(base), RecipeCategory.BUILDING_BLOCKS, db.get(),
                    form.kind().equals("slab") ? 2 : 1).unlockedBy("has_base", has(base)).save(out, id(name + "_stonecutting"));
        });
    }

    /** 2×2 материала → 4 блока декора. */
    private static void square(RecipeOutput out, DeferredBlock<? extends Block> result, ItemLike material) {
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, result.get(), 4).pattern("MM").pattern("MM")
                .define('M', material).unlockedBy("has_material", has(material)).save(out, id(result.getId().getPath()));
    }

    /** 8 блоков вокруг красителя → 8 окрашенных. */
    private static void dyed(RecipeOutput out, DeferredBlock<? extends Block> result, ItemLike base, ItemLike dye) {
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, result.get(), 8).pattern("BBB").pattern("BDB").pattern("BBB")
                .define('B', base).define('D', dye).unlockedBy("has_base", has(base)).save(out, id(result.getId().getPath()));
    }
}

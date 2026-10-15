package dev.denfry.promptcritical.mod.data;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.foundry.FoundryTags;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.registry.PcTags;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/** Теги блоков и предметов (05 §5.3.7): инструменты, топология, защита от разрушения, общие теги {@code c:}. */
public final class PcTagProviders {
    /** Металл руды для {@code c:ores/<metal>} и {@code c:raw_materials/<metal>}. */
    private static final Map<String, String> METAL = Map.ofEntries(
            Map.entry("magnetite_ore", "iron"), Map.entry("hematite_ore", "iron"), Map.entry("pyrolusite_ore", "manganese"),
            Map.entry("pentlandite_ore", "nickel"), Map.entry("chalcopyrite_ore", "copper"), Map.entry("sperrylite_ore", "platinum"),
            Map.entry("molybdenite_ore", "molybdenum"), Map.entry("sphalerite_ore", "zinc"), Map.entry("chromite_ore", "chromium"),
            Map.entry("cassiterite_ore", "tin"), Map.entry("bauxite_ore", "aluminum"), Map.entry("coal_seam", "coal"));
    private static final Set<String> IRON_TOOL = Set.of("pentlandite_ore", "sperrylite_ore", "molybdenite_ore", "chromite_ore");
    private static final Set<String> AXE = Set.of("mine_support", "ventilation_door", "explosion_barrier", "document_lectern");
    private static final Set<String> SHOVEL = Set.of("fireclay", "sand_mold");

    private PcTagProviders() {
    }

    public static final class BlockTagProvider extends BlockTagsProvider {
        public BlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper efh) {
            super(output, lookup, PromptCritical.MOD_ID, efh);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            for (DeferredBlock<? extends Block> db : PcBlocks.withItems()) {
                String id = db.getId().getPath();
                Block b = db.get();
                TagKey<Block> tool = AXE.contains(id) ? BlockTags.MINEABLE_WITH_AXE
                        : SHOVEL.contains(id) ? BlockTags.MINEABLE_WITH_SHOVEL : BlockTags.MINEABLE_WITH_PICKAXE;
                tag(tool).add(b);
                if (PcBlocks.oreDrops().containsKey(db)) {
                    tag(IRON_TOOL.contains(id) ? BlockTags.NEEDS_IRON_TOOL : BlockTags.NEEDS_STONE_TOOL).add(b);
                    tag(PcTags.Blocks.ORES).add(b);
                    tag(PcTags.Blocks.common("ores/" + METAL.get(id))).add(b);
                }
            }
            for (DeferredBlock<PcBlock> db : PcBlocks.selfAssayed()) {
                tag(BlockTags.NEEDS_STONE_TOOL).add(db.get());
                tag(PcTags.Blocks.ORES).add(db.get());
            }
            // 08 §8.2.6: открытое пламя в выработке — источник воспламенения
            tag(PcTags.Blocks.OPEN_FLAME).addTag(BlockTags.FIRE).addTag(BlockTags.CAMPFIRES).addTag(BlockTags.CANDLES)
                    .add(Blocks.TORCH, Blocks.WALL_TORCH, Blocks.SOUL_TORCH, Blocks.SOUL_WALL_TORCH, Blocks.LANTERN,
                            Blocks.SOUL_LANTERN, Blocks.LAVA, Blocks.MAGMA_BLOCK);
            // 08 §8.2.2: кровля — устойчивая (песчаник) и неустойчивая (углистый сланец, рыхлые); прочее — средняя
            tag(PcTags.Blocks.ROOF_STABLE).add(Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE, Blocks.DEEPSLATE,
                    Blocks.SANDSTONE, Blocks.RED_SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE);
            tag(PcTags.Blocks.ROOF_WEAK).add(PcBlocks.COAL_SEAM.get(), PcBlocks.FIRECLAY.get(), Blocks.GRAVEL, Blocks.DIRT,
                    Blocks.COARSE_DIRT, Blocks.CLAY, Blocks.SAND, Blocks.RED_SAND, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE);
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(PcBlocks.MULTIBLOCK_PART.get(), PcBlocks.KINETIC_PORT.get());
            // формы декора (11 §11.6.2)
            PcBlocks.decorForms().forEach((db, form) -> tag(switch (form.kind()) {
                case "slab" -> BlockTags.SLABS;
                case "stairs" -> BlockTags.STAIRS;
                default -> BlockTags.WALLS;
            }).add(db.get()));
            tag(BlockTags.CLIMBABLE).add(PcBlocks.CAGE_LADDER.get());
            tag(PcTags.Blocks.TOPOLOGY_RELEVANT).add(PcBlocks.topologyRelevant().toArray(Block[]::new));
            tag(PcTags.Blocks.DAMAGE_IMMUNE).add(Blocks.BEDROCK, Blocks.BARRIER, Blocks.LIGHT, Blocks.END_PORTAL,
                    Blocks.END_PORTAL_FRAME, Blocks.END_GATEWAY, Blocks.NETHER_PORTAL, Blocks.COMMAND_BLOCK,
                    Blocks.CHAIN_COMMAND_BLOCK, Blocks.REPEATING_COMMAND_BLOCK, Blocks.STRUCTURE_BLOCK, Blocks.JIGSAW,
                    Blocks.REINFORCED_DEEPSLATE);
        }
    }

    public static final class ItemTagProvider extends ItemTagsProvider {
        public ItemTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup,
                CompletableFuture<TagLookup<Block>> blockTags, ExistingFileHelper efh) {
            super(output, lookup, blockTags, PromptCritical.MOD_ID, efh);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            copy(PcTags.Blocks.ORES, PcTags.Items.ORES);
            copy(BlockTags.SLABS, net.minecraft.tags.ItemTags.SLABS);
            copy(BlockTags.STAIRS, net.minecraft.tags.ItemTags.STAIRS);
            copy(BlockTags.WALLS, net.minecraft.tags.ItemTags.WALLS);
            FoundryTags.addItemTags(this::tag);
            for (String metal : Set.copyOf(METAL.values())) {
                copy(PcTags.Blocks.common("ores/" + metal), PcTags.Items.common("ores/" + metal));
            }
            List<DeferredItem<Item>> raws = List.of(PcItems.RAW_MAGNETITE, PcItems.RAW_HEMATITE, PcItems.RAW_PYROLUSITE,
                    PcItems.RAW_PENTLANDITE, PcItems.RAW_CHALCOPYRITE, PcItems.RAW_SPERRYLITE, PcItems.RAW_MOLYBDENITE,
                    PcItems.RAW_SPHALERITE, PcItems.RAW_CHROMITE, PcItems.RAW_CASSITERITE, PcItems.RAW_BAUXITE);
            for (DeferredItem<Item> raw : raws) {
                String ore = raw.getId().getPath().substring("raw_".length()) + "_ore";
                tag(PcTags.Items.RAW_MATERIALS).add(raw.get());
                tag(PcTags.Items.common("raw_materials/" + METAL.get(ore))).add(raw.get());
            }
        }
    }
}

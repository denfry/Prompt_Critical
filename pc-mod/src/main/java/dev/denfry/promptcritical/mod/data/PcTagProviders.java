package dev.denfry.promptcritical.mod.data;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.compat.CommonTags;
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
import net.minecraft.data.tags.FluidTagsProvider;
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
            Map.entry("cassiterite_ore", "tin"), Map.entry("bauxite_ore", "aluminum"), Map.entry("coal_seam", "coal"),
            Map.entry("uraninite_ore", "uranium"), Map.entry("deepslate_uraninite_ore", "uranium"));
    private static final Set<String> IRON_TOOL = Set.of("pentlandite_ore", "sperrylite_ore", "molybdenite_ore", "chromite_ore",
            "uraninite_ore", "deepslate_uraninite_ore");
    private static final Set<String> AXE = Set.of("mine_support", "ventilation_door", "explosion_barrier", "document_lectern",
            "wooden_pole");
    private static final Set<String> SHOVEL = Set.of("fireclay", "sand_mold", "natural_bitumen");

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
                }
            }
            tag(BlockTags.NEEDS_IRON_TOOL).add(PcBlocks.OKLO_URANINITE_ORE.get());
            tag(PcTags.Blocks.ORES).add(PcBlocks.OKLO_URANINITE_ORE.get());
            for (DeferredBlock<PcBlock> db : PcBlocks.selfAssayed()) {
                tag(BlockTags.NEEDS_STONE_TOOL).add(db.get());
                tag(PcTags.Blocks.ORES).add(db.get());
            }
            // железные руды для маршрута I0: свои и любые c:ores/iron (свои в c:ores/<металл> — только в OpenCompatPack)
            tag(PcTags.Blocks.IRON_ORES).add(PcBlocks.MAGNETITE_ORE.get(), PcBlocks.HEMATITE_ORE.get())
                    .addOptionalTag(PcTags.Blocks.common("ores/iron"));
            // 08 §8.2.6: открытое пламя в выработке — источник воспламенения
            // взрывозащищённое исполнение 09 §9.11.3: газоанализаторы и ёмкость СУГ не искрят
            tag(PcTags.Blocks.EXPLOSION_PROOF).add(PcBlocks.GAS_DETECTOR.get(), PcBlocks.GAS_STORAGE_BULLET.get(),
                    PcBlocks.TANK_GAUGE.get(), PcBlocks.CP_TEST_POST.get());
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
            tag(BlockTags.CLIMBABLE).add(PcBlocks.CAGE_LADDER.get(), PcBlocks.STEEL_SCAFFOLD.get());
            tag(PcTags.Blocks.TOPOLOGY_RELEVANT).add(PcBlocks.topologyRelevant().toArray(Block[]::new));
            tag(PcTags.Blocks.DAMAGE_IMMUNE).add(Blocks.BEDROCK, Blocks.BARRIER, Blocks.LIGHT, Blocks.END_PORTAL,
                    Blocks.END_PORTAL_FRAME, Blocks.END_GATEWAY, Blocks.NETHER_PORTAL, Blocks.COMMAND_BLOCK,
                    Blocks.CHAIN_COMMAND_BLOCK, Blocks.REPEATING_COMMAND_BLOCK, Blocks.STRUCTURE_BLOCK, Blocks.JIGSAW,
                    Blocks.REINFORCED_DEEPSLATE);
            contraptionTags();
        }

        /**
         * Теги контрапций Create (16 §16.2.4, K22). {@code create:non_movable} — каждый наш блок с блок-сущностью:
         * работающая машина, активная зона, части мультиблоков; контрапция не должна увозить их вместе с состоянием.
         * Перевозить можно ёмкости и тару ({@link #MOVABLE_SUFFIXES}), кроме частей живого парового тракта
         * ({@link #NOT_MOVABLE_VESSELS}). {@code create:brittle} — приборы и элементы пульта 03c: они не входят
         * в {@code non_movable}, чтобы теги не пересекались. {@code create:safe_nbt} остаётся пустым.
         */
        private void contraptionTags() {
            TagKey<Block> nonMovable = createTag("non_movable");
            TagKey<Block> brittle = createTag("brittle");
            for (Block b : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
                var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b);
                if (!PromptCritical.MOD_ID.equals(key.getNamespace())) continue;
                String id = key.getPath();
                if (BRITTLE.contains(id)) tag(brittle).add(b);
                else if ((b instanceof net.minecraft.world.level.block.EntityBlock || NOT_MOVABLE_VESSELS.contains(id))
                        && !movableVessel(id)) tag(nonMovable).add(b);
            }
            tag(nonMovable).add(PcBlocks.MULTIBLOCK_PART.get(), PcBlocks.KINETIC_PORT.get());
        }

        private static boolean movableVessel(String id) {
            if (NOT_MOVABLE_VESSELS.contains(id)) return false;
            for (String s : MOVABLE_SUFFIXES) if (id.endsWith(s)) return true;
            return false;
        }

        private static TagKey<Block> createTag(String path) {
            return TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", path));
        }
    }

    /** Ёмкости и тара, которые контрапция может везти (16 §16.2.4: «перевозить можно контейнеры, ёмкости»). */
    static final List<String> MOVABLE_SUFFIXES = List.of("_tank", "_drum", "_barrel", "_crate", "_cask", "_flask",
            "_cylinder", "_bullet", "_container");
    /** Подходят по суффиксу, но это части работающего парового тракта [моё]. */
    static final Set<String> NOT_MOVABLE_VESSELS = Set.of("steam_drum", "steam_engine_cylinder");
    /** Приборы и элементы пульта 03c — {@code create:brittle}. */
    static final Set<String> BRITTLE = Set.of("annunciator_panel", "alarm_horn", "control_desk", "historian_server",
            "rtu_station", "simulation_workstation", "training_simulator", "simulator_console", "instructor_station",
            "watchdog_relay", "indicator_lamp", "recording_gauge", "tank_gauge", "energy_meter", "relay_rack");

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
            dev.denfry.promptcritical.mod.ironworks.IronworksTags.addItemTags(this::tag);
            CommonTags.addItemTags(this::tag);
            // руды и сырьё мода — только в общих c:ores и c:raw_materials: в теги по металлам их не кладём, иначе
            // Mekanism, IE и подобные удвоят их в слитки мимо цепочки мода (открытый вариант — OpenCompatPack)
            for (DeferredItem<Item> raw : RAWS) tag(PcTags.Items.RAW_MATERIALS).add(raw.get());
            // наполнители поглощения 09 §9.5.10: опилки (палки), солома (пшеница), водоросли, скорлупа (какао-бобы)
            tag(PcTags.Items.LOSS_CIRCULATION_MATERIAL).add(net.minecraft.world.item.Items.STICK, net.minecraft.world.item.Items.WHEAT,
                    net.minecraft.world.item.Items.DRIED_KELP, net.minecraft.world.item.Items.COCOA_BEANS);
        }
    }

    private static final List<DeferredItem<Item>> RAWS = List.of(PcItems.RAW_MAGNETITE, PcItems.RAW_HEMATITE,
            PcItems.RAW_PYROLUSITE, PcItems.RAW_PENTLANDITE, PcItems.RAW_CHALCOPYRITE, PcItems.RAW_SPERRYLITE,
            PcItems.RAW_MOLYBDENITE, PcItems.RAW_SPHALERITE, PcItems.RAW_CHROMITE, PcItems.RAW_CASSITERITE, PcItems.RAW_BAUXITE,
            PcItems.RAW_URANINITE);

    /** Металл сырья {@code raw_<руда>}. */
    private static String metalOfRaw(DeferredItem<Item> raw) {
        return METAL.get(raw.getId().getPath().substring("raw_".length()) + "_ore");
    }

    /**
     * Датапак открытой совместимости ({@code OpenCompatPack}): руды и сырьё мода в {@code c:ores/<металл>} и
     * {@code c:raw_materials/<металл>} — для сборок, где переработка руд другими модами желательна.
     */
    public static final class OpenCompatBlockTags extends BlockTagsProvider {
        public OpenCompatBlockTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper efh) {
            super(output, lookup, PromptCritical.MOD_ID, efh);
        }

        @Override
        public String getName() {
            return "Open compat block tags";
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            for (DeferredBlock<? extends Block> db : PcBlocks.oreDrops().keySet()) {
                tag(PcTags.Blocks.common("ores/" + METAL.get(db.getId().getPath()))).add(db.get());
            }
        }
    }

    public static final class OpenCompatItemTags extends ItemTagsProvider {
        public OpenCompatItemTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup,
                CompletableFuture<TagLookup<Block>> blockTags, ExistingFileHelper efh) {
            super(output, lookup, blockTags, PromptCritical.MOD_ID, efh);
        }

        @Override
        public String getName() {
            return "Open compat item tags";
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            for (String metal : Set.copyOf(METAL.values())) {
                copy(PcTags.Blocks.common("ores/" + metal), PcTags.Items.common("ores/" + metal));
            }
            for (DeferredItem<Item> raw : RAWS) tag(PcTags.Items.common("raw_materials/" + metalOfRaw(raw))).add(raw.get());
        }
    }

    /** Теги жидкостей: экспорт газов в {@code c:} ({@link CommonTags}). */
    public static final class FluidTagProvider extends FluidTagsProvider {
        public FluidTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper efh) {
            super(output, lookup, PromptCritical.MOD_ID, efh);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            CommonTags.addFluidTags(this::tag);
            // закалочное масло кузницы (18 §18.6.4): минеральные фракции, не лёгкое топливо
            tag(dev.denfry.promptcritical.mod.manufacturing.Smithy.QUENCH_OIL).add(
                    dev.denfry.promptcritical.mod.registry.PcFluids.CRUDE_OIL.source.get(),
                    dev.denfry.promptcritical.mod.registry.PcFluids.VACUUM_GAS_OIL.source.get(),
                    dev.denfry.promptcritical.mod.registry.PcFluids.LIGHT_CYCLE_OIL.source.get(),
                    dev.denfry.promptcritical.mod.registry.PcFluids.DIESEL_FUEL.source.get());
        }
    }
}

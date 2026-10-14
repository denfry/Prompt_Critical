package dev.denfry.promptcritical.mod.registry;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcBlock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Предметы вехи M2: реестры 07 §7.22, 08 §8.20, 11; предметы блоков. Поведение добавляют этапы 6–9. */
public final class PcItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PromptCritical.MOD_ID);

    private static final List<DeferredItem<? extends Item>> PLAIN = new ArrayList<>();

    // котельная (07)
    public static final DeferredItem<Item> FUSIBLE_PLUG = item("fusible_plug", 64);
    public static final DeferredItem<Item> GOVERNOR_DASHPOT = item("governor_dashpot", 16);
    public static final DeferredItem<Item> BOILER_SLAG = item("boiler_slag", 64);
    // уголь и руды (08 §8.1, §8.3)
    public static final DeferredItem<Item> RAW_COAL = item("raw_coal", 64);
    public static final DeferredItem<Item> WASHED_COAL = item("washed_coal", 64);
    public static final DeferredItem<Item> COAL_MIDDLINGS = item("coal_middlings", 64);
    public static final DeferredItem<Item> COAL_REFUSE = item("coal_refuse", 64);
    public static final DeferredItem<Item> RAW_MAGNETITE = item("raw_magnetite", 64);
    public static final DeferredItem<Item> RAW_HEMATITE = item("raw_hematite", 64);
    public static final DeferredItem<Item> RAW_PYROLUSITE = item("raw_pyrolusite", 64);
    public static final DeferredItem<Item> RAW_PENTLANDITE = item("raw_pentlandite", 64);
    public static final DeferredItem<Item> RAW_CHALCOPYRITE = item("raw_chalcopyrite", 64);
    public static final DeferredItem<Item> RAW_SPERRYLITE = item("raw_sperrylite", 64);
    public static final DeferredItem<Item> RAW_MOLYBDENITE = item("raw_molybdenite", 64);
    public static final DeferredItem<Item> RAW_SPHALERITE = item("raw_sphalerite", 64);
    public static final DeferredItem<Item> RAW_CHROMITE = item("raw_chromite", 64);
    public static final DeferredItem<Item> RAW_CASSITERITE = item("raw_cassiterite", 64);
    public static final DeferredItem<Item> RAW_BAUXITE = item("raw_bauxite", 64);
    // шахта, литьё, цех (08 §8.2, §8.9, §8.10)
    public static final DeferredItem<Item> STONE_DUST = item("stone_dust", 64);
    public static final DeferredItem<Item> PIG_IRON = item("pig_iron", 64);
    public static final DeferredItem<Item> FOUNDRY_SAND = item("foundry_sand", 64);
    public static final DeferredItem<Item> CASTING_PATTERN = item("casting_pattern", 16);
    public static final DeferredItem<Item> CASTING = item("casting", 16);
    public static final DeferredItem<Item> REFRACTORY_BRICK = item("refractory_brick", 64);
    public static final DeferredItem<Item> CUTTING_TOOL = item("cutting_tool", 1);
    public static final DeferredItem<Item> FLAME_SAFETY_LAMP = item("flame_safety_lamp", 1);
    public static final DeferredItem<Item> SELF_RESCUER = item("self_rescuer", 1);
    public static final DeferredItem<Item> STONE_DUSTER = item("stone_duster", 1);
    public static final DeferredItem<Item> WIRE_ROPE = item("wire_rope", 16);
    public static final DeferredItem<Item> CANARY = item("canary", 16);
    // справочник (11)
    public static final DeferredItem<Item> ENGINEERING_HANDBOOK = item("engineering_handbook", 1);
    public static final DeferredItem<Item> OPERATING_PROCEDURE = item("operating_procedure", 1);
    public static final DeferredItem<Item> GEOLOGICAL_MAP = item("geological_map", 1);

    static {
        for (DeferredBlock<? extends Block> b : PcBlocks.withItems()) {
            ITEMS.register(b.getId().getPath(), () -> new PcBlockItem(b.get(), new Item.Properties()));
        }
    }

    private PcItems() {
    }

    private static DeferredItem<Item> item(String name, int stack) {
        DeferredItem<Item> i = ITEMS.registerSimpleItem(name, new Item.Properties().stacksTo(stack));
        PLAIN.add(i);
        return i;
    }

    public static List<DeferredItem<? extends Item>> plainItems() {
        return Collections.unmodifiableList(PLAIN);
    }

    /** Предмет блока; многоклеточный блок с клетками ниже якоря ставится так, чтобы нижний слой лёг на землю. */
    public static final class PcBlockItem extends BlockItem {
        public PcBlockItem(Block block, Properties props) {
            super(block, props);
        }

        @Override
        public BlockPlaceContext updatePlacementContext(BlockPlaceContext ctx) {
            if (getBlock() instanceof PcBlock b) {
                int lift = -b.spec().footprint().minY();
                if (lift > 0) {
                    BlockPos pos = ctx.getClickedPos().above(lift);
                    return BlockPlaceContext.at(ctx, pos, ctx.getClickedFace());
                }
            }
            return ctx;
        }
    }
}

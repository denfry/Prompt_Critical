package dev.denfry.promptcritical.mod.registry;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.ChimneyBlock;
import dev.denfry.promptcritical.mod.block.HeadframeBlock;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.block.PcBlock.Spec;
import dev.denfry.promptcritical.mod.block.PcDeviceBlock;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.denfry.promptcritical.mod.multiblock.Footprint.Segment;
import dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Блоки вехи M2 (эпоха I0): реестры 07 §7.22 и 08 §8.20, состояния — по арт-реестрам. Поведение устройств добавляют
 * этапы 6–8; здесь — свойства, объём ({@link Footprint}) и доменный двойник.
 */
public final class PcBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PromptCritical.MOD_ID);

    /** Все блоки мода с предметами, в порядке вкладки. */
    private static final List<DeferredBlock<? extends Block>> WITH_ITEMS = new ArrayList<>();
    /** Блок руды → id сырого предмета (выпадение). */
    private static final Map<DeferredBlock<? extends Block>, String> ORE_DROPS = new LinkedHashMap<>();
    private static final List<DeferredBlock<PcDeviceBlock>> DEVICES = new ArrayList<>();

    // --- свойства по материалам ---
    private static Properties ore() {
        return Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops().strength(3.0f, 3.0f);
    }

    private static Properties machine() {
        return Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(3.5f, 6.0f)
                .sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK);
    }

    private static Properties brick() {
        return Properties.of().mapColor(MapColor.COLOR_RED).instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops().strength(2.0f, 6.0f).noOcclusion();
    }

    private static Properties wood() {
        return Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.0f, 3.0f)
                .sound(SoundType.WOOD).noOcclusion().ignitedByLava();
    }

    private static Properties lit(Properties p) {
        return p.lightLevel(s -> s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT) ? 13 : 0);
    }

    // --- объёмы (модельные координаты, фасад на север) ---
    private static final Segment CUBE3 = Segment.box(-1, 1, -1, 1, -1, 1);
    static final Footprint FP_BOILER = Footprint.of(CUBE3, CUBE3.at(0, 0, 3));
    static final Footprint FP_ENGINE = Footprint.of(CUBE3, Segment.box(-1, 1, -1, 0, -1, 1).at(0, 0, 3));
    private static final Footprint FP_CUBE3 = Footprint.of(CUBE3);
    private static final Footprint FP_TALL3 = Footprint.of(Segment.box(0, 0, -1, 1, 0, 0));
    private static final Footprint FP_TALL2 = Footprint.of(Segment.box(0, 0, 0, 1, 0, 0));
    private static final Footprint FP_WIDE2 = Footprint.of(Segment.box(0, 1, 0, 0, 0, 0));

    // --- котельная I0 (07) ---
    public static final DeferredBlock<PcDeviceBlock> FIRE_TUBE_BOILER = device("fire_tube_boiler", lit(machine()),
            Spec.facing(BlockStateProperties.LIT).segments(PcProperties.BOILER_SEGMENT, FP_BOILER));
    public static final DeferredBlock<PcDeviceBlock> GRATE_STOKER = device("grate_stoker", machine(), Spec.facing(PcProperties.RUNNING));
    public static final DeferredBlock<ChimneyBlock> CHIMNEY = withItem(BLOCKS.registerBlock("chimney", ChimneyBlock::create, brick()));
    public static final DeferredBlock<PcDeviceBlock> STEAM_ENGINE_CYLINDER = device("steam_engine_cylinder", machine(),
            Spec.facing(PcProperties.RUNNING).segments(PcProperties.ENGINE_SEGMENT, FP_ENGINE));

    // --- руды и пласты (08 §8.1) ---
    public static final DeferredBlock<PcBlock> MAGNETITE_ORE = ore("magnetite_ore", "raw_magnetite");
    public static final DeferredBlock<PcBlock> HEMATITE_ORE = ore("hematite_ore", "raw_hematite");
    public static final DeferredBlock<PcBlock> PYROLUSITE_ORE = ore("pyrolusite_ore", "raw_pyrolusite");
    public static final DeferredBlock<PcBlock> COAL_SEAM = oreBlock("coal_seam", "raw_coal",
            Spec.of(PcProperties.HOST, PcProperties.RANK));
    public static final DeferredBlock<PcBlock> FIRECLAY = block("fireclay",
            Properties.of().mapColor(MapColor.CLAY).instrument(NoteBlockInstrument.FLUTE).strength(0.6f).sound(SoundType.GRAVEL), Spec.of());
    public static final DeferredBlock<PcBlock> PENTLANDITE_ORE = ore("pentlandite_ore", "raw_pentlandite");
    public static final DeferredBlock<PcBlock> CHALCOPYRITE_ORE = ore("chalcopyrite_ore", "raw_chalcopyrite");
    public static final DeferredBlock<PcBlock> SPERRYLITE_ORE = ore("sperrylite_ore", "raw_sperrylite");
    public static final DeferredBlock<PcBlock> MOLYBDENITE_ORE = ore("molybdenite_ore", "raw_molybdenite");
    public static final DeferredBlock<PcBlock> SPHALERITE_ORE = ore("sphalerite_ore", "raw_sphalerite");
    public static final DeferredBlock<PcBlock> CHROMITE_ORE = ore("chromite_ore", "raw_chromite");
    public static final DeferredBlock<PcBlock> VEIN_QUARTZ = block("vein_quartz",
            Properties.of().mapColor(MapColor.QUARTZ).requiresCorrectToolForDrops().strength(1.5f, 6.0f), Spec.of());
    public static final DeferredBlock<PcBlock> CASSITERITE_ORE = ore("cassiterite_ore", "raw_cassiterite");
    public static final DeferredBlock<PcBlock> BAUXITE_ORE = oreBlock("bauxite_ore", "raw_bauxite", Spec.of());

    // --- шахта (08 §8.2) ---
    public static final DeferredBlock<PcBlock> MINE_SUPPORT = block("mine_support", wood(),
            Spec.facing(PcProperties.SUPPORT_KIND, PcProperties.DAMAGED).footprint(FP_TALL3));
    public static final DeferredBlock<PcDeviceBlock> POWERED_ROOF_SUPPORT = device("powered_roof_support", machine(),
            Spec.facing().footprint(Footprint.of(Segment.box(0, 0, -1, 1, 0, 1))));
    public static final DeferredBlock<PcDeviceBlock> LONGWALL_SHEARER = device("longwall_shearer", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(Footprint.of(Segment.box(-1, 1, 0, 0, 0, 0))));
    public static final DeferredBlock<PcDeviceBlock> MAIN_MINE_FAN = device("main_mine_fan", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(FP_CUBE3));
    public static final DeferredBlock<PcBlock> VENTILATION_DOOR = block("ventilation_door", wood(),
            Spec.facing(PcProperties.DOOR_KIND, BlockStateProperties.OPEN).footprint(FP_TALL2));
    public static final DeferredBlock<PcBlock> EXPLOSION_BARRIER = block("explosion_barrier", wood(), Spec.of(PcProperties.BARRIER_KIND));
    public static final DeferredBlock<PcDeviceBlock> DEGASIFICATION_BOREHOLE = device("degasification_borehole", machine(), Spec.facing());
    public static final DeferredBlock<PcDeviceBlock> DEGASIFICATION_PUMP = device("degasification_pump", machine(), Spec.facing(PcProperties.RUNNING));
    public static final DeferredBlock<PcDeviceBlock> MINE_DISPATCH_CABINET = device("mine_dispatch_cabinet", machine(),
            Spec.facing(PcProperties.ALARM).footprint(FP_TALL2));
    public static final DeferredBlock<PcDeviceBlock> MINE_HOIST = device("mine_hoist", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(Footprint.of(Segment.box(-1, 1, -1, 0, -1, 1))));
    public static final DeferredBlock<HeadframeBlock> HEADFRAME = withItem(BLOCKS.registerBlock("headframe", HeadframeBlock::create, machine()));
    public static final DeferredBlock<PcBlock> MINE_CAGE = block("mine_cage", machine(),
            Spec.facing(BlockStateProperties.OPEN).footprint(Footprint.of(Segment.box(0, 1, -1, 1, 0, 1))));
    public static final DeferredBlock<PcDeviceBlock> MINE_DRAINAGE_PUMP = device("mine_drainage_pump", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(FP_WIDE2));

    // --- обогащение, литьё, механический цех (08 §8.3, §8.9, §8.10) ---
    public static final DeferredBlock<PcDeviceBlock> COAL_JIG = device("coal_jig", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(Footprint.of(Segment.box(0, 1, 0, 1, 0, 0))));
    public static final DeferredBlock<PcDeviceBlock> CUPOLA_FURNACE = device("cupola_furnace", lit(brick()),
            Spec.of(BlockStateProperties.LIT).footprint(FP_TALL3));
    public static final DeferredBlock<PcDeviceBlock> CRUCIBLE_FURNACE = device("crucible_furnace", lit(brick()), Spec.facing(BlockStateProperties.LIT));
    public static final DeferredBlock<PcDeviceBlock> SAND_MOLD = device("sand_mold",
            Properties.of().mapColor(MapColor.SAND).strength(0.8f).sound(SoundType.SAND).noOcclusion(), Spec.of(PcProperties.MOLD_STAGE));
    public static final DeferredBlock<PcDeviceBlock> MOLDING_STATION = device("molding_station", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(FP_WIDE2));
    public static final DeferredBlock<PcDeviceBlock> SHAKEOUT_GRATE = device("shakeout_grate", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(Footprint.of(Segment.box(0, 1, 0, 0, 0, 1))));
    public static final DeferredBlock<PcDeviceBlock> MACHINE_LATHE = device("machine_lathe", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(FP_WIDE2));
    public static final DeferredBlock<PcDeviceBlock> DRILL_PRESS = device("drill_press", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(FP_TALL2));

    // --- справочник и мир (11) ---
    public static final DeferredBlock<PcBlock> DOCUMENT_LECTERN = block("document_lectern", wood(), Spec.facing(PcProperties.HAS_DOCUMENT));
    public static final DeferredBlock<PcBlock> CANARY_CAGE = block("canary_cage",
            Properties.of().mapColor(MapColor.METAL).strength(0.5f).sound(SoundType.LANTERN).noOcclusion(), Spec.of(PcProperties.BIRD));

    // --- служебные ---
    public static final DeferredBlock<MultiblockPartBlock> MULTIBLOCK_PART = BLOCKS.registerBlock("multiblock_part",
            MultiblockPartBlock::new, Properties.of().mapColor(MapColor.METAL).strength(3.5f, 6.0f).noOcclusion()
                    .noLootTable().pushReaction(PushReaction.BLOCK).isViewBlocking((s, l, p) -> false));

    private PcBlocks() {
    }

    private static <B extends Block> DeferredBlock<B> withItem(DeferredBlock<B> b) {
        WITH_ITEMS.add(b);
        return b;
    }

    private static DeferredBlock<PcBlock> block(String name, Properties props, Spec spec) {
        return withItem(BLOCKS.registerBlock(name, p -> PcBlock.create(p, spec), props));
    }

    private static DeferredBlock<PcDeviceBlock> device(String name, Properties props, Spec spec) {
        DeferredBlock<PcDeviceBlock> b = withItem(BLOCKS.registerBlock(name, p -> PcDeviceBlock.create(p, spec), props));
        DEVICES.add(b);
        return b;
    }

    private static DeferredBlock<PcBlock> ore(String name, String rawItem) {
        return oreBlock(name, rawItem, Spec.of(PcProperties.HOST));
    }

    private static DeferredBlock<PcBlock> oreBlock(String name, String rawItem, Spec spec) {
        DeferredBlock<PcBlock> b = block(name, ore(), spec);
        ORE_DROPS.put(b, rawItem);
        return b;
    }

    public static List<DeferredBlock<? extends Block>> withItems() {
        return Collections.unmodifiableList(WITH_ITEMS);
    }

    public static Map<DeferredBlock<? extends Block>, String> oreDrops() {
        return Collections.unmodifiableMap(ORE_DROPS);
    }

    public static List<DeferredBlock<PcDeviceBlock>> devices() {
        return Collections.unmodifiableList(DEVICES);
    }

    /** Блоки, состояние которых участвует в топологии (05 §5.3.3): всё, кроме руд и декора. */
    public static List<Block> topologyRelevant() {
        List<Block> out = new ArrayList<>();
        for (DeferredBlock<? extends Block> b : WITH_ITEMS) {
            if (!ORE_DROPS.containsKey(b) && b != FIRECLAY && b != VEIN_QUARTZ) out.add(b.get());
        }
        out.add(MULTIBLOCK_PART.get());
        return out;
    }
}

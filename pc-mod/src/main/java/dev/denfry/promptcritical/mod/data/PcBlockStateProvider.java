package dev.denfry.promptcritical.mod.data;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.grid.CableBlock;
import dev.denfry.promptcritical.mod.ironworks.GasMainBlock;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ItemModelBuilder;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * Состояния блоков по готовым моделям арт-пайплайна (00 §0.12, {@code art/README.md}): {@code <id>} и
 * {@code <id>_<состояние>} для визуально различимых вариантов, руды — по породе {@code host}. Модели только
 * читаются (ExistingFileHelper проверяет, что файл есть); поворот — по {@code facing}, фасад модели на север.
 */
public final class PcBlockStateProvider extends BlockStateProvider {
    public PcBlockStateProvider(PackOutput output, ExistingFileHelper efh) {
        super(output, PromptCritical.MOD_ID, efh);
    }

    @Override
    protected void registerStatesAndModels() {
        for (DeferredBlock<? extends Block> db : PcBlocks.withItems()) {
            Block block = db.get();
            String id = db.getId().getPath();
            PcBlocks.Form form = PcBlocks.decorForms().get(db);
            if (form != null) {
                decorForm(block, id, form);
                continue;
            }
            if (block instanceof CableBlock cable) {
                cable(cable, id);
                continue;
            }
            if (block instanceof GasMainBlock) {
                gasMain(block, id);
                continue;
            }
            if (PARTS.contains(id)) {
                parts(block, id);
                itemModel(id, block);
                continue;
            }
            getVariantBuilder(block).forAllStates(state -> ConfiguredModel.builder()
                    .modelFile(existing(modelName(id, state)))
                    .rotationY(rotationY(id, state))
                    .build());
            itemModel(id, block);
        }
        // второй предмет двери — регулятор (08 §8.2.7)
        itemModel(PcItems.VENTILATION_REGULATOR.getId().getPath(), "ventilation_door_regulator", PcBlocks.VENTILATION_DOOR.get());
        ModelFile part = models().getBuilder("multiblock_part").texture("particle", modLoc("block/common/steel_dark"));
        simpleBlock(PcBlocks.MULTIBLOCK_PART.get(), part);
        simpleBlock(PcBlocks.KINETIC_PORT.get(), part);
        // жидкость рисует рендер жидкостей; модели нужна только частица
        simpleBlock(PcBlocks.MINE_WATER.get(), models().getBuilder("mine_water").texture("particle", modLoc("block/fluid/mine_water_still")));
    }

    /** Плита, ступени и стенка по ванильным шаблонам из текстуры базового блока декора. */
    private void decorForm(Block block, String id, PcBlocks.Form form) {
        var tex = modLoc("block/decor/" + form.base());
        switch (form.kind()) {
            case "slab" -> slabBlock((SlabBlock) block, modLoc("block/" + form.base()), tex);
            case "stairs" -> stairsBlock((StairBlock) block, tex);
            default -> {
                wallBlock((WallBlock) block, tex);
                itemModels().wallInventory(id, tex);
                return;
            }
        }
        itemModels().withExistingParent(id, modLoc("block/" + id));
    }

    /**
     * Кабель и шина — сердечник и плечи к соединённым граням; модель плеча смотрит на север. Шина — только
     * горизонтальные плечи (её плечо у верха блока).
     */
    private void cable(CableBlock block, String id) {
        var mp = getMultipartBuilder(block);
        mp.part().modelFile(existing(id)).addModel().end();
        ModelFile side = existing(id + "_side");
        boolean vertical = block != PcBlocks.BUSBAR.get();
        for (Direction d : Direction.values()) {
            if (d.getAxis() == Direction.Axis.Y && !vertical) continue;
            int x = d == Direction.UP ? 270 : d == Direction.DOWN ? 90 : 0;
            int y = d.getAxis() == Direction.Axis.Y ? 0 : ((int) d.toYRot() + 180) % 360;
            mp.part().modelFile(side).rotationX(x).rotationY(y).uvLock(false).addModel()
                    .condition(CableBlock.SIDES.get(d), true).end();
        }
        itemModels().withExistingParent(id, modLoc("block/" + id));
    }

    /** Блоки с неподвижными частями из арт-реестра (partial): части добавляются мультипартом к модели состояния. */
    private static final java.util.Set<String> PARTS = java.util.Set.of("coke_pusher_ram", "sinter_strand", "blast_furnace_top",
            "cast_house_machine", "ladle", "roller_table", "rolling_mill_stand", "piercing_mill", "pipe_welder", "asu_main_compressor",
            "asu_expander", "air_separator", "hydrotest_pump", "recording_gauge", "water_clarifier");

    /** Модели состояния мультипартом: основная и части, все с поворотом по {@code facing}. */
    private void parts(Block block, String id) {
        var mp = getMultipartBuilder(block);
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            for (String model : partModels(id, state)) {
                var part = mp.part().modelFile(existing(model)).rotationY(rotationY(id, state)).addModel();
                for (var prop : state.getProperties()) condition(part, prop, state);
                part.end();
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void condition(net.neoforged.neoforge.client.model.generators.MultiPartBlockStateBuilder.PartBuilder part,
            net.minecraft.world.level.block.state.properties.Property prop, BlockState state) {
        part.condition(prop, state.getValue(prop));
    }

    static java.util.List<String> partModels(String id, BlockState s) {
        String base = modelName(id, s);
        return switch (id) {
            case "coke_pusher_ram" -> java.util.List.of(base, id + "/partial/ram");
            case "sinter_strand" -> java.util.List.of(base, id + "/partial/pallets");
            case "blast_furnace_top" -> java.util.List.of(base, id + "/partial/cone");
            case "cast_house_machine" -> java.util.List.of(base, s.getValue(PcProperties.CAST_HOUSE_KIND) == PcProperties.CastHouseKind.MUD_GUN
                    ? id + "/partial/piston" : id + "/partial/bit");
            case "ladle" -> s.getValue(PcProperties.FILLED) ? java.util.List.of(base, id + "/partial/body", id + "/partial/melt")
                    : java.util.List.of(base, id + "/partial/body");
            case "roller_table" -> java.util.List.of(base, id + "/partial/rollers");
            case "rolling_mill_stand" -> java.util.List.of(base, id + "/partial/roll_top", id + "/partial/roll_bottom");
            case "piercing_mill" -> java.util.List.of(base, id + "/partial/roll_l", id + "/partial/roll_r");
            case "pipe_welder" -> java.util.List.of(base, id + "/partial/forming_rolls");
            case "asu_main_compressor", "asu_expander" -> java.util.List.of(base, id + "/partial/rotor");
            case "air_separator" -> java.util.List.of(base, id + "/partial/fan");
            case "hydrotest_pump" -> java.util.List.of(base, id + "/partial/lever");
            case "recording_gauge" -> java.util.List.of(base, id + "/partial/drum");
            case "water_clarifier" -> java.util.List.of(base, id + "/partial/rake");
            default -> java.util.List.of(base);
        };
    }

    /** Газосборник: труба и горизонтальные плечи к соседям; модель плеча смотрит на запад. */
    private void gasMain(Block block, String id) {
        var mp = getMultipartBuilder(block);
        mp.part().modelFile(existing(id)).addModel().end();
        ModelFile side = existing(id + "_side");
        for (Direction d : Direction.Plane.HORIZONTAL) {
            mp.part().modelFile(side).rotationY(((int) d.toYRot() + 270) % 360).uvLock(false).addModel()
                    .condition(GasMainBlock.SIDES.get(d), true).end();
        }
        itemModels().withExistingParent(id, modLoc("block/" + id));
    }

    private ModelFile existing(String name) {
        return models().getExistingFile(modLoc("block/" + name));
    }

    private static int rotationY(String id, BlockState state) {
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return 0;
        Direction f = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        // жёлоб в модели идёт вдоль X: по направлению взгляда при установке
        int extra = id.equals("iron_runner") ? 90 : 0;
        return ((int) f.toYRot() + 180 + extra) % 360;
    }

    /** Имя модели состояния; варианты без отдельной модели рисуются базовой. */
    static String modelName(String id, BlockState s) {
        return switch (id) {
            case "fire_tube_boiler" -> {
                boolean exploded = s.getValue(PcProperties.EXPLODED);
                if (s.getValue(PcProperties.BOILER_SEGMENT) == PcProperties.BoilerSegment.REAR)
                    yield exploded ? id + "_rear_exploded" : id + "_rear";
                if (exploded) yield id + "_exploded";
                String lit = s.getValue(BlockStateProperties.LIT) ? "_lit" : "";
                yield id + lit + (s.getValue(PcProperties.LOW_WATER) ? "_low_water" : "");
            }
            case "steam_engine_cylinder" -> s.getValue(PcProperties.ENGINE_SEGMENT) == PcProperties.EngineSegment.FRAME ? id + "_frame" : id;
            case "grate_stoker", "main_mine_fan" -> s.getValue(PcProperties.RUNNING) ? id + "_running" : id;
            case "chimney" -> switch (s.getValue(PcProperties.CHIMNEY_PART)) {
                case BASE -> id + "_base";
                case TOP -> id + "_top";
                case MIDDLE -> id;
            };
            case "coal_seam" -> switch (s.getValue(PcProperties.RANK)) {
                case LIGNITE -> id + "_lignite";
                case ANTHRACITE -> id + "_anthracite";
                case BITUMINOUS -> hosted(id, s);
            };
            case "mine_support" -> s.getValue(PcProperties.SUPPORT_KIND) == PcProperties.SupportKind.ARCH ? id + "_arch"
                    : s.getValue(PcProperties.DAMAGED) ? id + "_damaged" : id;
            case "ventilation_door" -> s.getValue(PcProperties.DOOR_KIND) == PcProperties.DoorKind.REGULATOR ? id + "_regulator"
                    : s.getValue(BlockStateProperties.OPEN) ? id + "_open" : id;
            case "explosion_barrier" -> s.getValue(PcProperties.BARRIER_KIND) == PcProperties.BarrierKind.WATER ? id + "_water" : id;
            case "mine_dispatch_cabinet" -> s.getValue(PcProperties.ALARM) ? id + "_alarm" : id;
            case "headframe" -> s.getValue(PcProperties.TOP) ? id + "_top" : id;
            case "mine_cage" -> s.getValue(BlockStateProperties.OPEN) ? id + "_open" : id;
            case "cupola_furnace", "crucible_furnace" -> s.getValue(BlockStateProperties.LIT) ? id + "_lit" : id;
            case "sand_mold" -> switch (s.getValue(PcProperties.MOLD_STAGE)) {
                case EMPTY -> id;
                case POURED -> id + "_poured";
                case COOLING -> id + "_cooling";
            };
            case "document_lectern" -> s.getValue(PcProperties.HAS_DOCUMENT) ? id + "_document" : id;
            case "industrial_light", "industrial_light_street", "industrial_light_floodlight", "industrial_light_exit" ->
                    s.getValue(BlockStateProperties.LIT) ? id : id + "_off";
            // электросеть I1 (этап 8 M3)
            case "electric_motor" -> s.getValue(PcProperties.MEDIUM_VOLTAGE) ? id + "_mv" : id;
            case "disconnector" -> s.getValue(PcProperties.EARTHED) ? id + "_earthed" : id;
            case "switchgear_cell" -> s.getValue(PcProperties.ALARM) ? id + "_alarm"
                    : s.getValue(BlockStateProperties.OPEN) ? id + "_open" : id;
            case "lv_switchboard" -> s.getValue(PcProperties.ALARM) ? id + "_overload"
                    : s.getValue(BlockStateProperties.OPEN) ? id + "_off" : id;
            case "relay_cabinet" -> s.getValue(PcProperties.ALARM) ? id + "_tripped" : id;
            case "lv_distribution_box" -> s.getValue(PcProperties.ALARM) ? id + "_tripped" : id;
            case "grid_consumer" -> s.getValue(BlockStateProperties.LIT) ? id : id + "_dark";
            // автоматика (этап 9 M3)
            case "relay" -> s.getValue(PcProperties.PICKED) ? id + "_picked" : id;
            case "relay_latching" -> s.getValue(PcProperties.PICKED) ? id + "_set" : id;
            case "redstone_interface" -> s.getValue(BlockStateProperties.POWERED) ? id + "_powered" : id;
            case "time_relay" -> s.getValue(PcProperties.DONE) ? id + "_done" : s.getValue(PcProperties.RUNNING) ? id + "_running" : id;
            case "process_switch" -> s.getValue(PcProperties.ALARM) ? id + "_tripped" : switch (s.getValue(PcProperties.PROCESS_KIND)) {
                case PRESSURE -> id;
                case LEVEL -> id + "_level";
                case TEMPERATURE -> id + "_temperature";
                case FLOW -> id + "_flow";
            };
            case "relay_power_supply" -> s.getValue(PcProperties.ALARM) ? id + "_overload"
                    : s.getValue(BlockStateProperties.POWERED) ? id + "_powered" : id;
            case "pushbutton" -> s.getValue(BlockStateProperties.LIT) ? id + "_lit" : id;
            case "indicator_lamp" -> s.getValue(BlockStateProperties.LIT) ? id : id + "_off";
            case "gas_detector" -> switch (s.getValue(PcProperties.GAS_ALARM)) {
                case NONE -> id;
                case WARN -> id + "_warn";
                case ALARM -> id + "_alarm";
            };
            case "analog_comparator" -> s.getValue(PcProperties.ALARM) ? id + "_tripped" : id;
            case "manual_station" -> s.getValue(PcProperties.MANUAL) ? id + "_manual" : id;
            case "coke_battery_wall" -> s.getValue(PcProperties.HOT) ? id + "_hot" : id;
            case "byproduct_plant" -> s.getValue(PcProperties.BYPRODUCT_KIND) == PcProperties.ByproductKind.COOLER ? id
                    : id + "_" + s.getValue(PcProperties.BYPRODUCT_KIND).getSerializedName();
            case "baking_furnace", "sinter_strand" -> s.getValue(BlockStateProperties.LIT) ? id + "_lit" : id;
            case "blast_furnace_tuyere" -> s.getValue(PcProperties.BURNT) ? id + "_burnt" : id;
            case "blast_furnace_taphole" -> s.getValue(BlockStateProperties.OPEN) ? id + "_open" : id;
            case "blast_furnace_controller" -> s.getValue(PcProperties.ALARM) ? id + "_alarm" : id;
            case "hot_blast_stove" -> s.getValue(PcProperties.STOVE_SEGMENT) == PcProperties.StoveSegment.DOME ? id + "_dome" : id;
            case "cast_house_machine" -> s.getValue(PcProperties.CAST_HOUSE_KIND) == PcProperties.CastHouseKind.MUD_GUN ? id + "_mud_gun" : id;
            case "iron_runner", "ingot_mold" -> s.getValue(PcProperties.FILLED) ? id + "_filled" : id;
            case "reheating_furnace" -> s.getValue(BlockStateProperties.LIT) ? id + "_lit" : id;
            case "heat_treatment_furnace" -> s.getValue(BlockStateProperties.OPEN) ? id + "_open"
                    : s.getValue(BlockStateProperties.LIT) ? id + "_lit" : id;
            case "pipe_welder" -> s.getValue(PcProperties.RUNNING) ? id + "_welding" : id;
            case "hydrotest_bench" -> s.getValue(PcProperties.BURST) ? id + "_burst" : id;
            case "roller_table" -> s.getValue(PcProperties.ROLLER_KIND) == PcProperties.RollerKind.COOLING_BED ? id + "_cooling_bed" : id;
            case "quench_tank" -> s.getValue(PcProperties.QUENCH_MEDIUM) == PcProperties.QuenchMedium.OIL ? id + "_oil" : id;
            case "cryogenic_tank" -> s.getValue(PcProperties.CRYO_KIND) == PcProperties.CryoKind.VAPORIZER ? id + "_vaporizer" : id;
            // эксплуатация и водоподготовка (этап 11а M3)
            case "maintenance_panel" -> s.getValue(PcProperties.SEVERITY) == 0 ? id : id + "_severity" + s.getValue(PcProperties.SEVERITY);
            case "recording_gauge" -> s.getValue(PcProperties.PAPER) ? id : id + "_empty";
            case "sand_filter" -> s.getValue(PcProperties.ALARM) ? id + "_backwash" : id;
            case "wooden_pole", "concrete_pole" -> switch (s.getValue(PcProperties.CHIMNEY_PART)) {
                case BASE -> id + "_bottom";
                case TOP -> id + "_top";
                case MIDDLE -> id;
            };
            default -> s.hasProperty(PcProperties.HOST) ? hosted(id, s) : id;
        };
    }

    private static String hosted(String id, BlockState s) {
        return switch (s.getValue(PcProperties.HOST)) {
            case STONE -> id;
            case DEEPSLATE -> id + "_deepslate";
            case TUFF -> id + "_tuff";
        };
    }

    /** Предмет блока — модель состояния по умолчанию; многоклеточные уменьшены по наибольшему габариту. */
    private void itemModel(String id, Block block) {
        itemModel(id, modelName(id, block.defaultBlockState()), block);
    }

    private void itemModel(String id, String model, Block block) {
        ItemModelBuilder item = itemModels().withExistingParent(id, modLoc("block/" + model));
        if (!(block instanceof PcBlock pc) || pc.spec().footprint().isSingle()) return;
        int extent = 1;
        for (Footprint.Segment seg : pc.spec().footprint().segments()) {
            extent = Math.max(extent, Math.max(seg.x1() - seg.x0(), Math.max(seg.y1() - seg.y0(), seg.z1() - seg.z0())) + 1);
        }
        float k = 1.0f / extent;
        item.transforms()
                .transform(ItemDisplayContext.GUI).rotation(30, 225, 0).scale(0.625f * k).end()
                .transform(ItemDisplayContext.GROUND).translation(0, 3, 0).scale(0.25f * k).end()
                .transform(ItemDisplayContext.FIXED).scale(0.5f * k).end()
                .transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).rotation(75, 45, 0).translation(0, 2.5f, 0).scale(0.375f * k).end()
                .transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND).rotation(0, 45, 0).scale(0.4f * k).end()
                .transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND).rotation(0, 225, 0).scale(0.4f * k).end();
    }
}

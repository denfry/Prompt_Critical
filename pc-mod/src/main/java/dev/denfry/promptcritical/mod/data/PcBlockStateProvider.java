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
            if (block instanceof dev.denfry.promptcritical.mod.ops.MechanicalDisconnectBlock) {
                mechanicalDisconnect(block, id);
                continue;
            }
            if (block instanceof GasMainBlock) {
                gasMain(block, id);
                continue;
            }
            if (id.equals("coke_oven") || id.equals("coke_battery_wall")) {
                cokeCell(block, id);
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
        // встроенный путь клети: плоский рельс вдоль оси клети
        getVariantBuilder(PcBlocks.CAGE_TRACK.get()).forAllStates(s -> ConfiguredModel.builder().modelFile(existing("cage_track"))
                .rotationY(s.getValue(dev.denfry.promptcritical.mod.mine.CageTrackBlock.SHAPE) == net.minecraft.world.level.block.state.properties.RailShape.EAST_WEST ? 90 : 0)
                .build());
        // жидкость рисует рендер жидкостей; модели нужна только частица
        simpleBlock(PcBlocks.MINE_WATER.get(), models().getBuilder("mine_water").texture("particle", modLoc("block/fluid/mine_water_still")));
        simpleBlock(PcBlocks.HEAVY_WATER.get(), models().getBuilder("heavy_water").texture("particle", modLoc("block/fluid/heavy_water_still")));
        simpleBlock(PcBlocks.CRUDE_OIL.get(), models().getBuilder("crude_oil").texture("particle", modLoc("block/fluid/crude_oil_still")));
        // плёнка нефти без предмета: стадия выветривания — своя модель (09 §9.11.9)
        getVariantBuilder(PcBlocks.OIL_SLICK.get()).forAllStates(st -> {
            int age = st.getValue(dev.denfry.promptcritical.mod.hazard.SpillBlocks.OilSlickBlock.AGE);
            return ConfiguredModel.builder().modelFile(existing(age == 0 ? "oil_slick" : "oil_slick_age" + age)).build();
        });
    }

    /** Расцепная муфта: модель вдоль Z; ось X — поворот на 90° вокруг Y, ось Y — на 90° вокруг X. */
    private void mechanicalDisconnect(Block block, String id) {
        getVariantBuilder(block).forAllStates(state -> {
            String model = state.getValue(dev.denfry.promptcritical.mod.ops.MechanicalDisconnectBlock.LOCKED) ? id + "_locked"
                    : state.getValue(dev.denfry.promptcritical.mod.ops.MechanicalDisconnectBlock.POWERED) ? id + "_disengaged" : id;
            var axis = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS);
            return ConfiguredModel.builder().modelFile(existing(model))
                    .rotationX(axis == net.minecraft.core.Direction.Axis.Y ? 90 : 0)
                    .rotationY(axis == net.minecraft.core.Direction.Axis.X ? 90 : 0).build();
        });
        itemModel(id, block);
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
            "asu_expander", "air_separator", "hydrotest_pump", "recording_gauge", "water_clarifier", "rotary_table", "mud_pump",
            "pumpjack", "gas_compressor", "pvc_reactor", "leach_tank", "rotary_calciner", "hf_kiln", "mixer_settler",
            "pellet_press", "alumina_precipitator", "alumina_calciner", "reduction_pot", "peirce_smith_converter", "flotation_cell",
            "bop_accumulator", "logging_unit");

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
            case "rotary_table" -> java.util.List.of(base, id + "/partial/turntable", id + "/partial/kelly");
            case "mud_pump" -> java.util.List.of(base, id + "/partial/crankshaft");
            case "bop_accumulator" -> java.util.List.of(base, id + "/partial/levers");
            case "logging_unit" -> java.util.List.of(base, id + "/partial/cable_drum");
            case "leach_tank" -> java.util.List.of(base, id + "/partial/agitator");
            case "rotary_calciner", "hf_kiln" -> java.util.List.of(base, id + "/partial/shell");
            case "mixer_settler" -> java.util.List.of(base, id + "/partial/impeller");
            // цветная металлургия (08 §8.8)
            case "alumina_precipitator" -> java.util.List.of(base, id + "/partial/agitator");
            case "alumina_calciner" -> java.util.List.of(base, "rotary_calciner/partial/shell");
            case "reduction_pot" -> java.util.List.of(base, id + "/partial/anode_frame");
            case "peirce_smith_converter" -> java.util.List.of(base, id + "/partial/vessel");
            case "flotation_cell" -> java.util.List.of(base, id + "/partial/impeller");
            case "pellet_press" -> java.util.List.of(base, "pellet_press/partial/ram");
            case "pumpjack" -> java.util.List.of(base, "pumpjack_gearbox/partial/crank_counterweight", "pumpjack_gearbox/partial/walking_beam",
                    "pumpjack_gearbox/partial/pitman_arm", "pumpjack_gearbox/partial/polished_rod");
            default -> java.util.List.of(base);
        };
    }

    /**
     * Газосборник реального масштаба (art/blockbench/coke/coke_oven/coke_oven_real.author.js): стояк со ступицей
     * коллектора и горизонтальные плечи к соседям; модель плеча смотрит на север.
     */
    private void gasMain(Block block, String id) {
        var mp = getMultipartBuilder(block);
        mp.part().modelFile(existing(id + "/core")).addModel().end();
        ModelFile side = existing(id + "/side");
        for (Direction d : Direction.Plane.HORIZONTAL) {
            mp.part().modelFile(side).rotationY(((int) d.toYRot() + 180) % 360).uvLock(false).addModel()
                    .condition(GasMainBlock.SIDES.get(d), true).end();
        }
        itemModels().withExistingParent(id, modLoc("block/" + id + "/core"));
    }

    /**
     * Клетки коксовой батареи реального масштаба (08 §8.4.1, art/blockbench/coke/coke_oven/coke_oven_real.author.js):
     * модель — по ярусу и торцу, поворот — по оси камер и стороне торца (дверь и анкерная колонна модели — на севере).
     * У пустой камеры люк загрузки на своде открыт, у горячего простенка светятся гляделка нижнего торца и лючки свода.
     */
    private void cokeCell(Block block, String id) {
        boolean oven = id.equals("coke_oven");
        var mp = getMultipartBuilder(block);
        var axisProp = net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_AXIS;
        for (PcProperties.CellLevel lv : PcProperties.CellLevel.values())
            for (PcProperties.CellDoor door : PcProperties.CellDoor.values())
                for (Direction.Axis axis : new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z}) {
                    boolean end = door != PcProperties.CellDoor.NONE;
                    int y = axis == Direction.Axis.Z ? (door == PcProperties.CellDoor.POS ? 180 : 0)
                            : (door == PcProperties.CellDoor.NEG ? 270 : 90);
                    String base = id + "/" + lv.getSerializedName();
                    if (oven && lv == PcProperties.CellLevel.TOP && !end) {
                        mp.part().modelFile(existing(base + "_open")).rotationY(y).addModel()
                                .condition(PcProperties.CELL_LEVEL, lv).condition(PcProperties.CELL_DOOR, door).condition(axisProp, axis)
                                .condition(PcProperties.OVEN_STATE, PcProperties.OvenLook.EMPTY).end();
                        mp.part().modelFile(existing(base)).rotationY(y).addModel()
                                .condition(PcProperties.CELL_LEVEL, lv).condition(PcProperties.CELL_DOOR, door).condition(axisProp, axis)
                                .condition(PcProperties.OVEN_STATE, PcProperties.OvenLook.COKING, PcProperties.OvenLook.READY).end();
                        continue;
                    }
                    mp.part().modelFile(existing(base + (end ? (oven ? "_door" : "_end") : ""))).rotationY(y).addModel()
                            .condition(PcProperties.CELL_LEVEL, lv).condition(PcProperties.CELL_DOOR, door).condition(axisProp, axis).end();
                    if (!oven && (lv == PcProperties.CellLevel.TOP ? !end : lv == PcProperties.CellLevel.BOTTOM && end))
                        mp.part().modelFile(existing(id + "/partial/wall_glow")).rotationY(y).addModel()
                                .condition(PcProperties.CELL_LEVEL, lv).condition(PcProperties.CELL_DOOR, door).condition(axisProp, axis)
                                .condition(PcProperties.HOT, true).end();
                }
        itemModels().withExistingParent(id, modLoc("block/" + id + (oven ? "/middle_door" : "/middle_end")));
    }

    private ModelFile existing(String name) {
        return models().getExistingFile(modLoc("block/" + name));
    }

    private static int rotationY(String id, BlockState state) {
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return 0;
        Direction f = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        // жёлоб в модели идёт вдоль X: по направлению взгляда при установке
        int extra = id.equals("iron_runner") ? 90 : 0;
        // днище барабана нарисовано на восточном конце модели: на западном конце ряда модель разворачивается
        if (id.equals("steam_drum") && state.getValue(PcProperties.DRUM_END) == PcProperties.DrumEnd.WEST) extra = 180;
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
            // барабан котла реального масштаба: средняя секция или секция с днищем на конце ряда
            case "steam_drum" -> s.getValue(PcProperties.DRUM_END) == PcProperties.DrumEnd.NONE ? "steam_drum_real" : "steam_drum_real_end";
            case "steam_engine_cylinder" -> s.getValue(PcProperties.ENGINE_SEGMENT) == PcProperties.EngineSegment.FRAME ? id + "_frame" : id;
            case "grate_stoker", "main_mine_fan", "plc" -> s.getValue(PcProperties.RUNNING) ? id + "_running" : id;
            case "portable_ventilator" -> s.getValue(PcProperties.RUNNING) ? id + "_on" : id;
            case "water_intake" -> s.getValue(PcProperties.ALARM) ? id + "_fouled" : id;
            case "hazard_sign" -> switch (s.getValue(PcProperties.SEVERITY)) {
                case 1 -> id + "_forbid";
                case 2 -> id + "_mandatory";
                case 3 -> id + "_ghs";
                default -> id;
            };
            case "flare_ignition_panel" -> s.getValue(PcProperties.SEVERITY) == 0 ? id : id + "_pilots" + s.getValue(PcProperties.SEVERITY);
            case "flare_knockout_drum" -> switch (s.getValue(PcProperties.MUD_LEVEL)) {
                case 1 -> id + "_level2";
                case 2 -> id + "_level4";
                default -> id;
            };
            case "sis_logic_solver" -> s.getValue(PcProperties.ALARM) ? id + "_tripped" : id;
            case "chimney_stack" -> switch (s.getValue(PcProperties.CHIMNEY_PART)) {
                case BASE -> id + "_base";
                case TOP -> id + "_cap";
                case MIDDLE -> id;
            };
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
            // регулятор: модель на каждую ступень окна (жалюзи под своим углом), ступень 0 — базовая
            case "ventilation_door" -> s.getValue(PcProperties.DOOR_KIND) == PcProperties.DoorKind.REGULATOR
                    ? id + "_regulator" + switch (s.getValue(PcProperties.WINDOW)) {
                        case 1 -> "_25";
                        case 2 -> "_50";
                        case 3 -> "_100";
                        default -> "";
                    }
                    : s.getValue(BlockStateProperties.OPEN) ? id + "_open" : id;
            case "explosion_barrier" -> s.getValue(PcProperties.BARRIER_KIND) == PcProperties.BarrierKind.WATER ? id + "_water" : id;
            case "mine_dispatch_cabinet" -> s.getValue(PcProperties.ALARM) ? id + "_alarm" : id;
            case "headframe" -> s.getValue(PcProperties.TOP) ? id + "_top" : id;
            case "mine_cage" -> s.getValue(BlockStateProperties.OPEN) ? id + "_open" : id;
            case "shaft_signal" -> s.getValue(BlockStateProperties.POWERED) ? id + "_lit" : id;
            // нефть и газ I2
            case "glycol_regenerator" -> s.getValue(PcProperties.RUNNING) ? id + "_running" : id;
            // НТС нарисована своя: art/blockbench/core/reactor_island.author.js; холодная — под инеем
            case "low_temperature_separator" -> s.getValue(BlockStateProperties.LIT) ? id + "_cold" : id;
            case "helium_recovery_unit" -> s.getValue(PcProperties.RUNNING) ? id + "_running" : id;
            case "fuel_rack" -> "fuel_pool_rack_open";
            case "purex_cell" -> s.getValue(PcProperties.RUNNING) ? id + "_running" : id;
            case "rtu_station" -> s.getValue(BlockStateProperties.LIT) ? id : id + "_off";
            case "mes_server" -> s.getValue(BlockStateProperties.LIT) ? id + "_busy" : id;
            // химия I3
            case "pyrolysis_furnace" -> s.getValue(BlockStateProperties.LIT) ? id + "_lit" : id;
            case "ldpe_reactor", "hydrotreater" -> s.getValue(PcProperties.RUNNING) ? id + "_running" : id;
            // хлор и ПВХ I3
            case "brine_treater" -> s.getValue(PcProperties.RUNNING) ? id + "_running" : id;
            case "rectifier_unit" -> s.getValue(PcProperties.ALARM) ? "rectifier_unit_fault"
                    : s.getValue(PcProperties.RUNNING) ? "rectifier_unit" : "rectifier_unit_off";
            case "chlorine_liquefier" -> s.getValue(PcProperties.RUNNING) ? id + "_running" : id;
            case "edc_cracker" -> s.getValue(PcProperties.ALARM) ? id + "_coked" : s.getValue(BlockStateProperties.LIT) ? id + "_lit" : id;
            // химия I3, азот
            case "steam_reformer" -> s.getValue(BlockStateProperties.LIT) ? id + "_lit" : id;
            case "co2_absorber" -> "absorption_tower";
            case "nitric_acid_unit", "chlor_alkali_cell" -> s.getValue(PcProperties.RUNNING) ? id + "_running" : id;
            case "acid_concentrator" -> s.getValue(BlockStateProperties.LIT) ? id + "_lit" : id;
            // химия I3, метанол
            case "claus_unit" -> s.getValue(PcProperties.RUNNING) ? id + "_running" : id;
            // подготовка и углубление переработки НПЗ, ГПЗ (09 §9.7.3, §9.9–9.10)
            case "electrostatic_desalter" -> s.getValue(PcProperties.RUNNING) ? id + "_powered" : id;
            case "vacuum_column" -> "column_base_insulated";
            case "fcc_unit" -> "fcc_reactor_vessel";
            case "amine_unit" -> "absorption_tower";
            case "gasoline_blender" -> "surge_tank";
            case "quality_lab" -> s.getValue(PcProperties.RUNNING) ? id + "_busy" : id;
            // замедленное коксование нарисовано своё: art/blockbench/refinery/delayed_coker.author.js
            case "methanol_column" -> "fractionating_column";
            // ядерное топливо I4
            case "centrifuge_cascade" -> s.getValue(PcProperties.RUNNING) ? id + "_running" : id;
            case "uo2_conversion" -> s.getValue(BlockStateProperties.LIT) ? id + "_lit" : id;
            case "power_reactor" -> s.getValue(PcProperties.REACTOR_SEGMENT) == PcProperties.ReactorSegment.DRIVES ? id + "_drives" : id;
            // биржа (14): своей модели терминала пока нет — пульт
            case "market_terminal" -> "control_desk";
            case "irradiation_channel" -> s.getValue(PcProperties.FILLED) ? id + "_loaded" : id;
            case "rtg_thermoelectric" -> s.getValue(BlockStateProperties.LIT) ? id : id + "_depleted";
            case "transient_rod_drive" -> s.getValue(BlockStateProperties.TRIGGERED) ? id + "_fired"
                    : s.getValue(BlockStateProperties.ENABLED) ? id + "_armed" : id;
            case "pwr_reactor" -> s.getValue(PcProperties.PWR_SEGMENT) == PcProperties.PwrSegment.HEAD ? id + "_head" : id;
            // реальный масштаб: клетки пустые, цельную модель рисует контроллер
            case "vver_reactor", "vver_steam_generator", "vver_coolant_pump", "vver_pressurizer", "bwr240_reactor", "candu6_reactor",
                 "bn600_reactor", "am1_reactor", "lancashire_boiler", "headframe_steel", "mill_engine", "winding_engine", "guibal_fan",
                 "cupola_furnace_real", "mine_drainage_pump_geared", "lancashire_stoker", "crucible_furnace_real",
                 "hot_blast_stove_real", "blast_blower_engine",
                 "deaerator_real", "ion_exchange_column_real", "shaft_generator_real", "electric_motor_mv_real",
                 "longwall_shearer_real", "coal_jig_real", "quench_tower_real", "powered_roof_support_real" -> "whole_empty";
            case "bof_vessel_real", "reheating_furnace_real" -> "whole_empty";
            case "molding_station_real", "shakeout_grate_real" -> "whole_empty";
            case "machine_lathe_real", "drill_press_real", "milling_machine_real", "shaping_machine_real", "surface_grinder_real" -> "whole_empty";
            case "degasification_pump_real", "forced_draft_fan_real", "induced_draft_fan_real", "magnetic_separator_real",
                 "flotation_cell_real", "electrode_boiler_real" -> "whole_empty";
            case "pressurizer" -> s.getValue(BlockStateProperties.LIT) ? id + "_lit" : id;
            // тяжёлая вода T3
            case "gs_exchange_tower" -> "absorption_tower";
            // глинозём (08 §8.8.1): печь прокалки — модель вращающейся печи 02
            case "alumina_calciner" -> "rotary_calciner";
            case "reduction_pot" -> s.getValue(PcProperties.ALARM) ? id + "_anode_effect" : s.getValue(PcProperties.RUNNING) ? id : id + "_frozen";
            case "flotation_cell" -> s.getValue(PcProperties.RUNNING) ? id + "_froth" : id;
            case "matte_smelter" -> s.getValue(BlockStateProperties.LIT) ? id + "_lit" : id;
            case "electrorefining_cell" -> s.getValue(PcProperties.NICKEL) ? id + "_ni" : id;
            case "pgm_refinery" -> s.getValue(PcProperties.RUNNING) ? id + "_running" : id;
            case "heavy_water_column" -> "fractionating_column";
            // арт: art/blockbench/core/candu_reactor.author.js — свод с торцом каландра, над ним коробка питателей и площадка СУЗ
            case "candu_reactor" -> s.getValue(PcProperties.CANDU_SEGMENT) == PcProperties.CanduSegment.FUELLING ? id + "_fuelling" : id;
            case "submerged_arc_furnace" -> s.getValue(BlockStateProperties.LIT) ? id + "_running" : id;
            case "siemens_reactor" -> s.getValue(PcProperties.RUNNING) ? id + "_running" : id;
            case "czochralski_puller" -> s.getValue(PcProperties.RUNNING) ? id + "_pulling" : id;
            case "cleanroom_airlock" -> s.getValue(BlockStateProperties.OPEN) ? id + "_open" : id;
            case "sfr_reactor" -> s.getValue(PcProperties.SFR_SEGMENT) == PcProperties.SfrSegment.PLUG ? id + "_plug" : id;
            case "containment_hatch" -> s.getValue(BlockStateProperties.OPEN) ? id + "_open" : id;
            case "bwr_reactor" -> s.getValue(PcProperties.BWR_SEGMENT) == PcProperties.BwrSegment.SEPARATORS ? id + "_separators"
                    : s.getValue(BlockStateProperties.LIT) ? id + "_lit" : id;
            case "electrostatic_precipitator" -> s.getValue(BlockStateProperties.POWERED) ? id + "_energized" : id;
            case "cems_analyzer" -> id + "_on";
            case "christmas_tree" -> s.getValue(BlockStateProperties.OPEN) ? id : id + "_master_closed";
            case "pumpjack" -> "pumpjack_gearbox";
            case "flare_tip" -> s.getValue(BlockStateProperties.LIT) ? id + "_lit" : id;
            case "tube_furnace" -> s.getValue(BlockStateProperties.LIT) ? id + "_lit" : id;
            case "drillers_console", "seismic_recorder" -> s.getValue(BlockStateProperties.LIT) ? id + "_on" : id;
            case "bop_annular" -> s.getValue(PcProperties.SHUT) ? id + "_closed" : id;
            case "bop_ram" -> s.getValue(PcProperties.SHUT) ? id + "_closed" : switch (s.getValue(PcProperties.RAM_KIND)) {
                case PIPE -> id;
                case BLIND -> id + "_blind";
                case SHEAR -> id + "_shear";
            };
            case "bop_accumulator" -> s.getValue(PcProperties.CHARGED) ? id + "_charged" : id;
            case "logging_unit" -> id;
            case "core_analysis_station" -> s.getValue(PcProperties.RUNNING) ? id + "_busy" : id;
            case "mud_tank" -> switch (s.getValue(PcProperties.MUD_LEVEL)) {
                case 0 -> id + "_empty";
                case 2 -> id + "_full";
                default -> id;
            };
            case "cupola_furnace", "crucible_furnace", "bloomery", "forge_hearth", "heating_furnace", "hardening_furnace" ->
                    s.getValue(BlockStateProperties.LIT) ? id + "_lit" : id;
            case "sand_mold" -> switch (s.getValue(PcProperties.MOLD_STAGE)) {
                case EMPTY -> id;
                case POURED -> id + "_poured";
                case COOLING -> id + "_cooling";
            };
            case "document_lectern" -> s.getValue(PcProperties.HAS_DOCUMENT) ? id + "_document" : id;
            case "industrial_light", "industrial_light_street", "industrial_light_floodlight", "industrial_light_exit",
                 "ceiling_light_panel", "lamp_fluorescent", "lamp_bulkhead", "lamp_explosion_proof", "lamp_emergency",
                 "lamp_high_bay", "warning_beacon", "lighting_board", "power_socket", "power_socket_3p", "power_board" ->
                    s.getValue(BlockStateProperties.LIT) ? id : id + "_off";
            case "solar_inverter", "battery_inverter" -> s.getValue(BlockStateProperties.LIT) ? id + "_running" : id;
            case "ups_cabinet" -> !s.getValue(BlockStateProperties.LIT) ? id + "_fault" : s.getValue(PcProperties.ALARM) ? id + "_battery" : id;
            // электросеть I1 (этап 8 M3)
            case "electric_motor" -> s.getValue(PcProperties.MEDIUM_VOLTAGE) ? id + "_mv" : id;
            case "disconnector" -> s.getValue(PcProperties.EARTHED) ? id + "_earthed" : id;
            case "dam_gate" -> s.getValue(BlockStateProperties.OPEN) ? id : id + "_closed";
            case "cooling_tower_cell" -> s.getValue(PcProperties.ICED) ? id + "_iced" : id;
            case "diesel_generator" -> s.getValue(PcProperties.DIESEL_SEGMENT) == PcProperties.DieselSegment.ALTERNATOR
                    ? "diesel_generator_alternator" : id;
            case "hrsg" -> s.getValue(PcProperties.HRSG_SEGMENT) == PcProperties.HrsgSegment.DRUM ? "hrsg_top" : "hrsg";
            case "gas_turbine" -> switch (s.getValue(PcProperties.GAS_TURBINE_SEGMENT)) {
                case COMPRESSOR -> id;
                case COMBUSTOR -> id + "_combustor";
                case TURBINE -> id + "_turbine";
            };
            case "switchgear_cell" -> s.getValue(PcProperties.ALARM) ? id + "_alarm"
                    : s.getValue(BlockStateProperties.OPEN) ? id + "_open" : id;
            case "lv_switchboard" -> s.getValue(PcProperties.ALARM) ? id + "_overload"
                    : s.getValue(BlockStateProperties.OPEN) ? id + "_off" : id;
            case "relay_cabinet" -> s.getValue(PcProperties.ALARM) ? id + "_tripped" : id;
            case "lv_distribution_box" -> s.getValue(PcProperties.ALARM) ? id + "_tripped" : id;
            case "station_battery" -> s.getValue(PcProperties.CHARGE) == 4 ? id : id + "_soc" + s.getValue(PcProperties.CHARGE);
            case "grid_consumer" -> s.getValue(BlockStateProperties.LIT) ? id : id + "_dark";
            case "capacitor_bank" -> s.getValue(BlockStateProperties.LIT) ? id + "_on" : id;
            case "electric_arc_furnace" -> s.getValue(PcProperties.ARC_FURNACE_SEGMENT) == PcProperties.ArcFurnaceSegment.SHELL ? id
                    : s.getValue(BlockStateProperties.LIT) ? id + "_roof_arcing" : id + "_roof";
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
            case "byproduct_plant" -> s.getValue(PcProperties.BYPRODUCT_KIND) == PcProperties.ByproductKind.COOLER ? id
                    : id + "_" + s.getValue(PcProperties.BYPRODUCT_KIND).getSerializedName();
            case "baking_furnace", "sinter_strand" -> s.getValue(BlockStateProperties.LIT) ? id + "_lit" : id;
            case "blast_furnace_tuyere" -> s.getValue(PcProperties.BURNT) ? id + "_burnt" : id;
            case "blast_furnace_taphole", "blast_furnace_taphole_real" -> s.getValue(BlockStateProperties.OPEN) ? id + "_open" : id;
            case "blast_furnace_controller", "blast_furnace_controller_real" -> s.getValue(PcProperties.ALARM) ? id + "_alarm" : id;
            case "wind_turbine" -> switch (s.getValue(PcProperties.WIND_TURBINE_SEGMENT)) {
                case BASE -> id;
                case MIDDLE -> id + "_middle";
                case TOP -> id + "_top";
            };
            case "hot_blast_stove" -> switch (s.getValue(PcProperties.STOVE_SEGMENT)) {
                case DOME -> id + "_dome";
                case MIDDLE -> id + "_middle";
                default -> id;
            };
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
            case "smoke_detector", "heat_detector", "flame_detector" -> s.getValue(PcProperties.ALARM) ? id + "_alarm" : id;
            case "manual_call_point" -> s.getValue(PcProperties.ALARM) ? id + "_pressed" : id;
            case "sprinkler_head" -> s.getValue(PcProperties.ALARM) ? id + "_burst" : id;
            case "gas_suppression_cylinder" -> s.getValue(PcProperties.ALARM) ? id + "_discharged" : id;
            case "fire_alarm_panel" -> s.getValue(PcProperties.ALARM) ? id + "_fire"
                    : s.getValue(BlockStateProperties.POWERED) ? id + "_fault" : id;
            case "historian_server" -> s.getValue(BlockStateProperties.LIT) ? id + "_busy" : id;
            case "foam_proportioner" -> s.getValue(BlockStateProperties.POWERED) ? id + "_6" : id;
            case "oil_skimmer", "cp_rectifier" -> s.getValue(PcProperties.RUNNING) ? id + "_on" : id;
            case "oil_boom" -> s.getValue(BlockStateProperties.HORIZONTAL_AXIS) == Direction.Axis.X ? id + "_x" : id;
            case "oiled_sand", "oiled_gravel" -> {
                int age = s.getValue(dev.denfry.promptcritical.mod.hazard.SpillBlocks.OiledShoreBlock.AGE);
                yield age == 0 ? id : id + "_" + age;
            }
            case "gas_seep_vent" -> s.getValue(BlockStateProperties.LIT) ? id + "_burning" : id;
            case "oklo_uraninite_ore" -> "deepslate_uraninite_ore";
            case "training_simulator" -> s.getValue(PcProperties.RUNNING) ? id + "_running" : id;
            case "simulator_console", "instructor_station" -> s.getValue(BlockStateProperties.LIT) ? id + "_active" : id;
            case "grid_dispatch_center" -> s.getValue(PcProperties.ALARM) ? id + "_alarm" : id;
            case "lockout_box" -> switch (s.getValue(PcProperties.LOCKS)) {
                case 0 -> id;
                case 1, 2 -> id + "_locks1";
                case 3, 4, 5 -> id + "_locks3";
                default -> id + "_locks6";
            };
            case "maintenance_panel" -> s.getValue(PcProperties.SEVERITY) == 0 ? id : id + "_severity" + s.getValue(PcProperties.SEVERITY);
            case "recording_gauge" -> s.getValue(PcProperties.PAPER) ? id : id + "_empty";
            case "sand_filter" -> s.getValue(PcProperties.ALARM) ? id + "_backwash" : id;
            // котёл I1 (этап 11б M3)
            case "water_tube_boiler" -> s.getValue(PcProperties.ALARM) ? id + "_trip"
                    : s.getValue(BlockStateProperties.LIT) ? id + "_firing" : id;
            case "boiler_burner" -> id + (s.getValue(PcProperties.GAS_FIRED) ? "_gas" : "")
                    + (s.getValue(BlockStateProperties.LIT) ? "_lit" : "");
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
        BlockState icon = block.defaultBlockState();
        // светильник ставится погашенным, а в инвентаре показывается горящим
        if (block instanceof dev.denfry.promptcritical.mod.grid.LampBlock) icon = icon.setValue(BlockStateProperties.LIT, true);
        itemModel(id, modelName(id, icon), block);
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

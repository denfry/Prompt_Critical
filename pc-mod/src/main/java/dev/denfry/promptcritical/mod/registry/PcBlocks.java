package dev.denfry.promptcritical.mod.registry;

import dev.denfry.promptcritical.mod.ops.HydrotestPumpBlockEntity;
import dev.denfry.promptcritical.mod.ops.MaintenancePanelBlockEntity;
import dev.denfry.promptcritical.mod.ops.RecordingGaugeBlockEntity;
import dev.denfry.promptcritical.mod.ops.WaterTreatmentBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.CanaryCageBlock;
import dev.denfry.promptcritical.mod.content.DecorBlock;
import dev.denfry.promptcritical.mod.content.DocumentLecternBlock;
import dev.denfry.promptcritical.mod.content.WallClockBlock;
import dev.denfry.promptcritical.mod.boiler.FireTubeBoilerBlock;
import dev.denfry.promptcritical.mod.boiler.GrateStokerBlockEntity;
import dev.denfry.promptcritical.mod.boiler.KineticPortBlock;
import dev.denfry.promptcritical.mod.boiler.PressureGaugeBlock;
import dev.denfry.promptcritical.mod.boiler.SteamEngineBlock;
import dev.denfry.promptcritical.mod.block.ChimneyBlock;
import dev.denfry.promptcritical.mod.block.HeadframeBlock;
import dev.denfry.promptcritical.mod.block.MineDispatchCabinetBlock;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.block.PcBlock.Spec;
import dev.denfry.promptcritical.mod.block.PcDeviceBlock;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.block.VentilationDoorBlock;
import dev.denfry.promptcritical.mod.foundry.CoalJigBlockEntity;
import dev.denfry.promptcritical.mod.foundry.CrucibleBlockEntity;
import dev.denfry.promptcritical.mod.foundry.CupolaBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryBlock;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlock;
import dev.denfry.promptcritical.mod.ironworks.BakingFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.BlastBlowerBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.BlastFurnaceControllerBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.BlastFurnaceTopBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.ByproductPlantBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.CastHouseMachineBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.CokeOvenBlock;
import dev.denfry.promptcritical.mod.ironworks.DustCatcherBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.ElectrodeExtruderBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.FurnacePartBlock;
import dev.denfry.promptcritical.mod.ironworks.GasExhausterBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.GasMainBlock;
import dev.denfry.promptcritical.mod.ironworks.HotBlastStoveBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.IronRunnerBlock;
import dev.denfry.promptcritical.mod.ironworks.LadleBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.MagneticSeparatorBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.QuenchTowerBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.SinterStrandBlock;
import dev.denfry.promptcritical.mod.ironworks.SlagGranulatorBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.AirSeparatorBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.AsuCompressorBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.BofVesselBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.CryoTankBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.HeatTreatFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.HydrotestBenchBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.IngotMoldBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.LadleStationBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.MillStandBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.OxygenLanceBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.PiercingMillBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.PipeWelderBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.PortMachineBlock;
import dev.denfry.promptcritical.mod.steelworks.QuenchTankBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.ReheatingFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.RollerTableBlockEntity;
import dev.denfry.promptcritical.mod.mine.DegasificationPumpBlockEntity;
import dev.denfry.promptcritical.mod.mine.LongwallShearerBlock;
import dev.denfry.promptcritical.mod.mine.MainMineFanBlock;
import dev.denfry.promptcritical.mod.mine.MineCageBlock;
import dev.denfry.promptcritical.mod.mine.MineDrainagePumpBlockEntity;
import dev.denfry.promptcritical.mod.mine.MineHoistBlock;
import dev.denfry.promptcritical.mod.mine.PoweredRoofSupportBlock;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlock.ShaftSide;
import dev.denfry.promptcritical.mod.foundry.MachineToolBlockEntity;
import dev.denfry.promptcritical.mod.grid.BreakerCellBlockEntity;
import dev.denfry.promptcritical.mod.grid.CableBlock;
import dev.denfry.promptcritical.mod.grid.ConnectorBlockEntity;
import dev.denfry.promptcritical.mod.grid.ConsumerBlockEntity;
import dev.denfry.promptcritical.mod.grid.DisconnectorBlockEntity;
import dev.denfry.promptcritical.mod.grid.DistributionBoxBlockEntity;
import dev.denfry.promptcritical.mod.grid.ElectricMotorBlockEntity;
import dev.denfry.promptcritical.mod.grid.GridMountedBlock;
import dev.denfry.promptcritical.mod.grid.InstrumentBlockEntity;
import dev.denfry.promptcritical.mod.grid.PoleBlock;
import dev.denfry.promptcritical.mod.grid.RelayCabinetBlockEntity;
import dev.denfry.promptcritical.mod.grid.ShaftGeneratorBlockEntity;
import dev.denfry.promptcritical.mod.grid.TransformerBlockEntity;
import dev.denfry.promptcritical.mod.foundry.MoldingStationBlockEntity;
import dev.denfry.promptcritical.mod.foundry.SandMoldBlock;
import dev.denfry.promptcritical.mod.foundry.ShakeoutGrateBlockEntity;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.denfry.promptcritical.mod.multiblock.Footprint.Segment;
import dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Блоки вех M2–M3 (эпохи I0–I1): реестры 07 §7.22 и 08 §8.20, состояния — по арт-реестрам. Поведение устройств добавляют
 * этапы 6–8; здесь — свойства, объём ({@link Footprint}) и доменный двойник.
 */
public final class PcBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PromptCritical.MOD_ID);

    /** Все блоки мода с предметами, в порядке вкладки. */
    private static final List<DeferredBlock<? extends Block>> WITH_ITEMS = new ArrayList<>();
    /** Блок руды → id сырого предмета (выпадение). */
    private static final Map<DeferredBlock<? extends Block>, String> ORE_DROPS = new LinkedHashMap<>();
    private static final List<DeferredBlock<PcDeviceBlock>> DEVICES = new ArrayList<>();
    /** Руды 02, выпадающие сами собой (с анализом). */
    private static final List<DeferredBlock<PcBlock>> SELF_ASSAYED = new ArrayList<>();
    /** Декор (11 §11.6): в топологию не входит. */
    private static final List<DeferredBlock<? extends Block>> DECOR = new ArrayList<>();
    /** Ванильные формы декора: плита, ступени, стенка → вид и базовый блок (текстура). */
    private static final Map<DeferredBlock<? extends Block>, Form> DECOR_FORMS = new LinkedHashMap<>();

    public record Form(String kind, String base) {
    }

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

    private static Properties tile(MapColor color) {
        return Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops()
                .strength(1.5f, 6.0f);
    }

    private static Properties sheet(MapColor color) {
        return Properties.of().mapColor(color).requiresCorrectToolForDrops().strength(3.0f, 6.0f).sound(SoundType.METAL)
                .noOcclusion();
    }

    private static Properties glassBlock() {
        return Properties.of().mapColor(MapColor.NONE).instrument(NoteBlockInstrument.HAT).strength(1.5f, 6.0f)
                .sound(SoundType.GLASS).noOcclusion().isViewBlocking((st, l, p) -> false).isSuffocating((st, l, p) -> false);
    }

    private static Properties lamp() {
        return Properties.of().mapColor(MapColor.METAL).strength(1.0f).sound(SoundType.LANTERN).noOcclusion()
                .lightLevel(st -> st.getValue(BlockStateProperties.LIT) ? 15 : 0);
    }

    /** Щитовой прибор, изолятор: снимается рукой, разрушается поршнем. */
    private static Properties instrument() {
        return Properties.of().mapColor(MapColor.METAL).strength(1.0f).sound(SoundType.COPPER).noOcclusion()
                .pushReaction(PushReaction.DESTROY);
    }

    private static Properties plate() {
        return Properties.of().mapColor(MapColor.METAL).strength(1.0f).sound(SoundType.METAL).noOcclusion().noCollission();
    }

    /** Клёпаный кожух печи: непрозрачный, поршнем не двигается. */
    private static Properties shell() {
        return Properties.of().mapColor(MapColor.COLOR_GRAY).requiresCorrectToolForDrops().strength(5.0f, 12.0f)
                .sound(SoundType.METAL).pushReaction(PushReaction.BLOCK);
    }

    /** Машина, которую возят контрапции Create (штанга выталкивателя, ковш). */
    private static Properties movable() {
        return Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(3.5f, 6.0f)
                .sound(SoundType.METAL).noOcclusion();
    }

    private static Properties lit(Properties p) {
        return p.lightLevel(s -> s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT) ? 13 : 0);
    }

    // --- объёмы (модельные координаты, фасад на север) ---
    private static final Segment CUBE3 = Segment.box(-1, 1, -1, 1, -1, 1);
    static final Footprint FP_BOILER = Footprint.of(CUBE3, CUBE3.at(0, 0, 3));
    /** Машина 3×3×5 (арт 07 §7.23): цилиндр 3×3×3 и рама 3×2×2 с коленвалом на якоре рамы. */
    public static final Footprint FP_ENGINE = Footprint.of(CUBE3, Segment.box(-1, 1, -1, 0, 0, 1).at(0, 0, 2));
    private static final Footprint FP_CUBE3 = Footprint.of(CUBE3);
    private static final Footprint FP_TALL3 = Footprint.of(Segment.box(0, 0, -1, 1, 0, 0));
    private static final Footprint FP_TALL2 = Footprint.of(Segment.box(0, 0, 0, 1, 0, 0));
    private static final Footprint FP_WIDE2 = Footprint.of(Segment.box(0, 1, 0, 0, 0, 0));
    /** Печь 3×2×3 (обжиговая, агломашина, колошник). */
    private static final Footprint FP_FURNACE3 = Footprint.of(Segment.box(-1, 1, 0, 1, -1, 1));

    // --- котельная I0 (07) ---
    public static final DeferredBlock<PcDeviceBlock> FIRE_TUBE_BOILER = device("fire_tube_boiler", lit(machine()),
            Spec.facing(BlockStateProperties.LIT, PcProperties.LOW_WATER, PcProperties.EXPLODED)
                    .segments(PcProperties.BOILER_SEGMENT, FP_BOILER), FireTubeBoilerBlock::create);
    public static final DeferredBlock<PcDeviceBlock> GRATE_STOKER = device("grate_stoker", machine(), Spec.facing(PcProperties.RUNNING),
            KineticMachineBlock.with(ShaftSide.LEFT, GrateStokerBlockEntity::new));
    public static final DeferredBlock<ChimneyBlock> CHIMNEY = withItem(BLOCKS.registerBlock("chimney", ChimneyBlock::create, brick()));
    public static final DeferredBlock<PcDeviceBlock> STEAM_ENGINE_CYLINDER = device("steam_engine_cylinder", machine(),
            Spec.facing(PcProperties.RUNNING).segments(PcProperties.ENGINE_SEGMENT, FP_ENGINE), SteamEngineBlock::create);
    /** Местный манометр A0 (11 §11.5.4) на любой грани котла или машины. */
    public static final DeferredBlock<PressureGaugeBlock> INSTRUMENT_PRESSURE = withItem(BLOCKS.registerBlock("instrument_pressure",
            PressureGaugeBlock::create, Properties.of().mapColor(MapColor.METAL).strength(1.0f).sound(SoundType.COPPER)
                    .noOcclusion().pushReaction(PushReaction.DESTROY)));

    /** Реле на DIN-рейке: корпус у стены (модель {@code relay}). */
    private static final double[] RELAY_BOX = {0.5, 1, 5.9, 15.5, 15, 16};

    // --- электросеть I1 (07 §7.3–7.6, этап 8 M3) ---
    private static final Footprint FP_DISCONNECTOR = Footprint.of(Segment.box(-1, 1, 0, 1, 0, 0));
    public static final DeferredBlock<PcDeviceBlock> SHAFT_GENERATOR = device("shaft_generator", machine(),
            Spec.facing(PcProperties.RUNNING), KineticMachineBlock.with(ShaftSide.BACK, ShaftGeneratorBlockEntity::new));
    public static final DeferredBlock<PcBlock> GENERATOR_EXCITER = block("generator_exciter", machine(), Spec.facing());
    public static final DeferredBlock<PcDeviceBlock> INSTRUMENT_SYNCHROSCOPE = device("instrument_synchroscope", instrument(),
            Spec.facing(), GridMountedBlock.wall(InstrumentBlockEntity::synchroscope, 2, 2, 10.9, 14, 14, 16));
    public static final DeferredBlock<PcDeviceBlock> ELECTRIC_MOTOR = device("electric_motor", machine(),
            Spec.facing(PcProperties.RUNNING, PcProperties.MEDIUM_VOLTAGE),
            KineticMachineBlock.with(ShaftSide.FRONT, ElectricMotorBlockEntity::new));
    public static final DeferredBlock<CableBlock> BUSBAR = withItem(BLOCKS.registerBlock("busbar", CableBlock::create,
            sheet(MapColor.METAL)));
    public static final DeferredBlock<PcDeviceBlock> DISCONNECTOR = device("disconnector", machine(),
            Spec.facing(BlockStateProperties.OPEN, PcProperties.EARTHED).footprint(FP_DISCONNECTOR),
            FoundryBlock.with(DisconnectorBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> SWITCHGEAR_CELL = device("switchgear_cell", machine(),
            Spec.facing(BlockStateProperties.OPEN, PcProperties.ALARM).footprint(FP_TALL2), FoundryBlock.with(BreakerCellBlockEntity::mv));
    public static final DeferredBlock<PcDeviceBlock> LV_SWITCHBOARD = device("lv_switchboard", machine(),
            Spec.facing(BlockStateProperties.OPEN, PcProperties.ALARM).footprint(FP_TALL2), FoundryBlock.with(BreakerCellBlockEntity::lv));
    public static final DeferredBlock<PcDeviceBlock> RELAY_CABINET = device("relay_cabinet", machine(),
            Spec.facing(PcProperties.ALARM).footprint(FP_TALL2), FoundryBlock.with(RelayCabinetBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> DISTRIBUTION_TRANSFORMER = device("distribution_transformer", machine(),
            Spec.facing().footprint(FP_TALL2), FoundryBlock.with(TransformerBlockEntity::new));
    public static final DeferredBlock<PoleBlock> WOODEN_POLE = withItem(BLOCKS.registerBlock("wooden_pole",
            p -> PoleBlock.create(p, 2.5), wood()));
    public static final DeferredBlock<PoleBlock> CONCRETE_POLE = withItem(BLOCKS.registerBlock("concrete_pole",
            p -> PoleBlock.create(p, 3), Properties.of().mapColor(MapColor.STONE).requiresCorrectToolForDrops().strength(2.0f, 6.0f)
                    .noOcclusion()));
    public static final DeferredBlock<CableBlock> POWER_CABLE = withItem(BLOCKS.registerBlock("power_cable", CableBlock::create,
            Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.6f).sound(SoundType.WOOL).noOcclusion()));
    public static final DeferredBlock<PcDeviceBlock> GRID_CONSUMER = device("grid_consumer", brick(),
            Spec.facing(BlockStateProperties.LIT).footprint(FP_CUBE3), FoundryBlock.with(ConsumerBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> ENERGY_METER = device("energy_meter", instrument(), Spec.facing(),
            GridMountedBlock.wall(InstrumentBlockEntity::meter, 3, 0.5, 10.9, 13, 15, 16));
    public static final DeferredBlock<PcDeviceBlock> WIRE_CONNECTOR_LV = device("wire_connector_lv", instrument(), Spec.facing(),
            GridMountedBlock.wall(ConnectorBlockEntity::lv, 6, 4, 5.4, 10, 10, 16));
    public static final DeferredBlock<PcDeviceBlock> WIRE_CONNECTOR_MV = device("wire_connector_mv", instrument(), Spec.of(),
            GridMountedBlock.floor(ConnectorBlockEntity::mv, 5.5, 0, 5.5, 10.5, 12, 10.5));
    public static final DeferredBlock<PcDeviceBlock> LV_DISTRIBUTION_BOX = device("lv_distribution_box", machine(),
            Spec.facing(PcProperties.ALARM), GridMountedBlock.wall(DistributionBoxBlockEntity::new, 2, 0.5, 10.4, 14, 14, 16));

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
    // руды 02 в жилах 08 (отдельные ID глубинного сланца, как в 02 §2.1.2): выпадает сам блок с ore_assay
    public static final DeferredBlock<PcBlock> PYRITE_ORE = ore02("pyrite_ore");
    public static final DeferredBlock<PcBlock> DEEPSLATE_PYRITE_ORE = ore02("deepslate_pyrite_ore");
    public static final DeferredBlock<PcBlock> GALENA_ORE = ore02("galena_ore");
    public static final DeferredBlock<PcBlock> DEEPSLATE_GALENA_ORE = ore02("deepslate_galena_ore");
    public static final DeferredBlock<PcBlock> FLUORITE_ORE = ore02("fluorite_ore");

    // --- шахта (08 §8.2) ---
    public static final DeferredBlock<PcBlock> MINE_SUPPORT = block("mine_support", wood(),
            Spec.facing(PcProperties.SUPPORT_KIND, PcProperties.DAMAGED).footprint(FP_TALL3));
    public static final DeferredBlock<PcDeviceBlock> POWERED_ROOF_SUPPORT = device("powered_roof_support", machine(),
            Spec.facing(BlockStateProperties.EXTENDED).footprint(Footprint.of(Segment.box(0, 0, -1, 1, 0, 1))),
            PoweredRoofSupportBlock::create);
    public static final DeferredBlock<PcDeviceBlock> LONGWALL_SHEARER = device("longwall_shearer", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(Footprint.of(Segment.box(-1, 1, 0, 0, 0, 0))), LongwallShearerBlock::create);
    public static final DeferredBlock<PcDeviceBlock> MAIN_MINE_FAN = device("main_mine_fan", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(FP_CUBE3), MainMineFanBlock::create);
    public static final DeferredBlock<VentilationDoorBlock> VENTILATION_DOOR = withItem(BLOCKS.registerBlock("ventilation_door",
            p -> VentilationDoorBlock.create(p, Spec.facing(PcProperties.DOOR_KIND, BlockStateProperties.OPEN, PcProperties.WINDOW).footprint(FP_TALL2)), wood()));
    public static final DeferredBlock<PcBlock> EXPLOSION_BARRIER = block("explosion_barrier", wood(), Spec.of(PcProperties.BARRIER_KIND));
    public static final DeferredBlock<PcDeviceBlock> DEGASIFICATION_BOREHOLE = device("degasification_borehole", machine(), Spec.facing());
    public static final DeferredBlock<PcDeviceBlock> DEGASIFICATION_PUMP = device("degasification_pump", machine(), Spec.facing(PcProperties.RUNNING),
            KineticMachineBlock.with(ShaftSide.BACK, DegasificationPumpBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> MINE_DISPATCH_CABINET = device("mine_dispatch_cabinet", machine(),
            Spec.facing(PcProperties.ALARM).footprint(FP_TALL2), MineDispatchCabinetBlock::create);
    public static final DeferredBlock<PcDeviceBlock> MINE_HOIST = device("mine_hoist", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(Footprint.of(Segment.box(-1, 1, -1, 0, -1, 1))), MineHoistBlock::create);
    public static final DeferredBlock<HeadframeBlock> HEADFRAME = withItem(BLOCKS.registerBlock("headframe", HeadframeBlock::create, machine()));
    public static final DeferredBlock<MineCageBlock> MINE_CAGE = withItem(BLOCKS.registerBlock("mine_cage",
            p -> MineCageBlock.create(p, Spec.facing(BlockStateProperties.OPEN).footprint(Footprint.of(Segment.box(0, 1, -1, 1, 0, 1)))),
            machine()));
    public static final DeferredBlock<PcDeviceBlock> MINE_DRAINAGE_PUMP = device("mine_drainage_pump", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(FP_WIDE2),
            KineticMachineBlock.with(ShaftSide.LEFT, MineDrainagePumpBlockEntity::new));

    // --- обогащение, литьё, механический цех (08 §8.3, §8.9, §8.10) ---
    public static final DeferredBlock<PcDeviceBlock> COAL_JIG = device("coal_jig", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(Footprint.of(Segment.box(0, 1, 0, 1, 0, 0))),
            KineticMachineBlock.with(ShaftSide.LEFT, CoalJigBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> CUPOLA_FURNACE = device("cupola_furnace", lit(brick()),
            Spec.of(BlockStateProperties.LIT).footprint(FP_TALL3), KineticMachineBlock.with(ShaftSide.X_AXIS, CupolaBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> CRUCIBLE_FURNACE = device("crucible_furnace", lit(brick()), Spec.facing(BlockStateProperties.LIT),
            FoundryBlock.with(CrucibleBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> SAND_MOLD = device("sand_mold",
            Properties.of().mapColor(MapColor.SAND).strength(0.8f).sound(SoundType.SAND).noOcclusion(), Spec.of(PcProperties.MOLD_STAGE),
            SandMoldBlock.factory());
    public static final DeferredBlock<PcDeviceBlock> MOLDING_STATION = device("molding_station", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(FP_WIDE2), KineticMachineBlock.with(ShaftSide.LEFT, MoldingStationBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> SHAKEOUT_GRATE = device("shakeout_grate", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(Footprint.of(Segment.box(0, 1, 0, 0, 0, 1))),
            KineticMachineBlock.with(ShaftSide.LEFT, ShakeoutGrateBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> MACHINE_LATHE = device("machine_lathe", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(FP_WIDE2), KineticMachineBlock.with(ShaftSide.LEFT, MachineToolBlockEntity::lathe));
    public static final DeferredBlock<PcDeviceBlock> DRILL_PRESS = device("drill_press", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(FP_TALL2), KineticMachineBlock.with(ShaftSide.BACK, MachineToolBlockEntity::drillPress));

    // --- коксохим и домна (08 §8.4–8.5, этап 10 M3) ---
    public static final DeferredBlock<PcDeviceBlock> COKE_OVEN = device("coke_oven", brick().pushReaction(PushReaction.BLOCK),
            Spec.of(PcProperties.OVEN_STATE), CokeOvenBlock::create);
    public static final DeferredBlock<PcBlock> COKE_BATTERY_WALL = block("coke_battery_wall",
            brick().mapColor(MapColor.TERRACOTTA_WHITE).pushReaction(PushReaction.BLOCK), Spec.of(PcProperties.HOT));
    public static final DeferredBlock<GasMainBlock> GAS_COLLECTOR_MAIN = withItem(BLOCKS.registerBlock("gas_collector_main",
            GasMainBlock::create, machine()));
    public static final DeferredBlock<PcBlock> COKE_PUSHER_RAM = block("coke_pusher_ram", movable(), Spec.facing());
    public static final DeferredBlock<PcDeviceBlock> GAS_EXHAUSTER = device("gas_exhauster", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(Footprint.of(Segment.box(0, 1, 0, 1, 0, 0))),
            KineticMachineBlock.with(ShaftSide.FRONT, GasExhausterBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> BYPRODUCT_PLANT = device("byproduct_plant", machine(),
            Spec.facing(PcProperties.BYPRODUCT_KIND).footprint(Footprint.of(Segment.box(0, 1, -1, 1, 0, 1))),
            FoundryBlock.with(ByproductPlantBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> QUENCH_TOWER = device("quench_tower", brick().mapColor(MapColor.STONE),
            Spec.facing(PcProperties.RUNNING).footprint(FP_CUBE3), FoundryBlock.with(QuenchTowerBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> ELECTRODE_EXTRUDER = device("electrode_extruder", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(Footprint.of(Segment.box(-1, 1, 0, 0, 0, 0))),
            KineticMachineBlock.with(ShaftSide.FRONT, ElectrodeExtruderBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> BAKING_FURNACE = device("baking_furnace", lit(brick()),
            Spec.facing(BlockStateProperties.LIT).footprint(FP_FURNACE3), FoundryBlock.with(BakingFurnaceBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> MAGNETIC_SEPARATOR = device("magnetic_separator", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(FP_WIDE2), KineticMachineBlock.with(ShaftSide.LEFT, MagneticSeparatorBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> SINTER_STRAND = device("sinter_strand", lit(machine()),
            Spec.facing(PcProperties.RUNNING, BlockStateProperties.LIT).footprint(FP_FURNACE3), SinterStrandBlock::create);
    public static final DeferredBlock<FurnacePartBlock> BLAST_FURNACE_SHELL = withItem(BLOCKS.registerBlock("blast_furnace_shell",
            p -> FurnacePartBlock.create(p, Spec.of()), shell()));
    public static final DeferredBlock<FurnacePartBlock> BLAST_FURNACE_TUYERE = withItem(BLOCKS.registerBlock("blast_furnace_tuyere",
            p -> FurnacePartBlock.create(p, Spec.facing(PcProperties.BURNT)), shell().noOcclusion()));
    public static final DeferredBlock<FurnacePartBlock> BLAST_FURNACE_TAPHOLE = withItem(BLOCKS.registerBlock("blast_furnace_taphole",
            p -> FurnacePartBlock.create(p, Spec.facing(BlockStateProperties.OPEN)),
            shell().noOcclusion().lightLevel(s -> s.getValue(BlockStateProperties.OPEN) ? 12 : 0)));
    public static final DeferredBlock<PcDeviceBlock> BLAST_FURNACE_CONTROLLER = device("blast_furnace_controller", machine(),
            Spec.facing(PcProperties.ALARM), FoundryBlock.with(BlastFurnaceControllerBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> BLAST_FURNACE_TOP = device("blast_furnace_top", machine(),
            Spec.of().footprint(FP_FURNACE3), FoundryBlock.with(BlastFurnaceTopBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> HOT_BLAST_STOVE = device("hot_blast_stove", shell().noOcclusion(),
            Spec.facing().segments(PcProperties.STOVE_SEGMENT, Footprint.of(CUBE3, CUBE3.at(0, 3, 0), CUBE3.at(0, 6, 0))),
            FoundryBlock.with(HotBlastStoveBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> BLAST_BLOWER = device("blast_blower", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(Footprint.of(Segment.box(0, 1, 0, 1, 0, 1))),
            KineticMachineBlock.with(ShaftSide.FRONT, BlastBlowerBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> DUST_CATCHER = device("dust_catcher", machine(),
            Spec.facing().footprint(Footprint.of(Segment.box(0, 1, -1, 1, 0, 1))), FoundryBlock.with(DustCatcherBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> CAST_HOUSE_MACHINE = device("cast_house_machine", machine(),
            Spec.facing(PcProperties.CAST_HOUSE_KIND).footprint(FP_WIDE2), FoundryBlock.with(CastHouseMachineBlockEntity::new));
    public static final DeferredBlock<IronRunnerBlock> IRON_RUNNER = withItem(BLOCKS.registerBlock("iron_runner", IronRunnerBlock::create,
            brick().pushReaction(PushReaction.BLOCK).lightLevel(s -> s.getValue(PcProperties.FILLED) ? 12 : 0)));
    public static final DeferredBlock<PcDeviceBlock> LADLE = device("ladle", movable().lightLevel(s -> s.hasProperty(PcProperties.FILLED)
            && s.getValue(PcProperties.FILLED) ? 10 : 0), Spec.facing(PcProperties.FILLED, PcProperties.HOT)
            .footprint(Footprint.of(Segment.box(0, 1, 0, 1, 0, 1))), FoundryBlock.with(LadleBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> SLAG_GRANULATOR = device("slag_granulator", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(FP_WIDE2), FoundryBlock.with(SlagGranulatorBlockEntity::new));

    // --- сталь, прокат, трубы, ВРУ (08 §8.6–8.7, §8.11, этап 10б M3) ---
    public static final DeferredBlock<PcDeviceBlock> BOF_VESSEL = device("bof_vessel", lit(machine()),
            Spec.facing(PcProperties.RUNNING).footprint(FP_FURNACE3),
            PortMachineBlock.with(new BlockPos(-1, 0, 0), false, BofVesselBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> OXYGEN_LANCE = device("oxygen_lance", machine(),
            Spec.of(PcProperties.LOWERED), FoundryBlock.with(OxygenLanceBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> LADLE_STATION = device("ladle_station", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(FP_FURNACE3), FoundryBlock.with(LadleStationBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> INGOT_MOLD = device("ingot_mold", movable().lightLevel(s ->
            s.hasProperty(PcProperties.FILLED) && s.getValue(PcProperties.FILLED) ? 10 : 0),
            Spec.of(PcProperties.FILLED).footprint(FP_TALL2), FoundryBlock.with(IngotMoldBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> REHEATING_FURNACE = device("reheating_furnace", lit(brick()),
            Spec.facing(BlockStateProperties.LIT).footprint(FP_FURNACE3), FoundryBlock.with(ReheatingFurnaceBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> ROLLER_TABLE = device("roller_table", machine(),
            Spec.facing(PcProperties.ROLLER_KIND), KineticMachineBlock.with(ShaftSide.THROUGH, RollerTableBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> ROLLING_MILL_STAND = device("rolling_mill_stand", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(Footprint.of(Segment.box(-1, 1, -1, 1, 0, 0))),
            PortMachineBlock.with(new BlockPos(-1, 0, 0), false, MillStandBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> PIERCING_MILL = device("piercing_mill", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(Footprint.of(Segment.box(-1, 1, 0, 1, 0, 0))),
            PortMachineBlock.with(new BlockPos(-1, 0, 0), true, PiercingMillBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> PIPE_WELDER = device("pipe_welder", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(Footprint.of(Segment.box(-1, 1, 0, 1, 0, 0))),
            PortMachineBlock.with(new BlockPos(-1, 0, 0), true, PipeWelderBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> HYDROTEST_BENCH = device("hydrotest_bench", machine(),
            Spec.facing(PcProperties.RUNNING, PcProperties.BURST).footprint(Footprint.of(Segment.box(-1, 1, 0, 0, 0, 0))),
            FoundryBlock.with(HydrotestBenchBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> HEAT_TREATMENT_FURNACE = device("heat_treatment_furnace", lit(brick()),
            Spec.facing(BlockStateProperties.LIT, BlockStateProperties.OPEN).footprint(Footprint.of(Segment.box(0, 1, 0, 1, 0, 1))),
            FoundryBlock.with(HeatTreatFurnaceBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> QUENCH_TANK = device("quench_tank", machine(),
            Spec.facing(PcProperties.QUENCH_MEDIUM).footprint(Footprint.of(Segment.box(0, 1, 0, 0, 0, 1))),
            FoundryBlock.with(QuenchTankBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> AIR_SEPARATOR = device("air_separator", machine(),
            Spec.facing(PcProperties.RUNNING), FoundryBlock.with(AirSeparatorBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> ASU_MAIN_COMPRESSOR = device("asu_main_compressor", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(Footprint.of(Segment.box(-1, 1, 0, 1, 0, 1))),
            PortMachineBlock.with(new BlockPos(-1, 0, 0), false, AsuCompressorBlockEntity::new));
    public static final DeferredBlock<PcBlock> ASU_MOLSIEVE = block("asu_molsieve", machine(), Spec.facing().footprint(FP_TALL3));
    public static final DeferredBlock<PcBlock> ASU_EXPANDER = block("asu_expander", machine(), Spec.facing());
    public static final DeferredBlock<PcDeviceBlock> CRYOGENIC_TANK = device("cryogenic_tank", machine(),
            Spec.facing(PcProperties.CRYO_KIND).footprint(FP_TALL3), FoundryBlock.with(CryoTankBlockEntity::new));
    // --- эксплуатация и водоподготовка (13 §13.6.5–13.6.7, 08 §8.12, этап 11а M3) ---
    public static final DeferredBlock<PcDeviceBlock> MAINTENANCE_PANEL = device("maintenance_panel", instrument(),
            Spec.facing(PcProperties.SEVERITY), GridMountedBlock.wall(MaintenancePanelBlockEntity::new, 1, 1, 13, 15, 15, 16));
    public static final DeferredBlock<PcDeviceBlock> HYDROTEST_PUMP = device("hydrotest_pump", machine(),
            Spec.facing(PcProperties.PUMPING), FoundryBlock.with(HydrotestPumpBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> RECORDING_GAUGE = device("recording_gauge", instrument(),
            Spec.facing(PcProperties.PAPER), GridMountedBlock.wall(RecordingGaugeBlockEntity::new, 3, 2, 12, 13, 15, 16));
    public static final DeferredBlock<PcDeviceBlock> WATER_CLARIFIER = device("water_clarifier", machine(),
            Spec.facing(PcProperties.RUNNING, PcProperties.ALARM).footprint(FP_FURNACE3), FoundryBlock.with(WaterTreatmentBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> SAND_FILTER = device("sand_filter", machine(),
            Spec.facing(PcProperties.RUNNING, PcProperties.ALARM).footprint(FP_TALL2), FoundryBlock.with(WaterTreatmentBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> ION_EXCHANGE_COLUMN = device("ion_exchange_column", machine(),
            Spec.facing(PcProperties.RUNNING, PcProperties.ALARM), FoundryBlock.with(WaterTreatmentBlockEntity::new));

    // --- справочник и мир (11) ---
    public static final DeferredBlock<DocumentLecternBlock> DOCUMENT_LECTERN = withItem(BLOCKS.registerBlock("document_lectern",
            p -> DocumentLecternBlock.create(p, Spec.facing(PcProperties.HAS_DOCUMENT)), wood()));
    public static final DeferredBlock<CanaryCageBlock> CANARY_CAGE = withItem(BLOCKS.registerBlock("canary_cage",
            p -> CanaryCageBlock.create(p, Spec.of(PcProperties.BIRD)),
            Properties.of().mapColor(MapColor.METAL).strength(0.5f).sound(SoundType.LANTERN).noOcclusion()));

    // --- декор I0 (11 §11.6.2): кафель, керамогранит, крашеный бетон, стеклоблоки, профнастил, площадки, свет ---
    public static final DeferredBlock<PcBlock> TILE_WHITE = decorCube("tile_white", MapColor.SNOW, true);
    public static final DeferredBlock<PcBlock> TILE_BLUE = decorCube("tile_blue", MapColor.COLOR_LIGHT_BLUE, true);
    public static final DeferredBlock<PcBlock> TILE_GREEN = decorCube("tile_green", MapColor.COLOR_LIGHT_GREEN, true);
    public static final DeferredBlock<PcBlock> PORCELAIN_TILE = decorCube("porcelain_tile", MapColor.STONE, true);
    public static final DeferredBlock<PcBlock> PORCELAIN_TILE_BEIGE = decorCube("porcelain_tile_beige", MapColor.SAND, true);
    public static final DeferredBlock<PcBlock> PAINTED_CONCRETE = decorCube("painted_concrete", MapColor.COLOR_LIGHT_GRAY, true);
    public static final DeferredBlock<PcBlock> PAINTED_CONCRETE_YELLOW = decorCube("painted_concrete_yellow", MapColor.COLOR_YELLOW, true);
    public static final DeferredBlock<PcBlock> PAINTED_CONCRETE_RED = decorCube("painted_concrete_red", MapColor.COLOR_RED, true);
    public static final DeferredBlock<PcBlock> PAINTED_CONCRETE_TWO_TONE = decorCube("painted_concrete_two_tone", MapColor.COLOR_GREEN, false);
    public static final DeferredBlock<PcBlock> PAINTED_CONCRETE_TWO_TONE_BLUE = decorCube("painted_concrete_two_tone_blue", MapColor.COLOR_BLUE, false);
    public static final DeferredBlock<PcBlock> PAINTED_CONCRETE_TWO_TONE_BEIGE = decorCube("painted_concrete_two_tone_beige", MapColor.SAND, false);
    public static final DeferredBlock<PcBlock> GLASS_BLOCK = decorPlain("glass_block", glassBlock(), Spec.of());
    public static final DeferredBlock<PcBlock> GLASS_BLOCK_FROSTED = decorPlain("glass_block_frosted", glassBlock(), Spec.of());
    public static final DeferredBlock<PcBlock> CORRUGATED_STEEL = decorPlain("corrugated_steel", sheet(MapColor.COLOR_GRAY), Spec.of());
    public static final DeferredBlock<PcBlock> CORRUGATED_STEEL_BLUE = decorPlain("corrugated_steel_blue", sheet(MapColor.COLOR_BLUE), Spec.of());
    public static final DeferredBlock<PcBlock> CORRUGATED_STEEL_GREEN = decorPlain("corrugated_steel_green", sheet(MapColor.COLOR_GREEN), Spec.of());
    public static final DeferredBlock<PcBlock> CORRUGATED_STEEL_RED = decorPlain("corrugated_steel_red", sheet(MapColor.COLOR_RED), Spec.of());
    public static final DeferredBlock<PcBlock> CORRUGATED_STEEL_WHITE = decorPlain("corrugated_steel_white", sheet(MapColor.SNOW), Spec.of());
    public static final DeferredBlock<DecorBlock> GRATING = decor("grating", sheet(MapColor.METAL), Spec.of(), 0, 12, 0, 16, 16, 16);
    public static final DeferredBlock<DecorBlock> GRATING_STEP = decor("grating_step", sheet(MapColor.METAL), Spec.facing(), 0, 0, 0, 16, 16, 16);
    public static final DeferredBlock<DecorBlock> HANDRAIL = decor("handrail", sheet(MapColor.COLOR_YELLOW), Spec.facing(), 0, 0, 7, 16, 24, 8.5);
    public static final DeferredBlock<DecorBlock> HANDRAIL_GALVANIZED = decor("handrail_galvanized", sheet(MapColor.METAL), Spec.facing(),
            0, 0, 7, 16, 24, 8.5);
    public static final DeferredBlock<DecorBlock> CAGE_LADDER = decorOf(BLOCKS.registerBlock("cage_ladder",
            p -> DecorBlock.create(p, Spec.facing(), new double[] {1, 0, 13, 15, 16, 15}, true), sheet(MapColor.METAL)));
    public static final DeferredBlock<DecorBlock> INDUSTRIAL_LIGHT = decor("industrial_light", lamp(),
            Spec.of(BlockStateProperties.LIT), 3, 5, 3, 13, 16, 13);
    public static final DeferredBlock<DecorBlock> INDUSTRIAL_LIGHT_STREET = decor("industrial_light_street", lamp(),
            Spec.facing(BlockStateProperties.LIT), 5, 8.5, 1, 11, 12, 16);
    public static final DeferredBlock<DecorBlock> INDUSTRIAL_LIGHT_FLOODLIGHT = decor("industrial_light_floodlight", lamp(),
            Spec.facing(BlockStateProperties.LIT), 3, 0, 3.5, 13, 12, 10);
    public static final DeferredBlock<DecorBlock> INDUSTRIAL_LIGHT_EXIT = decor("industrial_light_exit", lamp(),
            Spec.facing(BlockStateProperties.LIT), 3, 5, 13.9, 13, 11, 16);
    public static final DeferredBlock<DecorBlock> ROOM_SIGN = decor("room_sign", plate(), Spec.facing(), 2, 5, 15.5, 14, 11, 16);
    public static final DeferredBlock<DecorBlock> ROOM_SIGN_EVAC = decor("room_sign_evac", plate(), Spec.facing(), 2, 5, 15.5, 14, 11, 16);
    public static final DeferredBlock<WallClockBlock> WALL_CLOCK = decorOf(BLOCKS.registerBlock("wall_clock",
            p -> WallClockBlock.create(p, Spec.facing(), new double[] {2, 2, 14.4, 14, 14, 16}), plate()));
    public static final DeferredBlock<DecorBlock> SAFETY_POSTER = decor("safety_poster", plate(), Spec.facing(), 0, 0, 15.75, 16, 16, 16);
    public static final DeferredBlock<DecorBlock> SAFETY_POSTER_LARGE = decor("safety_poster_large", plate(), Spec.facing(),
            0, 0, 15.75, 16, 16, 16);
    public static final DeferredBlock<DecorBlock> CURB = decor("curb", Properties.of().mapColor(MapColor.STONE)
            .requiresCorrectToolForDrops().strength(1.5f, 6.0f), Spec.facing(), 0, 0, 5, 16, 6, 11);

    /** Шахтная вода в выработках — затопление (08 §8.2.9); предмета нет. */
    public static final DeferredBlock<LiquidBlock> MINE_WATER = BLOCKS.registerBlock("mine_water",
            p -> new LiquidBlock(PcFluids.MINE_WATER.source.get(), p),
            Properties.ofFullCopy(Blocks.WATER).mapColor(MapColor.TERRACOTTA_ORANGE).noLootTable());

    // --- служебные ---
    /** Клетка конструкции, через которую машина отдаёт вращение в сеть Create (03a §3a.12.3). */
    public static final DeferredBlock<KineticPortBlock> KINETIC_PORT = BLOCKS.registerBlock("kinetic_port",
            KineticPortBlock::new, Properties.of().mapColor(MapColor.METAL).strength(3.5f, 6.0f).noOcclusion()
                    .noLootTable().pushReaction(PushReaction.BLOCK).isViewBlocking((s, l, p) -> false));
    public static final DeferredBlock<MultiblockPartBlock> MULTIBLOCK_PART = BLOCKS.registerBlock("multiblock_part",
            MultiblockPartBlock::new, Properties.of().mapColor(MapColor.METAL).strength(3.5f, 6.0f).noOcclusion()
                    .noLootTable().pushReaction(PushReaction.BLOCK).isViewBlocking((s, l, p) -> false));
    // --- автоматика A1–A2 и пульт (03d, 03c, этап 9 M3) ---
    public static final DeferredBlock<PcDeviceBlock> RELAY = automationWall("relay", Spec.facing(PcProperties.PICKED), RELAY_BOX);
    public static final DeferredBlock<PcDeviceBlock> RELAY_LATCHING = automationWall("relay_latching", Spec.facing(PcProperties.PICKED),
            RELAY_BOX);
    public static final DeferredBlock<PcDeviceBlock> TIME_RELAY = automationWall("time_relay",
            Spec.facing(PcProperties.RUNNING, PcProperties.DONE), 0.5, 1, 8, 15.5, 15, 16);
    public static final DeferredBlock<PcDeviceBlock> STEPPING_SWITCH = automationBlock("stepping_switch", Spec.facing());
    public static final DeferredBlock<PcDeviceBlock> CAM_CONTROLLER = automationBlock("cam_controller", Spec.facing());
    public static final DeferredBlock<PcDeviceBlock> PROCESS_SWITCH = automationWall("process_switch",
            Spec.facing(PcProperties.PROCESS_KIND, PcProperties.ALARM), 4, 0, 9.4, 12, 15.5, 16);
    public static final DeferredBlock<PcDeviceBlock> RELAY_POWER_SUPPLY = automationWall("relay_power_supply",
            Spec.facing(BlockStateProperties.POWERED, PcProperties.ALARM), 0.5, 1, 8.4, 15.5, 15, 16);
    public static final DeferredBlock<PcDeviceBlock> RELAY_RACK = device("relay_rack", machine(),
            Spec.facing(BlockStateProperties.OPEN).footprint(FP_TALL2),
            FoundryBlock.with(dev.denfry.promptcritical.mod.automation.RelayRackBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> REDSTONE_INTERFACE = automationWall("redstone_interface",
            Spec.facing(BlockStateProperties.POWERED), RELAY_BOX);
    public static final DeferredBlock<PcDeviceBlock> PUSHBUTTON = automationWall("pushbutton", Spec.facing(BlockStateProperties.LIT),
            0, 0, 10.97, 16, 16, 16);
    public static final DeferredBlock<PcDeviceBlock> SELECTOR_SWITCH = automationWall("selector_switch", Spec.facing(), 0, 0, 12.5, 16, 16, 16);
    public static final DeferredBlock<PcDeviceBlock> KEY_SWITCH = automationWall("key_switch", Spec.facing(), 0, 0, 12.5, 16, 16, 16);
    public static final DeferredBlock<PcDeviceBlock> INDICATOR_LAMP = automationWall("indicator_lamp", Spec.facing(BlockStateProperties.LIT),
            0, 0, 10.5, 16, 16, 16);
    public static final DeferredBlock<PcDeviceBlock> GAS_DETECTOR = automationWall("gas_detector", Spec.facing(PcProperties.GAS_ALARM),
            4, 2, 8, 12.5, 15, 16);
    public static final DeferredBlock<PcDeviceBlock> ANALOG_SUMMER = automationBlock("analog_summer", Spec.facing());
    public static final DeferredBlock<PcDeviceBlock> ANALOG_INTEGRATOR = automationBlock("analog_integrator", Spec.facing());
    public static final DeferredBlock<PcDeviceBlock> ANALOG_DIFFERENTIATOR = automationBlock("analog_differentiator", Spec.facing());
    public static final DeferredBlock<PcDeviceBlock> ANALOG_MULTIPLIER = automationBlock("analog_multiplier", Spec.facing());
    public static final DeferredBlock<PcDeviceBlock> ANALOG_FUNCTION = automationBlock("analog_function", Spec.facing());
    public static final DeferredBlock<PcDeviceBlock> ANALOG_COMPARATOR = automationBlock("analog_comparator", Spec.facing(PcProperties.ALARM));
    public static final DeferredBlock<PcDeviceBlock> MANUAL_STATION = automationWall("manual_station", Spec.facing(PcProperties.MANUAL),
            0, 0, 4.5, 16, 16, 16);
    public static final DeferredBlock<PcDeviceBlock> SIGNAL_CONVERTER = automationWall("signal_converter", Spec.facing(), 0.5, 1, 9, 15.5, 15, 16);


    private PcBlocks() {
    }

    private static <B extends Block> DeferredBlock<B> withItem(DeferredBlock<B> b) {
        WITH_ITEMS.add(b);
        return b;
    }

    /** Блок декора с формами: плита и ступени, у крашеного бетона ещё стенка (11 §11.6.2). */
    private static DeferredBlock<PcBlock> decorCube(String name, MapColor color, boolean forms) {
        DeferredBlock<PcBlock> base = decorPlain(name, tile(color), Spec.of());
        if (forms) {
            DECOR_FORMS.put(decorOf(BLOCKS.registerBlock(name + "_slab", SlabBlock::new, tile(color))), new Form("slab", name));
            DECOR_FORMS.put(decorOf(BLOCKS.registerBlock(name + "_stairs",
                    p -> new StairBlock(base.get().defaultBlockState(), p), tile(color))), new Form("stairs", name));
            if (name.startsWith("painted_concrete"))
                DECOR_FORMS.put(decorOf(BLOCKS.registerBlock(name + "_wall", WallBlock::new, tile(color).forceSolidOn())),
                        new Form("wall", name));
        }
        return base;
    }

    private static DeferredBlock<DecorBlock> decor(String name, Properties props, Spec spec, double... box) {
        return decorOf(BLOCKS.registerBlock(name, p -> DecorBlock.create(p, spec, box, false), props));
    }

    private static DeferredBlock<PcBlock> decorPlain(String name, Properties props, Spec spec) {
        return decorOf(BLOCKS.registerBlock(name, p -> PcBlock.create(p, spec), props));
    }

    private static <B extends Block> DeferredBlock<B> decorOf(DeferredBlock<B> b) {
        DECOR.add(b);
        return withItem(b);
    }

    private static DeferredBlock<PcBlock> block(String name, Properties props, Spec spec) {
        return withItem(BLOCKS.registerBlock(name, p -> PcBlock.create(p, spec), props));
    }

    private static DeferredBlock<PcDeviceBlock> automationWall(String name, Spec spec, double... box) {
        return device(name, instrument(), spec, dev.denfry.promptcritical.mod.automation.AutomationWallBlock.wall(
                (pos, state) -> dev.denfry.promptcritical.mod.automation.AutomationBlockEntities.create(name, pos, state), box));
    }

    private static DeferredBlock<PcDeviceBlock> automationBlock(String name, Spec spec) {
        return device(name, machine(), spec,
                FoundryBlock.with((pos, state) -> dev.denfry.promptcritical.mod.automation.AutomationBlockEntities.create(name, pos, state)));
    }

    private static DeferredBlock<PcDeviceBlock> device(String name, Properties props, Spec spec) {
        return device(name, props, spec, PcDeviceBlock::create);
    }

    private static DeferredBlock<PcDeviceBlock> device(String name, Properties props, Spec spec,
            BiFunction<Properties, Spec, ? extends PcDeviceBlock> factory) {
        DeferredBlock<PcDeviceBlock> b = withItem(BLOCKS.registerBlock(name, p -> factory.apply(p, spec), props));
        DEVICES.add(b);
        return b;
    }

    private static DeferredBlock<PcBlock> ore02(String name) {
        DeferredBlock<PcBlock> b = block(name, ore(), Spec.of());
        SELF_ASSAYED.add(b);
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

    /** Руды, которые выпадают сами и несут {@code ore_assay}. */
    public static List<DeferredBlock<PcBlock>> selfAssayed() {
        return Collections.unmodifiableList(SELF_ASSAYED);
    }

    public static Map<DeferredBlock<? extends Block>, Form> decorForms() {
        return Collections.unmodifiableMap(DECOR_FORMS);
    }

    public static List<DeferredBlock<PcDeviceBlock>> devices() {
        return Collections.unmodifiableList(DEVICES);
    }

    public static List<DeferredBlock<? extends Block>> decor() {
        return Collections.unmodifiableList(DECOR);
    }

    /** Блоки, состояние которых участвует в топологии (05 §5.3.3): всё, кроме руд и декора. */
    public static List<Block> topologyRelevant() {
        List<Block> out = new ArrayList<>();
        for (DeferredBlock<? extends Block> b : WITH_ITEMS) {
            if (!ORE_DROPS.containsKey(b) && !SELF_ASSAYED.contains(b) && !DECOR.contains(b) && b != FIRECLAY && b != VEIN_QUARTZ)
                out.add(b.get());
        }
        out.add(MULTIBLOCK_PART.get());
        out.add(KINETIC_PORT.get());
        return out;
    }
}

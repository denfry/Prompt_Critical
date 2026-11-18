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
import dev.denfry.promptcritical.mod.foundry.ProductionToolBlockEntity;
import dev.denfry.promptcritical.mod.grid.BreakerCellBlockEntity;
import dev.denfry.promptcritical.mod.grid.CableBlock;
import dev.denfry.promptcritical.mod.grid.ConnectorBlockEntity;
import dev.denfry.promptcritical.mod.grid.ConsumerBlockEntity;
import dev.denfry.promptcritical.mod.grid.DisconnectorBlockEntity;
import dev.denfry.promptcritical.mod.grid.DistributionBoxBlockEntity;
import dev.denfry.promptcritical.mod.grid.ElectricMotorBlockEntity;
import dev.denfry.promptcritical.mod.grid.GridMountedBlock;
import dev.denfry.promptcritical.mod.grid.LampBlock;
import dev.denfry.promptcritical.mod.grid.LampKind;
import dev.denfry.promptcritical.mod.grid.LightingBoardBlockEntity;
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
    private static final List<DeferredBlock<PcBlock>> GEOLOGY = new ArrayList<>();
    /** Руды 02, выпадающие сами собой (с анализом). */
    private static final List<DeferredBlock<PcBlock>> SELF_ASSAYED = new ArrayList<>();
    /** Декор (11 §11.6): в топологию не входит. */
    private static final List<DeferredBlock<? extends Block>> DECOR = new ArrayList<>();
    /** Ванильные формы декора: плита, ступени, стенка → вид и базовый блок (текстура). */
    private static final Map<DeferredBlock<? extends Block>, Form> DECOR_FORMS = new LinkedHashMap<>();
    /** Светильники с питанием от щитка освещения (один тип BE на все). */
    public static final List<DeferredBlock<LampBlock>> LAMPS = new ArrayList<>();

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

    private static Properties containment() {
        return Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops().strength(50.0f, 1200.0f).pushReaction(PushReaction.BLOCK);
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

    /** Графит ядерной чистоты: кладка котла, колется киркой. */
    private static Properties graphite() {
        return Properties.of().mapColor(MapColor.COLOR_BLACK).instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops().strength(2.0f, 6.0f).sound(SoundType.STONE).noOcclusion();
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
    /** Башня 1×9×1 из трёх секций по три блока; контроллер — средний блок нижней. */
    private static final Segment TOWER3 = Segment.box(0, 0, -1, 1, 0, 0);
    private static final Footprint FP_WIND_TOWER = Footprint.of(TOWER3, TOWER3.at(0, 3, 0), TOWER3.at(0, 6, 0));
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

    /** Вибродатчик на грани машины Create: общий уровень виброскорости — аналоговым редстоуном (04a §4a.8.3). */
    public static final DeferredBlock<dev.denfry.promptcritical.mod.acoustics.VibrationTransmitterBlock> INSTRUMENT_VIBRATION = withItem(
            BLOCKS.registerBlock("instrument_vibration", dev.denfry.promptcritical.mod.acoustics.VibrationTransmitterBlock::create,
                    Properties.of().mapColor(MapColor.METAL).strength(1.0f).sound(SoundType.COPPER).noOcclusion()
                            .pushReaction(PushReaction.DESTROY)));

    /** Реле на DIN-рейке: корпус у стены (модель {@code relay}). */
    private static final double[] RELAY_BOX = {0.5, 1, 5.9, 15.5, 15, 16};

    // --- электросеть I1 (07 §7.3–7.6, этап 8 M3) ---
    private static final Footprint FP_DISCONNECTOR = Footprint.of(Segment.box(-1, 1, 0, 1, 0, 0));
    /** Пульт диспетчера энергосистемы (07 §7.6.6), CC-периферия {@code pc_grid}. */
    public static final DeferredBlock<PcDeviceBlock> GRID_DISPATCH_CENTER = device("grid_dispatch_center", instrument(),
            Spec.facing(PcProperties.ALARM), FoundryBlock.with(dev.denfry.promptcritical.mod.grid.GridDispatchCenterBlockEntity::new));
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
    /** Батарея статических конденсаторов 6 кВ: ступени по 150 квар. */
    public static final DeferredBlock<PcDeviceBlock> CAPACITOR_BANK = device("capacitor_bank", machine().noOcclusion(),
            Spec.facing(BlockStateProperties.LIT), FoundryBlock.with(dev.denfry.promptcritical.mod.grid.CapacitorBankBlockEntity::new));
    /** Дуговая печь ДСП-6: корпус 3×3×3, свод сверху; 6 кВ — кабелем к корпусу, ковш — перед носком. */
    public static final DeferredBlock<PcDeviceBlock> ELECTRIC_ARC_FURNACE = device("electric_arc_furnace", machine().noOcclusion(),
            Spec.facing(BlockStateProperties.LIT).segments(PcProperties.ARC_FURNACE_SEGMENT,
                    Footprint.of(CUBE3, Segment.box(-1, 1, -1, 0, -1, 1).at(0, 3, 0))),
            FoundryBlock.with(dev.denfry.promptcritical.mod.steelworks.ArcFurnaceBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> ENERGY_METER = device("energy_meter", instrument(), Spec.facing(),
            GridMountedBlock.wall(InstrumentBlockEntity::meter, 3, 0.5, 10.9, 13, 15, 16));
    public static final DeferredBlock<PcDeviceBlock> WIRE_CONNECTOR_LV = device("wire_connector_lv", instrument(), Spec.facing(),
            GridMountedBlock.wall(ConnectorBlockEntity::lv, 6, 4, 5.4, 10, 10, 16));
    public static final DeferredBlock<PcDeviceBlock> WIRE_CONNECTOR_MV = device("wire_connector_mv", instrument(), Spec.of(),
            GridMountedBlock.floor(ConnectorBlockEntity::mv, 5.5, 0, 5.5, 10.5, 12, 10.5));
    public static final DeferredBlock<PcDeviceBlock> LIGHTING_BOARD = device("lighting_board", machine(),
            Spec.facing(BlockStateProperties.LIT), GridMountedBlock.wall(LightingBoardBlockEntity::new, 3, 1, 12.5, 13, 15, 16));
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
    // урановая смолка в гидротермальных жилах (уранинит, I1): выпадает сырой уранинит
    public static final DeferredBlock<PcBlock> URANINITE_ORE = oreBlock("uraninite_ore", "raw_uraninite", Spec.of());
    public static final DeferredBlock<PcBlock> DEEPSLATE_URANINITE_ORE = oreBlock("deepslate_uraninite_ore", "raw_uraninite",
            Spec.of());

    // --- графитовый котёл T0 (I1, 01 §1.14.4) ---
    public static final DeferredBlock<PcBlock> MODERATOR_BLOCK_GRAPHITE = block("moderator_block_graphite", graphite(), Spec.of());
    public static final DeferredBlock<PcDeviceBlock> FUEL_CHANNEL = device("fuel_channel", graphite(), Spec.of(),
            dev.denfry.promptcritical.mod.foundry.FoundryBlock.with(dev.denfry.promptcritical.mod.reactor.FuelChannelBlockEntity::new));
    public static final DeferredBlock<PcBlock> CONTROL_ROD_CHANNEL = block("control_rod_channel", graphite(), Spec.of());
    public static final DeferredBlock<PcBlock> STARTUP_SOURCE = block("startup_source", instrument(), Spec.of());
    // --- изотопы (12): канал облучения в кладке T0 или у корпуса T1, термоэлектрический РИТЭГ ---
    public static final DeferredBlock<PcDeviceBlock> IRRADIATION_CHANNEL = device("irradiation_channel", graphite(), Spec.of(PcProperties.FILLED),
            dev.denfry.promptcritical.mod.foundry.FoundryBlock.with(dev.denfry.promptcritical.mod.isotopes.IrradiationChannelBlockEntity::new));
    // --- кремний и микросхемы (11 §11.5): печь, хлорсиланы, Сименс, Чохральский, резка, чистая комната ---
    public static final DeferredBlock<PcDeviceBlock> SUBMERGED_ARC_FURNACE = device("submerged_arc_furnace", lit(brick()),
            Spec.facing(BlockStateProperties.LIT),
            dev.denfry.promptcritical.mod.foundry.FoundryBlock.with(dev.denfry.promptcritical.mod.electronics.SubmergedArcFurnaceBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> TRICHLOROSILANE_REACTOR = device("trichlorosilane_reactor", machine(), Spec.facing(),
            dev.denfry.promptcritical.mod.foundry.FoundryBlock.with(dev.denfry.promptcritical.mod.electronics.TrichlorosilaneReactorBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> SIEMENS_REACTOR = device("siemens_reactor", machine(), Spec.facing(PcProperties.RUNNING),
            dev.denfry.promptcritical.mod.foundry.FoundryBlock.with(dev.denfry.promptcritical.mod.electronics.SiemensReactorBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> CZOCHRALSKI_PULLER = device("czochralski_puller", machine(),
            Spec.facing(PcProperties.RUNNING),
            KineticMachineBlock.with(ShaftSide.BACK, dev.denfry.promptcritical.mod.electronics.CzochralskiPullerBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> WAFER_SAW = device("wafer_saw", machine(), Spec.facing(PcProperties.RUNNING),
            KineticMachineBlock.with(ShaftSide.BACK, dev.denfry.promptcritical.mod.electronics.WaferSawBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> CLEANROOM_AIRLOCK = device("cleanroom_airlock", machine(),
            Spec.facing(BlockStateProperties.OPEN, PcProperties.RUNNING),
            dev.denfry.promptcritical.mod.foundry.FoundryBlock.with(dev.denfry.promptcritical.mod.electronics.CleanroomAirlockBlockEntity::new));
    public static final DeferredBlock<PcBlock> CLEANROOM_PANEL = block("cleanroom_panel", machine(), Spec.of());
    public static final DeferredBlock<PcBlock> CLEANROOM_WINDOW = block("cleanroom_window", glassBlock(), Spec.of());
    public static final DeferredBlock<PcBlock> HEPA_FILTER = block("hepa_filter", machine(), Spec.of());
    // --- очистка дымовых газов и САКВ (14 §14.2) ---
    public static final DeferredBlock<PcDeviceBlock> ELECTROSTATIC_PRECIPITATOR = device("electrostatic_precipitator", machine(),
            Spec.facing(BlockStateProperties.POWERED),
            dev.denfry.promptcritical.mod.foundry.FoundryBlock.with(dev.denfry.promptcritical.mod.environment.ElectrostaticPrecipitatorBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> FGD_ABSORBER = device("fgd_absorber", machine(), Spec.of(),
            dev.denfry.promptcritical.mod.foundry.FoundryBlock.with(dev.denfry.promptcritical.mod.environment.FgdAbsorberBlockEntity::new));
    public static final DeferredBlock<PcBlock> CEMS_ANALYZER = block("cems_analyzer", instrument(), Spec.facing());
    public static final DeferredBlock<PcDeviceBlock> RTG_THERMOELECTRIC = device("rtg_thermoelectric", machine(),
            Spec.of(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT),
            dev.denfry.promptcritical.mod.foundry.FoundryBlock.with(dev.denfry.promptcritical.mod.isotopes.RtgBlockEntity::new));
    // --- исследовательский реактор TRIGA (12 §12.6.1): мост с приводом импульсного стержня над бассейном ---
    public static final DeferredBlock<PcDeviceBlock> TRANSIENT_ROD_DRIVE = device("transient_rod_drive", machine(),
            Spec.facing(BlockStateProperties.ENABLED, BlockStateProperties.TRIGGERED).footprint(FP_TALL2),
            dev.denfry.promptcritical.mod.foundry.FoundryBlock.with(dev.denfry.promptcritical.mod.isotopes.TrigaBlockEntity::new));
    // --- биржа (14 §14.6): терминал продажи товара NPC-рынку ---
    public static final DeferredBlock<PcDeviceBlock> MARKET_TERMINAL = device("market_terminal", instrument(), Spec.facing(),
            dev.denfry.promptcritical.mod.foundry.FoundryBlock.with(dev.denfry.promptcritical.mod.market.MarketTerminalBlockEntity::new));
    public static final DeferredBlock<PcBlock> CONTROL_ROD_DRIVE = block("control_rod_drive", machine(), Spec.facing());
    public static final DeferredBlock<PcBlock> ZIP_ROD_WINCH = block("zip_rod_winch", machine(), Spec.facing());
    public static final DeferredBlock<PcBlock> NEUTRON_DETECTOR = block("neutron_detector", instrument(), Spec.facing());
    public static final DeferredBlock<PcDeviceBlock> REACTOR_CONTROLLER = device("reactor_controller", machine(), Spec.facing(),
            dev.denfry.promptcritical.mod.foundry.FoundryBlock.with(dev.denfry.promptcritical.mod.reactor.PileControllerBlockEntity::new));
    public static final DeferredBlock<dev.denfry.promptcritical.mod.reactor.ScramButtonBlock> SCRAM_BUTTON = withItem(BLOCKS.registerBlock(
            "scram_button", p -> dev.denfry.promptcritical.mod.reactor.ScramButtonBlock.create(p, Spec.facing(BlockStateProperties.POWERED)),
            instrument()));
    public static final DeferredBlock<PcDeviceBlock> ACHESON_FURNACE = device("acheson_furnace", lit(brick()),
            Spec.facing(BlockStateProperties.LIT),
            dev.denfry.promptcritical.mod.foundry.FoundryBlock.with(dev.denfry.promptcritical.mod.reactor.AchesonFurnaceBlockEntity::new));

    // --- шахта (08 §8.2) ---
    public static final DeferredBlock<dev.denfry.promptcritical.mod.mine.MineSupportBlock> MINE_SUPPORT = withItem(BLOCKS.registerBlock("mine_support",
            p -> dev.denfry.promptcritical.mod.mine.MineSupportBlock.create(p,
                    Spec.facing(PcProperties.SUPPORT_KIND, PcProperties.DAMAGED).footprint(FP_TALL3)), wood()));
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
    /** Зарядный стеллаж ламповой на 12 головных светильников: кабель 0,4 кВ — к любой грани, кроме фасада. */
    public static final DeferredBlock<PcDeviceBlock> LAMP_RACK = device("lamp_rack", machine().noOcclusion(),
            Spec.facing(BlockStateProperties.LIT), FoundryBlock.with(dev.denfry.promptcritical.mod.mine.LampRackBlockEntity::new));
    /** Свет фары у головы игрока — без предмета, ставит и снимает только {@code CapLampService}. */
    public static final DeferredBlock<dev.denfry.promptcritical.mod.mine.CapLampLightBlock> CAP_LAMP_LIGHT = BLOCKS.registerBlock(
            "cap_lamp_light", dev.denfry.promptcritical.mod.mine.CapLampLightBlock::new, Properties.of().replaceable().noCollission()
                    .noLootTable().air().instabreak().noOcclusion().pushReaction(PushReaction.DESTROY)
                    .lightLevel(s -> s.getValue(dev.denfry.promptcritical.mod.mine.CapLampLightBlock.LEVEL)));
    public static final DeferredBlock<PcDeviceBlock> MINE_DRAINAGE_PUMP = device("mine_drainage_pump", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(FP_WIDE2),
            KineticMachineBlock.with(ShaftSide.LEFT, MineDrainagePumpBlockEntity::new));

    // --- нефть и газ I2: геология, сейсморазведка, буровая ---
    public static final DeferredBlock<PcBlock> RESERVOIR_SANDSTONE = geology("reservoir_sandstone", MapColor.TERRACOTTA_ORANGE);
    public static final DeferredBlock<PcBlock> RESERVOIR_SANDSTONE_GAS = geology("reservoir_sandstone_gas", MapColor.TERRACOTTA_YELLOW);
    public static final DeferredBlock<PcBlock> RESERVOIR_SANDSTONE_WATER = geology("reservoir_sandstone_water", MapColor.TERRACOTTA_LIGHT_GRAY);
    public static final DeferredBlock<PcBlock> CAP_ROCK_SHALE = geology("cap_rock_shale", MapColor.TERRACOTTA_GRAY);
    public static final DeferredBlock<PcBlock> BARITE_ORE = geology("barite_ore", MapColor.QUARTZ);
    public static final DeferredBlock<PcBlock> OIL_SEEP = geologyOf(block("oil_seep",
            Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.6f).sound(SoundType.MUD).noOcclusion().speedFactor(0.4f), Spec.of()));
    // находки мира 11 §11.4: асфальтовое озеро, горящий газовый кратер, природный реактор, станция пробирного анализа
    public static final DeferredBlock<dev.denfry.promptcritical.mod.worldgen.WorldBlocks.NaturalBitumenBlock> NATURAL_BITUMEN = withItem(
            BLOCKS.registerBlock("natural_bitumen", dev.denfry.promptcritical.mod.worldgen.WorldBlocks.NaturalBitumenBlock::new,
                    Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.8f).sound(SoundType.HONEY_BLOCK).speedFactor(0.3f)
                            .jumpFactor(0.5f)));
    public static final DeferredBlock<dev.denfry.promptcritical.mod.worldgen.WorldBlocks.GasSeepVentBlock> GAS_SEEP_VENT = withItem(
            BLOCKS.registerBlock("gas_seep_vent", dev.denfry.promptcritical.mod.worldgen.WorldBlocks.GasSeepVentBlock::new,
                    Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).strength(1.5f).requiresCorrectToolForDrops()
                            .lightLevel(s -> s.getValue(BlockStateProperties.LIT) ? 15 : 0)));
    /**
     * Уранинит природного реактора (11 §11.4.2, {@code natural_reactor_oklo}): с виду — обычная руда в глубинном сланце,
     * но выпадает сырьё с обеднённым U-235 (6000 ppm вместо 7200) — это видно только на станции пробирного анализа.
     */
    public static final DeferredBlock<PcBlock> OKLO_URANINITE_ORE = block("oklo_uraninite_ore", ore(), Spec.of());
    public static final DeferredBlock<PcBlock> ASSAY_STATION = block("assay_station", instrument(), Spec.facing());
    public static final DeferredBlock<PcDeviceBlock> SEISMIC_RECORDER = device("seismic_recorder", instrument(),
            Spec.facing(BlockStateProperties.LIT), FoundryBlock.with(dev.denfry.promptcritical.mod.drilling.SeismicRecorderBlockEntity::new));
    public static final DeferredBlock<PcBlock> GEOPHONE = block("geophone", instrument().noCollission(), Spec.of());
    public static final DeferredBlock<PcDeviceBlock> DRILLERS_CONSOLE = device("drillers_console", machine(),
            Spec.facing(BlockStateProperties.LIT, PcProperties.ALARM).footprint(FP_TALL2),
            FoundryBlock.with(dev.denfry.promptcritical.mod.drilling.DrillersConsoleBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> ROTARY_TABLE = device("rotary_table", machine(), Spec.facing(PcProperties.RUNNING),
            KineticMachineBlock.with(ShaftSide.LEFT, dev.denfry.promptcritical.mod.drilling.RotaryTableBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> MUD_PUMP = device("mud_pump", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(Footprint.of(Segment.box(-1, 1, 0, 1, 0, 1))),
            dev.denfry.promptcritical.mod.drilling.MudPumpBlock::create);
    public static final DeferredBlock<PcBlock> DRAWWORKS = block("drawworks", machine(),
            Spec.facing().footprint(Footprint.of(Segment.box(-1, 1, 0, 1, 0, 1))));
    public static final DeferredBlock<PcBlock> DERRICK_SEGMENT = block("derrick_segment", machine(), Spec.facing().footprint(FP_CUBE3));
    public static final DeferredBlock<PcBlock> BOP_ANNULAR = block("bop_annular", machine(), Spec.facing(PcProperties.SHUT));
    public static final DeferredBlock<PcBlock> CHOKE_MANIFOLD = block("choke_manifold", machine(), Spec.facing().footprint(FP_WIDE2));
    public static final DeferredBlock<PcBlock> MUD_TANK = block("mud_tank", machine(), Spec.facing(PcProperties.MUD_LEVEL));
    public static final DeferredBlock<PcDeviceBlock> MUD_MIXING_HOPPER = device("mud_mixing_hopper", machine(), Spec.facing(),
            FoundryBlock.with(dev.denfry.promptcritical.mod.drilling.MudHopperBlockEntity::new));

    // --- нефть и газ I2: добыча, подготовка, атмосферная перегонка ---
    public static final DeferredBlock<PcDeviceBlock> WELLHEAD = device("wellhead", machine(), Spec.facing(),
            FoundryBlock.with(dev.denfry.promptcritical.mod.production.WellheadBlockEntity::new));
    public static final DeferredBlock<PcBlock> CHRISTMAS_TREE = block("christmas_tree", machine(),
            Spec.facing(BlockStateProperties.OPEN).footprint(FP_TALL2));
    public static final DeferredBlock<PcDeviceBlock> PUMPJACK = device("pumpjack", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(Footprint.of(Segment.box(-1, 1, 0, 0, -1, 1))),
            dev.denfry.promptcritical.mod.production.PumpjackBlock::create);
    public static final DeferredBlock<PcDeviceBlock> THREE_PHASE_SEPARATOR = device("three_phase_separator", machine(),
            Spec.facing().footprint(Footprint.of(Segment.box(-1, 1, 0, 1, 0, 1))),
            FoundryBlock.with(dev.denfry.promptcritical.mod.production.SeparatorBlockEntity::new));
    public static final DeferredBlock<PcBlock> FLARE_STACK = block("flare_stack", machine(), Spec.of());
    public static final DeferredBlock<PcBlock> FLARE_TIP = block("flare_tip", lit(machine()), Spec.of(BlockStateProperties.LIT));
    public static final DeferredBlock<PcDeviceBlock> TUBE_FURNACE = device("tube_furnace", lit(brick()),
            Spec.facing(BlockStateProperties.LIT).footprint(FP_CUBE3),
            FoundryBlock.with(dev.denfry.promptcritical.mod.production.TubeFurnaceBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> CRUDE_COLUMN = device("crude_column", machine(), Spec.facing().footprint(FP_TALL2),
            FoundryBlock.with(dev.denfry.promptcritical.mod.production.CrudeColumnBlockEntity::new));
    public static final DeferredBlock<PcBlock> COLUMN_TRAY_SECTION = block("column_tray_section", machine(), Spec.facing().footprint(FP_TALL2));
    public static final DeferredBlock<PcBlock> COLUMN_TOP = block("column_top", machine(), Spec.facing());

    // --- газопереработка I2: компрессор, осушка, НТС, ГФУ ---
    public static final DeferredBlock<PcDeviceBlock> GAS_COMPRESSOR = device("gas_compressor", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(Footprint.of(Segment.box(0, 1, 0, 1, 0, 1))),
            KineticMachineBlock.with(ShaftSide.LEFT, dev.denfry.promptcritical.mod.gas.GasCompressorBlockEntity::new));
    public static final DeferredBlock<PcBlock> GLYCOL_REGENERATOR = block("glycol_regenerator", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(Footprint.of(Segment.box(-1, 1, 0, 1, 0, 0))));
    public static final DeferredBlock<PcDeviceBlock> LOW_TEMPERATURE_SEPARATOR = device("low_temperature_separator", machine(),
            Spec.facing(BlockStateProperties.LIT).footprint(FP_CUBE3),
            FoundryBlock.with(dev.denfry.promptcritical.mod.gas.GasPlantBlockEntity::new));
    /** Гелиевая установка: криоблок и адсорберы доочистки, 2×2×1. */
    public static final DeferredBlock<PcDeviceBlock> HELIUM_RECOVERY_UNIT = device("helium_recovery_unit", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(Footprint.of(Segment.box(0, 1, 0, 1, 0, 0))),
            FoundryBlock.with(dev.denfry.promptcritical.mod.gas.HeliumRecoveryBlockEntity::new));
    // --- радиохимия PUREX (I4): каньон, аппараты линии, хранилище стекла ---
    public static final DeferredBlock<PcDeviceBlock> PUREX_CELL = device("purex_cell", machine(), Spec.facing(PcProperties.RUNNING),
            FoundryBlock.with(dev.denfry.promptcritical.mod.reprocessing.PurexCellBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> HLW_VAULT = device("hlw_vault", machine(), Spec.of(BlockStateProperties.LIT),
            FoundryBlock.with(dev.denfry.promptcritical.mod.reprocessing.HlwVaultBlockEntity::new));
    public static final DeferredBlock<PcBlock> SHEARING_MACHINE = block("shearing_machine", machine(), Spec.facing());
    public static final DeferredBlock<PcBlock> DISSOLVER = block("dissolver", machine(), Spec.facing());
    public static final DeferredBlock<PcBlock> MIXER_SETTLER = block("mixer_settler", machine(), Spec.facing());
    public static final DeferredBlock<PcBlock> OXALATE_PRECIPITATOR = block("oxalate_precipitator", machine(), Spec.facing());
    public static final DeferredBlock<PcBlock> DENITRATION_REACTOR = block("denitration_reactor", machine(), Spec.facing());
    public static final DeferredBlock<PcBlock> VITRIFICATION_MELTER = block("vitrification_melter", machine(), Spec.facing());
    // --- обращение с РАО: выпарка ЖРО, отверждение, суперпресс, захоронение, очистка теплоносителя ---
    public static final DeferredBlock<PcDeviceBlock> LRW_EVAPORATOR = device("lrw_evaporator", machine(), Spec.facing(PcProperties.RUNNING),
            FoundryBlock.with(dev.denfry.promptcritical.mod.waste.LrwEvaporatorBlockEntity::new));
    public static final DeferredBlock<PcBlock> CEMENTATION_MIXER = block("cementation_mixer", machine(), Spec.facing());
    public static final DeferredBlock<PcBlock> BITUMENIZER = block("bitumenizer", machine(), Spec.facing());
    public static final DeferredBlock<PcDeviceBlock> WASTE_COMPACTOR = device("waste_compactor", machine(), Spec.facing(PcProperties.RUNNING),
            FoundryBlock.with(dev.denfry.promptcritical.mod.waste.WasteCompactorBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> NEAR_SURFACE_REPOSITORY = device("near_surface_repository", machine(), Spec.facing(),
            FoundryBlock.with(dev.denfry.promptcritical.mod.waste.RepositoryBlockEntity::nearSurface));
    public static final DeferredBlock<PcDeviceBlock> DEEP_GEOLOGICAL_REPOSITORY = device("deep_geological_repository", machine(), Spec.facing(),
            FoundryBlock.with(dev.denfry.promptcritical.mod.waste.RepositoryBlockEntity::deep));
    public static final DeferredBlock<PcDeviceBlock> REACTOR_WATER_CLEANUP = device("reactor_water_cleanup", machine(),
            Spec.facing(PcProperties.RUNNING), FoundryBlock.with(dev.denfry.promptcritical.mod.waste.ReactorWaterCleanupBlockEntity::new));
    /** Стеллаж бассейна выдержки облучённого топлива. */
    public static final DeferredBlock<PcDeviceBlock> FUEL_RACK = device("fuel_rack", machine().noOcclusion(), Spec.of(),
            FoundryBlock.with(dev.denfry.promptcritical.mod.radiation.FuelRackBlockEntity::new));
    // поверхностное загрязнение и дезактивация (санпропускник, мойка деталей)
    public static final DeferredBlock<PcDeviceBlock> ULTRASONIC_BATH = device("ultrasonic_bath", machine(), Spec.facing(),
            FoundryBlock.with(dev.denfry.promptcritical.mod.radiation.UltrasonicBathBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> DECON_SHOWER = device("decon_shower", machine(), Spec.facing(),
            FoundryBlock.with(dev.denfry.promptcritical.mod.radiation.DeconShowerBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> HAND_FOOT_MONITOR = device("hand_foot_monitor", machine(), Spec.facing(),
            FoundryBlock.with(dev.denfry.promptcritical.mod.radiation.HandFootMonitorBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> NGL_FRACTIONATOR = device("ngl_fractionator", machine(),
            Spec.facing().footprint(FP_CUBE3), FoundryBlock.with(dev.denfry.promptcritical.mod.gas.NglFractionatorBlockEntity::new));

    // --- телемеханика A4: шлюз (КП) для диспетчерского компьютера CC: Tweaked ---
    /** Историан 03d §3d.9.5: стойка 1×2×1, CC-периферия {@code pc_historian}. */
    public static final DeferredBlock<PcDeviceBlock> HISTORIAN_SERVER = device("historian_server", machine(),
            Spec.facing(BlockStateProperties.LIT).footprint(FP_TALL2),
            FoundryBlock.with(dev.denfry.promptcritical.mod.scada.HistorianBlockEntity::new));
    /** Инженерная станция 03d §3d.9.1: цифровой двойник для FAT программ ПЛК, стол 2×1×1. */
    public static final DeferredBlock<PcDeviceBlock> SIMULATION_WORKSTATION = device("simulation_workstation", machine(),
            Spec.facing().footprint(FP_WIDE2),
            FoundryBlock.with(dev.denfry.promptcritical.mod.scada.TwinWorkstationBlockEntity::new));
    /** Тренажёр 11 §11.3.2: вычислительный шкаф 1×2×1, пульт оператора и место инструктора 2×1×1. */
    public static final DeferredBlock<PcDeviceBlock> TRAINING_SIMULATOR = device("training_simulator", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(FP_TALL2),
            FoundryBlock.with(dev.denfry.promptcritical.mod.scada.TrainingSimulatorBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> SIMULATOR_CONSOLE = device("simulator_console", machine(),
            Spec.facing(BlockStateProperties.LIT),
            FoundryBlock.with(dev.denfry.promptcritical.mod.scada.SimulatorDeskBlockEntity::console));
    public static final DeferredBlock<PcDeviceBlock> INSTRUCTOR_STATION = device("instructor_station", machine(),
            Spec.facing(BlockStateProperties.LIT).footprint(FP_WIDE2),
            FoundryBlock.with(dev.denfry.promptcritical.mod.scada.SimulatorDeskBlockEntity::instructor));
    public static final DeferredBlock<PcDeviceBlock> RTU_STATION = device("rtu_station", machine(),
            Spec.facing(BlockStateProperties.LIT).footprint(FP_TALL2),
            FoundryBlock.with(dev.denfry.promptcritical.mod.scada.TelemetryGatewayBlockEntity::new));
    // --- автоматизация A5: сервер завода (MES) ---
    public static final DeferredBlock<PcDeviceBlock> MES_SERVER = device("mes_server", machine(),
            Spec.facing(BlockStateProperties.LIT).footprint(FP_TALL2),
            FoundryBlock.with(dev.denfry.promptcritical.mod.mes.MesServerBlockEntity::new));
    // --- химия I3: пиролиз, газоразделение, ПЭВД ---
    public static final DeferredBlock<PcDeviceBlock> PYROLYSIS_FURNACE = device("pyrolysis_furnace", lit(brick()),
            Spec.facing(BlockStateProperties.LIT).footprint(FP_CUBE3),
            FoundryBlock.with(dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> CRACKED_GAS_UNIT = device("cracked_gas_unit", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(FP_CUBE3), FoundryBlock.with(dev.denfry.promptcritical.mod.chem.CrackedGasUnitBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> LDPE_REACTOR = device("ldpe_reactor", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(Footprint.of(Segment.box(-1, 1, 0, 0, 0, 0))),
            KineticMachineBlock.with(ShaftSide.BACK, dev.denfry.promptcritical.mod.chem.LdpeReactorBlockEntity::new));

    // --- облагораживание на НПЗ: гидроочистка и сера (I2) ---
    public static final DeferredBlock<PcDeviceBlock> HYDROTREATER = uranium("hydrotreater",
            dev.denfry.promptcritical.mod.refinery.HydrotreaterBlockEntity::new);
    public static final DeferredBlock<PcDeviceBlock> CLAUS_UNIT = uranium("claus_unit",
            dev.denfry.promptcritical.mod.refinery.ClausUnitBlockEntity::new);
    public static final DeferredBlock<PcDeviceBlock> CATALYTIC_REFORMER = uranium("catalytic_reformer",
            dev.denfry.promptcritical.mod.refinery.CatalyticReformerBlockEntity::new);
    public static final DeferredBlock<PcDeviceBlock> DELAYED_COKER = uranium("delayed_coker",
            dev.denfry.promptcritical.mod.refinery.DelayedCokerBlockEntity::new);

    // --- передел урана: от руды до UF₆ (I4) ---
    public static final DeferredBlock<PcDeviceBlock> ACID_PLANT = uranium("acid_plant",
            dev.denfry.promptcritical.mod.uranium.AcidPlantBlockEntity::new);
    public static final DeferredBlock<PcDeviceBlock> HF_KILN = uranium("hf_kiln", dev.denfry.promptcritical.mod.uranium.HfKilnBlockEntity::new);
    public static final DeferredBlock<PcDeviceBlock> LEACH_TANK = uranium("leach_tank",
            dev.denfry.promptcritical.mod.uranium.LeachTankBlockEntity::new);
    public static final DeferredBlock<PcDeviceBlock> URANIUM_ION_EXCHANGE = uranium("uranium_ion_exchange",
            dev.denfry.promptcritical.mod.uranium.IonExchangeBlockEntity::new);
    public static final DeferredBlock<PcDeviceBlock> PRECIPITATION_TANK = uranium("precipitation_tank",
            dev.denfry.promptcritical.mod.uranium.PrecipitationBlockEntity::new);
    public static final DeferredBlock<PcDeviceBlock> ROTARY_CALCINER = uranium("rotary_calciner",
            dev.denfry.promptcritical.mod.uranium.ConversionBlockEntity::calciner);
    public static final DeferredBlock<PcDeviceBlock> REDUCTION_FURNACE = uranium("reduction_furnace",
            dev.denfry.promptcritical.mod.uranium.ConversionBlockEntity::reduction);
    public static final DeferredBlock<PcDeviceBlock> HYDROFLUORINATION_REACTOR = uranium("hydrofluorination_reactor",
            dev.denfry.promptcritical.mod.uranium.ConversionBlockEntity::hydrofluorination);
    public static final DeferredBlock<PcDeviceBlock> FLUORINATION_REACTOR = uranium("fluorination_reactor",
            dev.denfry.promptcritical.mod.uranium.FluorinationBlockEntity::new);
    public static final DeferredBlock<PcDeviceBlock> FLUORINE_CELL = uranium("fluorine_cell",
            dev.denfry.promptcritical.mod.uranium.FluorineCellBlockEntity::new);

    // --- хлор-щелочное производство и ПВХ (I3) ---
    public static final DeferredBlock<PcDeviceBlock> BRINE_TREATER = device("brine_treater", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(FP_FURNACE3),
            FoundryBlock.with(dev.denfry.promptcritical.mod.chlor.BrineTreaterBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> CHLOR_ALKALI_CELL = device("chlor_alkali_cell", machine(),
            Spec.facing(PcProperties.RUNNING), FoundryBlock.with(dev.denfry.promptcritical.mod.chlor.CellRoomBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> RECTIFIER_UNIT = device("rectifier_unit", machine(),
            Spec.facing(PcProperties.RUNNING, PcProperties.ALARM).footprint(Footprint.of(Segment.box(0, 1, 0, 1, 0, 0))),
            FoundryBlock.with(dev.denfry.promptcritical.mod.chlor.RectifierBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> CHLORINE_LIQUEFIER = device("chlorine_liquefier", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(FP_TALL2),
            FoundryBlock.with(dev.denfry.promptcritical.mod.chlor.LiquefierBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> EDC_UNIT = device("edc_unit", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(FP_TALL2),
            FoundryBlock.with(dev.denfry.promptcritical.mod.chlor.EdcUnitBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> EDC_CRACKER = device("edc_cracker", lit(brick()),
            Spec.facing(BlockStateProperties.LIT, PcProperties.ALARM),
            FoundryBlock.with(dev.denfry.promptcritical.mod.chlor.EdcCrackerBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> PVC_REACTOR = device("pvc_reactor", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(FP_TALL3),
            FoundryBlock.with(dev.denfry.promptcritical.mod.chlor.PvcReactorBlockEntity::new));
    // --- химия I3: азот — синтез-газ, аммиак, азотная кислота ---
    private static final Footprint FP_COLUMN3 = Footprint.of(Segment.box(0, 0, -1, 1, 0, 0).at(0, 1, 0)); // контроллер внизу, колонна вверх на 3
    public static final DeferredBlock<PcDeviceBlock> STEAM_REFORMER = device("steam_reformer", lit(brick()),
            Spec.facing(BlockStateProperties.LIT).footprint(FP_CUBE3),
            FoundryBlock.with(dev.denfry.promptcritical.mod.nitrogen.SteamReformerBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> CO2_ABSORBER = device("co2_absorber", machine(),
            Spec.facing(BlockStateProperties.LIT).footprint(FP_COLUMN3),
            FoundryBlock.with(dev.denfry.promptcritical.mod.nitrogen.Co2AbsorberBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> AMMONIA_CONVERTER = device("ammonia_converter", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(FP_COLUMN3),
            KineticMachineBlock.with(ShaftSide.BACK, dev.denfry.promptcritical.mod.nitrogen.AmmoniaConverterBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> NITRIC_ACID_UNIT = device("nitric_acid_unit", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(Footprint.of(Segment.box(-1, 1, 0, 0, 0, 0))),
            KineticMachineBlock.with(ShaftSide.BACK, dev.denfry.promptcritical.mod.nitrogen.NitricAcidBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> ACID_CONCENTRATOR = device("acid_concentrator", machine(),
            Spec.facing(BlockStateProperties.LIT).footprint(FP_COLUMN3),
            FoundryBlock.with(dev.denfry.promptcritical.mod.nitrogen.AcidConcentratorBlockEntity::new));
    // --- химия I3: метанол — свой риформинг, контур синтеза на валу Create, ректификация ---
    public static final DeferredBlock<PcDeviceBlock> METHANOL_CONVERTER = device("methanol_converter", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(FP_TALL2),
            KineticMachineBlock.with(ShaftSide.BACK, dev.denfry.promptcritical.mod.methanol.MethanolConverterBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> METHANOL_COLUMN = device("methanol_column", machine(),
            Spec.facing(BlockStateProperties.LIT).footprint(FP_COLUMN3),
            FoundryBlock.with(dev.denfry.promptcritical.mod.methanol.MethanolColumnBlockEntity::new));

    // --- ядерное топливо I4: обогащение урана на газовых центрифугах ---
    /** Секция газовых центрифуг (20 машин); их считает пульт каскада в радиусе {@code CascadeBlockEntity#RADIUS}. */
    public static final DeferredBlock<PcBlock> GAS_CENTRIFUGE = block("gas_centrifuge", machine(), Spec.facing());
    public static final DeferredBlock<PcDeviceBlock> CENTRIFUGE_CASCADE = device("centrifuge_cascade", machine(),
            Spec.facing(PcProperties.RUNNING),
            KineticMachineBlock.with(ShaftSide.BACK, dev.denfry.promptcritical.mod.nuclear.CascadeBlockEntity::new));
    /** Фабрикация топлива: конверсия UF₆ в UO₂ (АДУ), пресс и печь спекания таблеток, снаряжение твэлов, сборка ТВС. */
    public static final DeferredBlock<PcDeviceBlock> UO2_CONVERSION = device("uo2_conversion", machine(), Spec.facing(BlockStateProperties.LIT),
            FoundryBlock.with(dev.denfry.promptcritical.mod.nuclear.Uo2ConversionBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> PELLET_PRESS = device("pellet_press", machine(), Spec.facing(PcProperties.RUNNING),
            KineticMachineBlock.with(ShaftSide.BACK, dev.denfry.promptcritical.mod.nuclear.PelletPressBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> SINTERING_FURNACE = device("sintering_furnace", machine(),
            Spec.facing(BlockStateProperties.LIT), FoundryBlock.with(dev.denfry.promptcritical.mod.nuclear.SinteringFurnaceBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> ROD_LOADING_STATION = device("rod_loading_station", machine(),
            Spec.facing(BlockStateProperties.LIT), FoundryBlock.with(dev.denfry.promptcritical.mod.nuclear.RodStationBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> ASSEMBLY_JIG = device("assembly_jig", machine(), Spec.facing(BlockStateProperties.LIT),
            FoundryBlock.with(dev.denfry.promptcritical.mod.nuclear.AssemblyJigBlockEntity::new));

    // --- АЭС T1: канальный уран-графитовый реактор (АМ-1), ГЦН, парогенератор ---
    /** Кладка 3×6×3: нижний куб — активная зона с отражателем, верхний — приводы СУЗ; контроллер — в центре низа. */
    private static final Footprint FP_POWER_REACTOR = Footprint.of(CUBE3, CUBE3.at(0, 3, 0));
    public static final DeferredBlock<PcDeviceBlock> POWER_REACTOR = device("power_reactor", machine(), Spec.facing().segments(PcProperties.REACTOR_SEGMENT, FP_POWER_REACTOR),
            FoundryBlock.with(dev.denfry.promptcritical.mod.npp.PowerReactorBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> PRIMARY_PUMP = device("primary_pump", machine(), Spec.facing(PcProperties.RUNNING),
            KineticMachineBlock.with(ShaftSide.BACK, dev.denfry.promptcritical.mod.npp.PrimaryPumpBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> STEAM_GENERATOR = device("steam_generator", machine(),
            Spec.facing(BlockStateProperties.LIT), FoundryBlock.with(dev.denfry.promptcritical.mod.npp.SteamGeneratorBlockEntity::new));

    // --- АЭС T2a: ВВЭР — корпус с зоной на 19 ТВС и верхний блок СУЗ, компенсатор давления ---
    /** Установка 3×6×3: нижний куб — корпус с зоной, верхний — блок приводов СУЗ; контроллер (пульт) — в центре низа. */
    private static final Footprint FP_PWR_REACTOR = Footprint.of(CUBE3, CUBE3.at(0, 3, 0));
    public static final DeferredBlock<PcDeviceBlock> PWR_REACTOR = device("pwr_reactor", machine(), Spec.facing().segments(PcProperties.PWR_SEGMENT, FP_PWR_REACTOR),
            FoundryBlock.with(dev.denfry.promptcritical.mod.npp.PwrReactorBlockEntity::new));
    // --- гермооболочка ВВЭР/PWR (04c, M10): стена, люк и пассивный рекомбинатор водорода ---
    public static final DeferredBlock<PcBlock> CONTAINMENT_WALL = block("containment_wall", containment(), Spec.of());
    public static final DeferredBlock<dev.denfry.promptcritical.mod.npp.ContainmentHatchBlock> CONTAINMENT_HATCH = withItem(
            BLOCKS.registerBlock("containment_hatch", dev.denfry.promptcritical.mod.npp.ContainmentHatchBlock::create, containment()));
    public static final DeferredBlock<PcBlock> HYDROGEN_RECOMBINER = block("hydrogen_recombiner", machine(), Spec.of());
    // --- САОЗ (04c): канал с насосом на валу Create и гидроёмкость под азотом ---
    public static final DeferredBlock<PcDeviceBlock> ECCS_PUMP = device("eccs_pump", machine(), Spec.facing(PcProperties.RUNNING),
            KineticMachineBlock.with(ShaftSide.BACK, dev.denfry.promptcritical.mod.npp.EccsPumpBlockEntity::new));
    public static final DeferredBlock<PcBlock> ECCS_ACCUMULATOR = block("eccs_accumulator", machine(), Spec.of());
    /**
     * Кипящий реактор T2b 3×6×3: внизу корпус с зоной, вверху сепараторы и осушители; пульт — в центре низа. {@code lit} —
     * реактор на мощности, {@code open} — открыты предохранительные клапаны (для огней пульта, звука и сброса пара).
     */
    private static final Footprint FP_BWR_REACTOR = Footprint.of(CUBE3, CUBE3.at(0, 3, 0));
    public static final DeferredBlock<PcDeviceBlock> BWR_REACTOR = device("bwr_reactor", machine(),
            Spec.facing(BlockStateProperties.LIT, BlockStateProperties.OPEN).segments(PcProperties.BWR_SEGMENT, FP_BWR_REACTOR),
            FoundryBlock.with(dev.denfry.promptcritical.mod.npp.BwrReactorBlockEntity::new));
    /** CANDU T3 3×6×3: внизу каландр с каналами, вверху мост перегрузочной машины; пульт — в центре низа. */
    private static final Footprint FP_CANDU_REACTOR = Footprint.of(CUBE3, CUBE3.at(0, 3, 0));
    public static final DeferredBlock<PcDeviceBlock> CANDU_REACTOR = device("candu_reactor", machine(), Spec.facing().segments(PcProperties.CANDU_SEGMENT, FP_CANDU_REACTOR),
            FoundryBlock.with(dev.denfry.promptcritical.mod.npp.CanduReactorBlockEntity::new));
    /**
     * Сероводородный завод тяжёлой воды 3×6×3: холодная колонна внизу, горячая над ней; питание — вода озера вокруг
     * основания, продукт (15 % D) — в трубы. Пульт — в центре низа.
     */
    private static final Footprint FP_GS_TOWER = Footprint.of(CUBE3, CUBE3.at(0, 3, 0));
    public static final DeferredBlock<PcDeviceBlock> GS_EXCHANGE_TOWER = device("gs_exchange_tower", machine(),
            Spec.facing(PcProperties.RUNNING).segments(PcProperties.GS_SEGMENT, FP_GS_TOWER),
            FoundryBlock.with(dev.denfry.promptcritical.mod.heavywater.GsExchangeTowerBlockEntity::new));
    /** Вакуумная колонна доводки D₂O 1×3×1: обогащённая и разбавленная вода — в куб, реакторная D₂O — из куба. */
    public static final DeferredBlock<PcDeviceBlock> HEAVY_WATER_COLUMN = device("heavy_water_column", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(FP_TALL3),
            FoundryBlock.with(dev.denfry.promptcritical.mod.heavywater.HeavyWaterColumnBlockEntity::new));
    /** Натриевый быстрый реактор T4 3×6×3: внизу бассейн натрия с зоной и экраном, вверху поворотная пробка; пульт — в центре низа. */
    private static final Footprint FP_SFR_REACTOR = Footprint.of(CUBE3, CUBE3.at(0, 3, 0));
    public static final DeferredBlock<PcDeviceBlock> SFR_REACTOR = device("sfr_reactor", machine(), Spec.facing().segments(PcProperties.SFR_SEGMENT, FP_SFR_REACTOR),
            FoundryBlock.with(dev.denfry.promptcritical.mod.npp.SfrReactorBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> PRESSURIZER = device("pressurizer", machine(), Spec.facing(BlockStateProperties.LIT),
            FoundryBlock.with(dev.denfry.promptcritical.mod.npp.PressurizerBlockEntity::new));

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
    // станочный парк (плейсхолдеры арта): фрезерный 1×2×1, строгальный и плоскошлифовальный 2×1×1; вал слева
    public static final DeferredBlock<PcDeviceBlock> MILLING_MACHINE = device("milling_machine", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(FP_TALL2),
            KineticMachineBlock.with(ShaftSide.LEFT, ProductionToolBlockEntity::milling));
    public static final DeferredBlock<PcDeviceBlock> SHAPING_MACHINE = device("shaping_machine", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(FP_WIDE2), KineticMachineBlock.with(ShaftSide.LEFT, ProductionToolBlockEntity::shaper));
    public static final DeferredBlock<PcDeviceBlock> SURFACE_GRINDER = device("surface_grinder", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(FP_WIDE2),
            KineticMachineBlock.with(ShaftSide.LEFT, ProductionToolBlockEntity::grinder));

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
    /** Пожарная сигнализация и тушение 13 §13.5: извещатели, ручной извещатель, ППК, спринклер, дренчер, газовое тушение, ствол. */
    public static final DeferredBlock<PcDeviceBlock> SMOKE_DETECTOR = device("smoke_detector", instrument(), Spec.facing(PcProperties.ALARM), FoundryBlock.with(dev.denfry.promptcritical.mod.ops.FireDeviceBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> HEAT_DETECTOR = device("heat_detector", instrument(), Spec.facing(PcProperties.ALARM), FoundryBlock.with(dev.denfry.promptcritical.mod.ops.FireDeviceBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> FLAME_DETECTOR = device("flame_detector", instrument(), Spec.facing(PcProperties.ALARM), FoundryBlock.with(dev.denfry.promptcritical.mod.ops.FireDeviceBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> MANUAL_CALL_POINT = device("manual_call_point", instrument(), Spec.facing(PcProperties.ALARM), FoundryBlock.with(dev.denfry.promptcritical.mod.ops.FireDeviceBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> FIRE_ALARM_PANEL = device("fire_alarm_panel", instrument(), Spec.facing(PcProperties.ALARM, BlockStateProperties.POWERED), FoundryBlock.with(dev.denfry.promptcritical.mod.ops.FireDeviceBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> SPRINKLER_HEAD = device("sprinkler_head", instrument(), Spec.facing(PcProperties.ALARM), FoundryBlock.with(dev.denfry.promptcritical.mod.ops.FireDeviceBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> DELUGE_NOZZLE = device("deluge_nozzle", instrument(), Spec.facing(PcProperties.RUNNING), FoundryBlock.with(dev.denfry.promptcritical.mod.ops.FireDeviceBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> GAS_SUPPRESSION_CYLINDER = device("gas_suppression_cylinder", machine(), Spec.facing(PcProperties.ALARM), FoundryBlock.with(dev.denfry.promptcritical.mod.ops.FireDeviceBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> FIRE_MONITOR = device("fire_monitor", machine(), Spec.facing(PcProperties.RUNNING), FoundryBlock.with(dev.denfry.promptcritical.mod.ops.FireDeviceBlockEntity::new));
    /** Противопожарный водопровод 13 §13.5.3: пожарный насос, гидрант, дозатор пенообразователя, пенокамера. */
    public static final DeferredBlock<PcDeviceBlock> FIRE_WATER_PUMP = device("fire_water_pump", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(FP_WIDE2), FoundryBlock.with(dev.denfry.promptcritical.mod.ops.FireWaterBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> FIRE_HYDRANT = device("fire_hydrant", machine(), Spec.facing(),
            FoundryBlock.with(dev.denfry.promptcritical.mod.ops.FireWaterBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> FOAM_PROPORTIONER = device("foam_proportioner", machine(),
            Spec.facing(BlockStateProperties.POWERED).footprint(FP_TALL2), FoundryBlock.with(dev.denfry.promptcritical.mod.ops.FireWaterBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> FOAM_CHAMBER = device("foam_chamber", machine(), Spec.facing(),
            FoundryBlock.with(dev.denfry.promptcritical.mod.ops.FireWaterBlockEntity::new));
    /** LOTO 13 §13.1.2: поворотная заглушка на фланце, групповой ящик блокировки, расцепная муфта на валу Create. */
    public static final DeferredBlock<PcDeviceBlock> LINE_BLIND = device("line_blind", machine(),
            Spec.facing(BlockStateProperties.OPEN), FoundryBlock.with(dev.denfry.promptcritical.mod.ops.LineBlindBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> LOCKOUT_BOX = device("lockout_box", instrument(),
            Spec.facing(PcProperties.LOCKS), FoundryBlock.with(dev.denfry.promptcritical.mod.ops.LockoutBoxBlockEntity::new));
    /** Наряды-допуски 13 §13.2.2: стенд нарядов в операторной, 2×1×1, ведёт остановочный ремонт установки (§13.6.2). */
    public static final DeferredBlock<PcDeviceBlock> PERMIT_BOARD = device("permit_board", instrument(),
            Spec.facing().footprint(FP_WIDE2), FoundryBlock.with(dev.denfry.promptcritical.mod.ops.PermitBoardBlockEntity::new));
    public static final DeferredBlock<dev.denfry.promptcritical.mod.ops.MechanicalDisconnectBlock> MECHANICAL_DISCONNECT =
            withItem(BLOCKS.registerBlock("mechanical_disconnect", dev.denfry.promptcritical.mod.ops.MechanicalDisconnectBlock::new, machine()));
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
    // --- котёл I1 и деаэратор (07 §7.9.5–7.9.6, 08 §8.12, этап 11б M3) ---
    public static final DeferredBlock<PcDeviceBlock> WATER_TUBE_BOILER = device("water_tube_boiler", machine(),
            Spec.facing(BlockStateProperties.LIT, PcProperties.ALARM),
            FoundryBlock.with(dev.denfry.promptcritical.mod.powerhouse.WaterTubeBoilerBlockEntity::new));
    public static final DeferredBlock<PcBlock> BOILER_WALL = block("boiler_wall", shell(), Spec.of());
    public static final DeferredBlock<PcBlock> BOILER_BURNER = block("boiler_burner", lit(shell()),
            Spec.facing(BlockStateProperties.LIT, PcProperties.GAS_FIRED));
    public static final DeferredBlock<PcBlock> STEAM_DRUM = block("steam_drum", shell().noOcclusion(), Spec.facing());
    public static final DeferredBlock<PcBlock> BOILER_SUPERHEATER = block("boiler_superheater", shell().noOcclusion(), Spec.of());
    public static final DeferredBlock<PcBlock> BOILER_ECONOMIZER = block("boiler_economizer", shell().noOcclusion(), Spec.of());
    public static final DeferredBlock<PcBlock> AIR_PREHEATER = block("air_preheater", shell().noOcclusion(), Spec.of());
    public static final DeferredBlock<PcDeviceBlock> FORCED_DRAFT_FAN = device("forced_draft_fan", machine(),
            Spec.facing(PcProperties.RUNNING),
            KineticMachineBlock.with(ShaftSide.FRONT, dev.denfry.promptcritical.mod.powerhouse.DraftFanBlockEntity::forced));
    public static final DeferredBlock<PcDeviceBlock> INDUCED_DRAFT_FAN = device("induced_draft_fan", machine(),
            Spec.facing(PcProperties.RUNNING),
            KineticMachineBlock.with(ShaftSide.FRONT, dev.denfry.promptcritical.mod.powerhouse.DraftFanBlockEntity::induced));
    /** Гидроагрегат 3×2×3: вертикальный вал Create вверх, вода — от затвора водозабора выше по склону. */
    public static final DeferredBlock<PcDeviceBlock> HYDRO_TURBINE = device("hydro_turbine", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(Footprint.of(Segment.box(-1, 1, -1, 0, -1, 1))),
            KineticMachineBlock.with(ShaftSide.UP, dev.denfry.promptcritical.mod.powerhouse.HydroTurbineBlockEntity::new));
    /**
     * Капсульный агрегат приливной электростанции 3×2×3 в плотине: фасадом к морю, тылом к бассейну, вал Create вверх.
     */
    public static final DeferredBlock<PcDeviceBlock> TIDAL_UNIT = device("tidal_unit", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(Footprint.of(Segment.box(-1, 1, -1, 0, -1, 1))),
            KineticMachineBlock.with(ShaftSide.UP, dev.denfry.promptcritical.mod.powerhouse.TidalUnitBlockEntity::new));
    /** Обратимый агрегат ГАЭС 3×2×3: верхний бассейн — затвор водозабора выше по склону, кабель 6,3 кВ — сверху и с боков. */
    public static final DeferredBlock<PcDeviceBlock> PUMPED_STORAGE_UNIT = device("pumped_storage_unit", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(Footprint.of(Segment.box(-1, 1, -1, 0, -1, 1))),
            FoundryBlock.with(dev.denfry.promptcritical.mod.powerhouse.PumpedStorageBlockEntity::new));
    /** Электродный котёл 6,3 кВ: вплотную к котлу котельной, пар — в его коллектор. */
    public static final DeferredBlock<PcDeviceBlock> ELECTRODE_BOILER = device("electrode_boiler", machine(),
            Spec.facing(PcProperties.RUNNING), FoundryBlock.with(dev.denfry.promptcritical.mod.powerhouse.ElectrodeBoilerBlockEntity::new));
    /** Мареограф: уровень моря по модели прилива, амплитуда M2 места и фаза Луны. */
    public static final DeferredBlock<PcDeviceBlock> TIDE_GAUGE = device("tide_gauge", machine().noOcclusion(), Spec.facing(),
            FoundryBlock.with(dev.denfry.promptcritical.mod.powerhouse.TideGaugeBlockEntity::new));
    /** Затвор водозабора ГЭС: ставится в воду, поднимается и опускается рукой. */
    public static final DeferredBlock<dev.denfry.promptcritical.mod.powerhouse.DamGateBlock> DAM_GATE = withItem(BLOCKS.registerBlock(
            "dam_gate", p -> dev.denfry.promptcritical.mod.powerhouse.DamGateBlock.create(p, Spec.facing(BlockStateProperties.OPEN)),
            machine().noOcclusion()));
    /** ГТУ 3×3×9: компрессор, камера сгорания, турбина; газ — по трубе, вал — через редуктор у компрессора. */
    public static final DeferredBlock<PcDeviceBlock> GAS_TURBINE = device("gas_turbine", machine(),
            Spec.facing(PcProperties.RUNNING).segments(PcProperties.GAS_TURBINE_SEGMENT,
                    Footprint.of(CUBE3, CUBE3.at(0, 0, 3), CUBE3.at(0, 0, 6))),
            FoundryBlock.with(dev.denfry.promptcritical.mod.powerhouse.GasTurbineBlockEntity::new));
    /**
     * Аварийный дизель-генератор 3×3×6 (07 §7.11): дизель спереди, генератор сзади; дизтопливо — трубой к любой части,
     * кабель — к верху, бокам или торцу генератора, аккумулятор оперативного тока — вплотную к агрегату.
     */
    public static final DeferredBlock<PcDeviceBlock> DIESEL_GENERATOR = device("diesel_generator", machine(),
            Spec.facing(PcProperties.RUNNING).segments(PcProperties.DIESEL_SEGMENT, Footprint.of(CUBE3, CUBE3.at(0, 0, 3))),
            FoundryBlock.with(dev.denfry.promptcritical.mod.powerhouse.DieselGeneratorBlockEntity::new));
    /** Котёл-утилизатор ПГУ 3×6×3: газоход у выхлопа ГТУ, барабан сверху; пар — в котёл, которого он касается. */
    public static final DeferredBlock<PcDeviceBlock> HRSG = device("hrsg", machine(),
            Spec.facing(PcProperties.RUNNING).segments(PcProperties.HRSG_SEGMENT, Footprint.of(CUBE3, CUBE3.at(0, 3, 0))),
            FoundryBlock.with(dev.denfry.promptcritical.mod.powerhouse.HrsgBlockEntity::new));
    /**
     * Сетевая ВЭУ Д-30: решётчатая башня 1×9×1 (основание со шкафом управления, средняя секция, оголовок), гондола
     * и колесо над ней поворачиваются на ветер; кабель 0,4 кВ — к основанию.
     */
    public static final DeferredBlock<PcDeviceBlock> WIND_TURBINE = device("wind_turbine", machine().noOcclusion(),
            Spec.facing(PcProperties.RUNNING).segments(PcProperties.WIND_TURBINE_SEGMENT, FP_WIND_TOWER),
            FoundryBlock.with(dev.denfry.promptcritical.mod.powerhouse.WindTurbineBlockEntity::new));
    /** Редуктор турбины: задней гранью к агрегату, вал Create спереди. */
    public static final DeferredBlock<PcDeviceBlock> TURBINE_GEARBOX = device("turbine_gearbox", machine(),
            Spec.facing(PcProperties.RUNNING),
            KineticMachineBlock.with(ShaftSide.FRONT, dev.denfry.promptcritical.mod.powerhouse.TurbineGearboxBlockEntity::new));
    /** Секция вентиляторной градирни 3×3×3: холодная вода для конденсатора, который её касается. */
    public static final DeferredBlock<dev.denfry.promptcritical.mod.powerhouse.CoolingTowerBlock> COOLING_TOWER_CELL = withItem(
            BLOCKS.registerBlock("cooling_tower_cell", p -> dev.denfry.promptcritical.mod.powerhouse.CoolingTowerBlock.create(p,
                    Spec.facing(PcProperties.RUNNING, PcProperties.ICED).footprint(FP_CUBE3)), machine().noOcclusion()));
    /** Конденсатор под паровой турбиной: охлаждается водой, которой касается (река, пруд). */
    public static final DeferredBlock<PcBlock> CONDENSER = block("condenser", machine().noOcclusion(), Spec.facing());
    /** Паровая турбина с противодавлением: вал Create спереди, пар — от котла, которого касается. */
    public static final DeferredBlock<PcDeviceBlock> STEAM_TURBINE = device("steam_turbine", machine(),
            Spec.facing(PcProperties.RUNNING),
            KineticMachineBlock.with(ShaftSide.FRONT, dev.denfry.promptcritical.mod.powerhouse.SteamTurbineBlockEntity::new));
    public static final DeferredBlock<PcDeviceBlock> DEAERATOR = device("deaerator", machine(),
            Spec.facing(PcProperties.RUNNING).footprint(Footprint.of(Segment.box(-1, 1, 0, 1, 0, 0))),
            FoundryBlock.with(dev.denfry.promptcritical.mod.powerhouse.DeaeratorBlockEntity::new));

    // --- справочник и мир (11) ---
    public static final DeferredBlock<DocumentLecternBlock> DOCUMENT_LECTERN = withItem(BLOCKS.registerBlock("document_lectern",
            p -> DocumentLecternBlock.create(p, Spec.facing(PcProperties.HAS_DOCUMENT)), wood()));
    public static final DeferredBlock<CanaryCageBlock> CANARY_CAGE = withItem(BLOCKS.registerBlock("canary_cage",
            p -> CanaryCageBlock.create(p, Spec.of(PcProperties.BIRD)),
            Properties.of().mapColor(MapColor.METAL).strength(0.5f).sound(SoundType.LANTERN).noOcclusion()));

    // --- декор I0 (11 §11.6.2): кафель, керамогранит, крашеный бетон, стеклоблоки, профнастил, площадки, свет ---
    /** Свинцовые блоки биологической защиты (11,35 г/см³): слой половинного ослабления γ Co-60 — около 1 см. */
    public static final DeferredBlock<PcBlock> LEAD_BLOCK = decorCube("lead_block", MapColor.COLOR_GRAY, false);
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
    // светильники горят от щитка освещения (lighting_board) в радиусе группы
    public static final DeferredBlock<LampBlock> INDUSTRIAL_LIGHT = lampBlock("industrial_light", LampKind.PENDANT,
            Spec.of(BlockStateProperties.LIT), 3, 5, 3, 13, 16, 13);
    public static final DeferredBlock<LampBlock> INDUSTRIAL_LIGHT_STREET = lampBlock("industrial_light_street", LampKind.STREET,
            Spec.facing(BlockStateProperties.LIT), 5, 8.5, 1, 11, 12, 16);
    public static final DeferredBlock<LampBlock> INDUSTRIAL_LIGHT_FLOODLIGHT = lampBlock("industrial_light_floodlight",
            LampKind.FLOODLIGHT, Spec.facing(BlockStateProperties.LIT), 3, 0, 3.5, 13, 12, 10);
    public static final DeferredBlock<LampBlock> INDUSTRIAL_LIGHT_EXIT = lampBlock("industrial_light_exit", LampKind.EXIT,
            Spec.facing(BlockStateProperties.LIT), 3, 5, 13.9, 13, 11, 16);
    public static final DeferredBlock<LampBlock> LAMP_FLUORESCENT = lampBlock("lamp_fluorescent", LampKind.FLUORESCENT,
            Spec.facing(BlockStateProperties.LIT), 0, 13.5, 5, 16, 16, 11);
    public static final DeferredBlock<LampBlock> LAMP_BULKHEAD = lampBlock("lamp_bulkhead", LampKind.BULKHEAD,
            Spec.facing(BlockStateProperties.LIT), 4, 4, 12.5, 12, 12, 16);
    public static final DeferredBlock<LampBlock> LAMP_EXPLOSION_PROOF = lampBlock("lamp_explosion_proof", LampKind.EXPLOSION_PROOF,
            Spec.facing(BlockStateProperties.LIT), 5, 2, 6, 11, 14, 16);
    public static final DeferredBlock<LampBlock> LAMP_EMERGENCY = lampBlock("lamp_emergency", LampKind.EMERGENCY,
            Spec.facing(BlockStateProperties.LIT), 3, 6, 14, 13, 10, 16);
    public static final DeferredBlock<LampBlock> LAMP_HIGH_BAY = lampBlock("lamp_high_bay", LampKind.HIGH_BAY,
            Spec.of(BlockStateProperties.LIT), 2, 0, 2, 14, 16, 14);
    public static final DeferredBlock<LampBlock> WARNING_BEACON = lampBlock("warning_beacon", LampKind.BEACON,
            Spec.of(BlockStateProperties.LIT), 5, 0, 5, 11, 7, 11);
    public static final DeferredBlock<DecorBlock> ROOM_SIGN = decor("room_sign", plate(), Spec.facing(), 2, 5, 15.5, 14, 11, 16);
    public static final DeferredBlock<DecorBlock> ROOM_SIGN_EVAC = decor("room_sign_evac", plate(), Spec.facing(), 2, 5, 15.5, 14, 11, 16);
    public static final DeferredBlock<WallClockBlock> WALL_CLOCK = decorOf(BLOCKS.registerBlock("wall_clock",
            p -> WallClockBlock.create(p, Spec.facing(), new double[] {2, 2, 14.4, 14, 14, 16}), plate()));
    public static final DeferredBlock<DecorBlock> SAFETY_POSTER = decor("safety_poster", plate(), Spec.facing(), 0, 0, 15.75, 16, 16, 16);
    public static final DeferredBlock<DecorBlock> SAFETY_POSTER_LARGE = decor("safety_poster_large", plate(), Spec.facing(),
            0, 0, 15.75, 16, 16, 16);
    public static final DeferredBlock<DecorBlock> CURB = decor("curb", Properties.of().mapColor(MapColor.STONE)
            .requiresCorrectToolForDrops().strength(1.5f, 6.0f), Spec.facing(), 0, 0, 5, 16, 6, 11);

    // --- декор I1 (11 §11.6.2): линолеум щита управления, подвесной потолок, кабельные лотки, таблички ---
    public static final DeferredBlock<PcBlock> LINOLEUM_CONTROL = decorCube("linoleum_control", MapColor.COLOR_GRAY, true);
    public static final DeferredBlock<PcBlock> LINOLEUM_CONTROL_BEIGE = decorCube("linoleum_control_beige", MapColor.SAND, true);
    public static final DeferredBlock<PcBlock> LINOLEUM_CONTROL_PARQUET = decorCube("linoleum_control_parquet", MapColor.WOOD, true);
    public static final DeferredBlock<PcBlock> CEILING_TILE = decorCube("ceiling_tile", MapColor.SNOW, true);
    /** Светильник подвесного потолка: висит под перекрытием, питается от щитка освещения. */
    public static final DeferredBlock<LampBlock> CEILING_LIGHT_PANEL = lampBlock("ceiling_light_panel", LampKind.CEILING_PANEL,
            Spec.of(BlockStateProperties.LIT), 0, 14.5, 0, 16, 16, 16);
    public static final DeferredBlock<DecorBlock> CABLE_TRAY = decor("cable_tray", sheet(MapColor.METAL), Spec.facing(), 3, 0, 0, 13, 3, 16);
    public static final DeferredBlock<DecorBlock> CABLE_TRAY_CABLES = decor("cable_tray_cables", sheet(MapColor.METAL), Spec.facing(),
            3, 0, 0, 13, 3, 16);
    public static final DeferredBlock<DecorBlock> CABLE_TRAY_CORNER = decor("cable_tray_corner", sheet(MapColor.METAL), Spec.facing(),
            3, 0, 0, 16, 3, 13);
    public static final DeferredBlock<DecorBlock> CABLE_TRAY_TEE = decor("cable_tray_tee", sheet(MapColor.METAL), Spec.facing(),
            3, 0, 0, 16, 3, 16);
    public static final DeferredBlock<DecorBlock> CABLE_TRAY_VERTICAL = decor("cable_tray_vertical", sheet(MapColor.METAL), Spec.facing(),
            3, 0, 12.5, 13, 16, 16);
    public static final DeferredBlock<DecorBlock> ROOM_SIGN_ELECTRICAL = decor("room_sign_electrical", plate(), Spec.facing(),
            2, 5, 15.5, 14, 11, 16);
    public static final DeferredBlock<DecorBlock> ROOM_SIGN_PUMP = decor("room_sign_pump", plate(), Spec.facing(), 2, 5, 15.5, 14, 11, 16);
    public static final DeferredBlock<DecorBlock> ROOM_SIGN_CONTROL = decor("room_sign_control", plate(), Spec.facing(), 2, 5, 15.5, 14, 11, 16);

    // --- декор завода: электрооборудование и интерьер цеха ---
    public static final DeferredBlock<DecorBlock> ELECTRICAL_CABINET = decor("electrical_cabinet", sheet(MapColor.METAL), Spec.facing(), 0, 0, 0.5, 16, 16, 16);
    public static final DeferredBlock<DecorBlock> JUNCTION_BOX = decor("junction_box", sheet(MapColor.METAL), Spec.facing(), 3, 3, 14, 13, 13, 16);
    public static final DeferredBlock<DecorBlock> WALL_SOCKET = decor("wall_socket", plate(), Spec.facing(), 3, 3, 12.4, 13, 13, 16);
    public static final DeferredBlock<DecorBlock> KNIFE_SWITCH = decor("knife_switch", plate(), Spec.facing(), 3, 2, 10, 13, 14, 16);
    public static final DeferredBlock<DecorBlock> VOLTAGE_WARNING_SIGN = decor("voltage_warning_sign", plate(), Spec.facing(), 3, 4, 15.6, 13, 12, 16);
    public static final DeferredBlock<DecorBlock> CABLE_DRUM = decor("cable_drum", sheet(MapColor.WOOD), Spec.facing(), 0, 0.5, 0.5, 16, 15.5, 15.5);
    public static final DeferredBlock<DecorBlock> DIELECTRIC_MAT = decor("dielectric_mat", sheet(MapColor.COLOR_BLACK), Spec.of(), 0, 0, 0, 16, 0.5, 16);
    public static final DeferredBlock<DecorBlock> CONDUIT = decor("conduit", plate(), Spec.facing(), 0, 6, 14, 16, 10, 16);
    public static final DeferredBlock<DecorBlock> CONDUIT_VERTICAL = decor("conduit_vertical", plate(), Spec.facing(), 6, 0, 14, 10, 16, 16);
    public static final DeferredBlock<DecorBlock> WORKBENCH = decor("workbench", sheet(MapColor.METAL), Spec.facing(), 0, 0, 0, 16, 15, 16);
    public static final DeferredBlock<DecorBlock> LOCKER = decor("locker", sheet(MapColor.COLOR_GREEN), Spec.facing(), 1, 0, 2, 15, 16, 16);
    public static final DeferredBlock<DecorBlock> SHELVING = decor("shelving", sheet(MapColor.METAL), Spec.facing(), 0, 0, 1, 16, 16, 15);
    public static final DeferredBlock<DecorBlock> FIRE_EXTINGUISHER_WALL = decor("fire_extinguisher_wall", plate(), Spec.facing(), 5, 1, 11, 11, 13, 16);
    public static final DeferredBlock<DecorBlock> FIRE_HOSE_CABINET = decor("fire_hose_cabinet", sheet(MapColor.COLOR_RED), Spec.facing(), 1, 1, 13, 15, 15, 16);
    public static final DeferredBlock<DecorBlock> FIRST_AID_BOX = decor("first_aid_box", plate(), Spec.facing(), 4, 5, 14, 12, 11, 16);
    public static final DeferredBlock<DecorBlock> OIL_DRUM = decor("oil_drum", sheet(MapColor.COLOR_BLUE), Spec.of(), 1.5, 0, 1.5, 14.5, 15, 14.5);
    public static final DeferredBlock<DecorBlock> GAS_CYLINDERS = decor("gas_cylinders", sheet(MapColor.METAL), Spec.facing(), 0, 0, 7, 16, 15, 16);
    public static final DeferredBlock<DecorBlock> COLUMN_I_BEAM = decor("column_i_beam", sheet(MapColor.COLOR_BROWN), Spec.facing(), 3, 0, 3, 13, 16, 13);
    public static final DeferredBlock<DecorBlock> FLOOR_MARKING = decor("floor_marking", plate(), Spec.facing(), 0, 0, 0, 16, 0.1, 16);
    public static final DeferredBlock<DecorBlock> FLOOR_MARKING_HAZARD = decor("floor_marking_hazard", plate(), Spec.facing(), 0, 0, 0, 16, 0.1, 16);

    /** Шахтная вода в выработках — затопление (08 §8.2.9); предмета нет. */
    public static final DeferredBlock<LiquidBlock> MINE_WATER = BLOCKS.registerBlock("mine_water",
            p -> new LiquidBlock(PcFluids.MINE_WATER.source.get(), p),
            Properties.ofFullCopy(Blocks.WATER).mapColor(MapColor.TERRACOTTA_ORANGE).noLootTable());

    /** Реакторная тяжёлая вода из ведра: течёт как вода, в источник не превращается. */
    public static final DeferredBlock<LiquidBlock> HEAVY_WATER = BLOCKS.registerBlock("heavy_water",
            p -> new LiquidBlock(PcFluids.HEAVY_WATER.source.get(), p),
            Properties.ofFullCopy(Blocks.WATER).mapColor(MapColor.WATER).noLootTable());

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
    /** Аккумуляторная батарея оперативного тока 24 В рядом с выпрямителем реле. */
    public static final DeferredBlock<PcDeviceBlock> STATION_BATTERY = device("station_battery", machine().noOcclusion(),
            Spec.facing(PcProperties.CHARGE), dev.denfry.promptcritical.mod.foundry.FoundryBlock.with(
                    dev.denfry.promptcritical.mod.automation.StationBatteryBlockEntity::new));
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
    /** ПЛК (A3): программируемый контроллер на IL с модулями ввода-вывода. */
    public static final DeferredBlock<PcDeviceBlock> PLC = automationBlock("plc", Spec.facing(PcProperties.RUNNING));
    /** Логический блок ПАЗ (A3): голосование датчиков, останов снятием питания, байпас и квитирование. */
    public static final DeferredBlock<PcDeviceBlock> SIS_LOGIC = automationBlock("sis_logic_solver", Spec.facing(PcProperties.ALARM).footprint(FP_TALL2));


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

    private static DeferredBlock<LampBlock> lampBlock(String name, LampKind kind, Spec spec, double... box) {
        Properties props = Properties.of().mapColor(MapColor.METAL).strength(1.0f).sound(SoundType.LANTERN).noOcclusion()
                .lightLevel(st -> st.hasProperty(BlockStateProperties.LIT) && st.getValue(BlockStateProperties.LIT) ? kind.light() : 0);
        DeferredBlock<LampBlock> b = decorOf(BLOCKS.registerBlock(name, p -> LampBlock.create(p, spec, box, kind), props));
        LAMPS.add(b);
        return b;
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

    /** Порода нефтегазоносного разреза: колется киркой, в топологии не участвует. */
    private static DeferredBlock<PcBlock> geology(String name, MapColor color) {
        return geologyOf(block(name, Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops().strength(2.0f, 6.0f), Spec.of()));
    }

    private static DeferredBlock<PcBlock> geologyOf(DeferredBlock<PcBlock> b) {
        GEOLOGY.add(b);
        return b;
    }

    /** Породы разреза I2: вне топологии, как руды. */
    public static List<DeferredBlock<PcBlock>> geology() {
        return Collections.unmodifiableList(GEOLOGY);
    }

    /** Аппарат передела урана: один блок, фасад и флаг работы. */
    private static DeferredBlock<PcDeviceBlock> uranium(String name, BiFunction<BlockPos, net.minecraft.world.level.block.state.BlockState, ? extends net.minecraft.world.level.block.entity.BlockEntity> be) {
        return device(name, machine(), Spec.facing(PcProperties.RUNNING), FoundryBlock.with(be));
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
            if (!ORE_DROPS.containsKey(b) && !SELF_ASSAYED.contains(b) && !DECOR.contains(b) && !GEOLOGY.contains(b) && b != FIRECLAY
                    && b != VEIN_QUARTZ)
                out.add(b.get());
        }
        out.add(MULTIBLOCK_PART.get());
        out.add(KINETIC_PORT.get());
        return out;
    }
}

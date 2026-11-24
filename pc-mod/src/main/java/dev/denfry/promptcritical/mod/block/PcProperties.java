package dev.denfry.promptcritical.mod.block;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Свойства блоков мода; значения — состояния из арт-реестров 07 §7.23 и 08 §8.21. */
public final class PcProperties {
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");
    public static final BooleanProperty DAMAGED = BooleanProperty.create("damaged");
    public static final BooleanProperty ALARM = BooleanProperty.create("alarm");
    /** Ванна электрорафинирования в режиме никеля (08 §8.8.4; арт {@code mode=ni}). */
    public static final BooleanProperty NICKEL = BooleanProperty.create("nickel");
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    public static final BooleanProperty TOP = BooleanProperty.create("top");
    public static final BooleanProperty HAS_DOCUMENT = BooleanProperty.create("has_document");
    /** Котёл: уровень ушёл из водоуказательного стекла. */
    public static final BooleanProperty LOW_WATER = BooleanProperty.create("low_water");
    /** Котёл: обечайка разорвана (07 §7.9.6), ставится на оба сегмента. */
    public static final BooleanProperty EXPLODED = BooleanProperty.create("exploded");
    /** Секция градирни обмёрзла. */
    public static final BooleanProperty ICED = BooleanProperty.create("iced");
    /** Разъединитель: заземляющие ножи включены (07 §7.4.6). */
    public static final BooleanProperty EARTHED = BooleanProperty.create("earthed");
    /** Двигатель в исполнении 6 кВ ({@code voltage=mv} арт-реестра 07 §7.23). */
    public static final BooleanProperty MEDIUM_VOLTAGE = BooleanProperty.create("mv");

    public static final EnumProperty<OreHost> HOST = EnumProperty.create("host", OreHost.class);
    /** Монтаж ГГС (13 §13.3.4): пульт на стене или на столе, громкоговоритель на стене, на мачте или под потолком. */
    public static final EnumProperty<PaMount> INTERCOM_MOUNT = EnumProperty.create("mount", PaMount.class, PaMount.WALL, PaMount.DESK);
    public static final EnumProperty<PaMount> SPEAKER_MOUNT = EnumProperty.create("mount", PaMount.class, PaMount.WALL, PaMount.POLE,
            PaMount.CEILING);
    public static final EnumProperty<CoalRank> RANK = EnumProperty.create("rank", CoalRank.class);
    public static final EnumProperty<BoilerSegment> BOILER_SEGMENT = EnumProperty.create("segment", BoilerSegment.class);
    public static final EnumProperty<EngineSegment> ENGINE_SEGMENT = EnumProperty.create("segment", EngineSegment.class);
    public static final EnumProperty<ChimneyPart> CHIMNEY_PART = EnumProperty.create("part", ChimneyPart.class);
    public static final EnumProperty<SupportKind> SUPPORT_KIND = EnumProperty.create("kind", SupportKind.class);
    public static final EnumProperty<DoorKind> DOOR_KIND = EnumProperty.create("kind", DoorKind.class);
    /** Ступень окна регулятора проветривания ({@code VentilationDoorBlock#WINDOW_FRACTIONS}). */
    public static final IntegerProperty WINDOW = IntegerProperty.create("window", 0, 3);
    /** Заряд аккумуляторной батареи по банкам, 0…4. */
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 4);
    public static final EnumProperty<BarrierKind> BARRIER_KIND = EnumProperty.create("kind", BarrierKind.class);
    public static final EnumProperty<MoldStage> MOLD_STAGE = EnumProperty.create("stage", MoldStage.class);
    public static final EnumProperty<Bird> BIRD = EnumProperty.create("bird", Bird.class);
    /** Автоматика A1–A2 (03d, этап 9 M3): якорь реле притянут, выдержка истекла, станция в РУЧ. */
    public static final BooleanProperty PICKED = BooleanProperty.create("picked");
    public static final BooleanProperty DONE = BooleanProperty.create("done");
    public static final BooleanProperty MANUAL = BooleanProperty.create("manual");
    public static final EnumProperty<ProcessKind> PROCESS_KIND = EnumProperty.create("kind", ProcessKind.class);
    public static final EnumProperty<GasAlarm> GAS_ALARM = EnumProperty.create("alarm", GasAlarm.class);
    // коксохим и домна (этап 10 M3)
    public static final BooleanProperty HOT = BooleanProperty.create("hot");
    public static final BooleanProperty BURNT = BooleanProperty.create("burnt");
    public static final BooleanProperty FILLED = BooleanProperty.create("filled");
    public static final EnumProperty<OvenLook> OVEN_STATE = EnumProperty.create("state", OvenLook.class);
    /** Клетка коксовой батареи реального масштаба: ярус и торец с дверью (или анкерной колонной) вдоль оси камер. */
    public static final EnumProperty<CellLevel> CELL_LEVEL = EnumProperty.create("level", CellLevel.class);
    public static final EnumProperty<CellDoor> CELL_DOOR = EnumProperty.create("door", CellDoor.class);
    public static final EnumProperty<ByproductKind> BYPRODUCT_KIND = EnumProperty.create("kind", ByproductKind.class);
    public static final EnumProperty<CastHouseKind> CAST_HOUSE_KIND = EnumProperty.create("kind", CastHouseKind.class);
    public static final EnumProperty<StoveSegment> STOVE_SEGMENT = EnumProperty.create("segment", StoveSegment.class);
    public static final EnumProperty<WindTurbineSegment> WIND_TURBINE_SEGMENT = EnumProperty.create("segment", WindTurbineSegment.class);
    public static final EnumProperty<GasTurbineSegment> GAS_TURBINE_SEGMENT = EnumProperty.create("segment", GasTurbineSegment.class);
    public static final EnumProperty<ArcFurnaceSegment> ARC_FURNACE_SEGMENT = EnumProperty.create("segment", ArcFurnaceSegment.class);
    public static final EnumProperty<ReactorSegment> REACTOR_SEGMENT = EnumProperty.create("segment", ReactorSegment.class);
    public static final EnumProperty<HrsgSegment> HRSG_SEGMENT = EnumProperty.create("segment", HrsgSegment.class);
    public static final EnumProperty<DieselSegment> DIESEL_SEGMENT = EnumProperty.create("segment", DieselSegment.class);
    public static final EnumProperty<PwrSegment> PWR_SEGMENT = EnumProperty.create("segment", PwrSegment.class);
    public static final EnumProperty<BwrSegment> BWR_SEGMENT = EnumProperty.create("segment", BwrSegment.class);
    public static final EnumProperty<CanduSegment> CANDU_SEGMENT = EnumProperty.create("segment", CanduSegment.class);
    public static final EnumProperty<SfrSegment> SFR_SEGMENT = EnumProperty.create("segment", SfrSegment.class);
    public static final EnumProperty<GsSegment> GS_SEGMENT = EnumProperty.create("segment", GsSegment.class);
    // сталь, прокат, ВРУ (этап 10б M3)
    public static final BooleanProperty LOWERED = BooleanProperty.create("lowered");
    public static final BooleanProperty BURST = BooleanProperty.create("burst");
    // эксплуатация (13 §13.6.5–13.6.7, этап 11 M3)
    public static final IntegerProperty SEVERITY = IntegerProperty.create("severity", 0, 3);
    /** Замков на ящике блокировки LOTO (13 §13.16). */
    public static final IntegerProperty LOCKS = IntegerProperty.create("locks", 0, 6);
    public static final BooleanProperty PUMPING = BooleanProperty.create("pumping");
    public static final BooleanProperty PAPER = BooleanProperty.create("paper");
    /** Горелка котла I1 газовая (иначе пылеугольная) — по топливу котла. */
    public static final BooleanProperty GAS_FIRED = BooleanProperty.create("gas");
    // буровая I2: превентор закрыт, уровень раствора в ёмкости (пусто, рабочий, полно)
    public static final BooleanProperty SHUT = BooleanProperty.create("shut");
    public static final BooleanProperty CHARGED = BooleanProperty.create("charged");
    public static final IntegerProperty MUD_LEVEL = IntegerProperty.create("level", 0, 2);
    public static final EnumProperty<RollerKind> ROLLER_KIND = EnumProperty.create("kind", RollerKind.class);
    public static final EnumProperty<QuenchMedium> QUENCH_MEDIUM = EnumProperty.create("medium", QuenchMedium.class);
    public static final EnumProperty<CryoKind> CRYO_KIND = EnumProperty.create("kind", CryoKind.class);
    /** Плашки превентора 09 §9.5.7. */
    public static final EnumProperty<RamKind> RAM_KIND = EnumProperty.create("ram", RamKind.class);

    private PcProperties() {
    }

    private interface Named extends StringRepresentable {
        @Override
        default String getSerializedName() {
            return ((Enum<?>) this).name().toLowerCase(Locale.ROOT);
        }
    }

    /** Вмещающая порода руды 08 (модели {@code <id>}, {@code <id>_deepslate}, {@code <id>_tuff}). */
    public enum OreHost implements Named { STONE, DEEPSLATE, TUFF }

    /** Монтаж устройств ГГС. */
    public enum PaMount implements Named { WALL, DESK, POLE, CEILING }

    /** Марка угля пласта (08 §8.1.4). */
    public enum CoalRank implements Named { LIGNITE, BITUMINOUS, ANTHRACITE }

    /** Сегменты котла 3×3×6: топочный (фасад) и задний. */
    public enum BoilerSegment implements Named { FRONT, REAR }

    /** Сегменты паровой машины: цилиндр и рама с маховиком. */
    public enum EngineSegment implements Named { CYLINDER, FRAME }

    /**
     * Конец ряда барабана котла (art/blockbench/boilers/steam_drum_real.author.js): NONE — средняя секция, EAST/WEST —
     * днище с лазом на восточном или западном конце модели (+x или −x при фасаде на север); выбирается по соседям.
     */
    public enum DrumEnd implements Named { NONE, EAST, WEST }

    public static final EnumProperty<DrumEnd> DRUM_END = EnumProperty.create("end", DrumEnd.class);

    /** Звено дымовой трубы; выбирается по соседям. */
    public enum ChimneyPart implements Named { BASE, MIDDLE, TOP }

    public enum SupportKind implements Named { TIMBER, ARCH }

    public enum DoorKind implements Named { DOOR, REGULATOR }

    public enum BarrierKind implements Named { STONE, WATER }

    public enum MoldStage implements Named { EMPTY, POURED, COOLING }

    public enum Bird implements Named { EMPTY, ALIVE, FAINTED }

    /** Вид реле процесса (порядок — {@code ProcessSwitchKind}). */
    public enum ProcessKind implements Named { PRESSURE, LEVEL, TEMPERATURE, FLOW }

    /** Порог газоанализатора. */
    public enum GasAlarm implements Named { NONE, WARN, ALARM }

    /** Камера коксовой батареи на вид: пусто, коксуется (в т. ч. загрузка), готов. */
    public enum OvenLook implements Named { EMPTY, COKING, READY }

    /** Ярус клетки коксовой батареи: под, середина, свод. */
    public enum CellLevel implements Named { BOTTOM, MIDDLE, TOP }

    /** Торец клетки вдоль оси камер: внутри, торец к −оси, торец к +оси. */
    public enum CellDoor implements Named { NONE, NEG, POS }

    /** Аппарат улавливания (08 §8.4.4). */
    public enum ByproductKind implements Named { COOLER, PRECIPITATOR, SATURATOR, SCRUBBER }

    /** Машина литейного двора: бурмашина или пушка. */
    public enum CastHouseKind implements Named { DRILL, MUD_GUN }

    /** Секции газотурбинной установки 3×3×9: компрессор, камера сгорания, турбина. */
    public enum GasTurbineSegment implements Named { COMPRESSOR, COMBUSTOR, TURBINE }
    /** Секции дуговой печи: корпус 3×3×3 и свод над ним. */
    public enum ArcFurnaceSegment implements Named { SHELL, ROOF }

    /** Кладка T1: нижний куб — активная зона с отражателем, верхний — приводы СУЗ. */
    public enum ReactorSegment implements Named { CORE, DRIVES }

    /** Котёл-утилизатор 3×6×3: нижний куб — газоход с поверхностями нагрева, верхний — барабан. */
    public enum HrsgSegment implements Named { DUCT, DRUM }

    /** Аварийный ДГ 3×3×6: дизель спереди, генератор сзади. */
    public enum DieselSegment implements Named { ENGINE, ALTERNATOR }
    /** Реакторная установка T2a: корпус с активной зоной и верхний блок с приводами СУЗ. */
    public enum PwrSegment implements Named { VESSEL, HEAD }

    /**
     * Сегменты машин в реальном масштабе ({@link dev.denfry.promptcritical.mod.multiblock.Footprint#tiled}): у каждой
     * машины своё подмножество первых значений, см. {@link #wholeSegments(int)}.
     */
    public enum WholeSegment implements Named {
        S0, S1, S2, S3, S4, S5, S6, S7, S8, S9, S10, S11, S12, S13, S14, S15, S16, S17, S18, S19, S20, S21, S22, S23,
        S24, S25, S26, S27, S28, S29, S30, S31, S32, S33, S34, S35, S36, S37, S38, S39, S40, S41, S42, S43, S44, S45,
        S46, S47, S48, S49, S50, S51, S52, S53, S54, S55, S56, S57, S58, S59, S60, S61, S62, S63, S64, S65, S66, S67,
        S68, S69, S70, S71, S72, S73, S74, S75, S76, S77, S78, S79, S80, S81, S82, S83, S84, S85, S86, S87, S88, S89,
        S90, S91, S92, S93, S94, S95, S96, S97, S98, S99, S100, S101, S102, S103, S104, S105, S106, S107, S108, S109,
        S110, S111, S112, S113, S114, S115, S116, S117, S118, S119, S120, S121, S122, S123, S124, S125, S126, S127,
        S128, S129, S130, S131, S132, S133, S134, S135, S136, S137, S138, S139, S140, S141, S142, S143, S144, S145,
        S146, S147, S148, S149, S150, S151, S152, S153, S154, S155, S156, S157, S158, S159, S160, S161, S162, S163,
        S164, S165, S166, S167, S168, S169, S170, S171, S172, S173, S174, S175, S176, S177, S178, S179, S180, S181,
        S182, S183, S184, S185, S186, S187, S188, S189, S190, S191, S192, S193, S194, S195, S196, S197, S198, S199,
        S200, S201, S202, S203, S204, S205, S206, S207, S208, S209, S210, S211, S212, S213, S214, S215, S216, S217,
        S218, S219, S220, S221, S222, S223, S224, S225, S226, S227, S228, S229, S230, S231, S232, S233, S234, S235,
        S236, S237, S238, S239, S240, S241, S242, S243, S244, S245, S246, S247, S248, S249, S250, S251, S252, S253,
        S254, S255
    }

    /** Свойство сегмента машины из {@code count} сегментов (значения S0…S{count−1}). */
    public static EnumProperty<WholeSegment> wholeSegments(int count) {
        if (count < 1 || count > WholeSegment.values().length) throw new IllegalArgumentException("segments: " + count);
        return EnumProperty.create("segment", WholeSegment.class, v -> v.ordinal() < count);
    }

    /** Кипящий реактор T2b: корпус с зоной и верхняя часть с сепараторами и осушителями пара. */
    public enum BwrSegment implements Named { VESSEL, SEPARATORS }
    /** Натриевый быстрый реактор T4: бассейн с зоной и экраном и поворотная пробка с приводами стержней. */
    public enum SfrSegment implements Named { POOL, PLUG }

    /** Тяжеловодный реактор T3: каландр с каналами и мост перегрузочной машины над ним. */
    public enum CanduSegment implements Named { CALANDRIA, FUELLING }

    /** Сероводородный завод тяжёлой воды: холодная колонна внизу, горячая над ней. */
    public enum GsSegment implements Named { COLD, HOT }

    /** Секции башни ВЭУ 1×9×1: основание со шкафом, средняя секция, оголовок с опорно-поворотным кругом. */
    public enum WindTurbineSegment implements Named { BASE, MIDDLE, TOP }

    /** Секции воздухонагревателя 3×9×3: низ, середина, купол. */
    public enum StoveSegment implements Named { BOTTOM, MIDDLE, DOME }

    /** Рольганг или холодильник (08 §8.7.2). */
    public enum RollerKind implements Named { TABLE, COOLING_BED }

    /** Закалочная среда: вода или масло. */
    public enum QuenchMedium implements Named { WATER, OIL }

    /** Криогенный бак или испаритель (08 §8.11). */
    public enum CryoKind implements Named { STORAGE, VAPORIZER }

    /** Плашки превентора (09 §9.5.7): трубные, глухие, срезающие. */
    public enum RamKind implements Named { PIPE, BLIND, SHEAR }
}

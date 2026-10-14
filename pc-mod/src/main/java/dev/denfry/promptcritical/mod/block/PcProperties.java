package dev.denfry.promptcritical.mod.block;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** Свойства блоков мода; значения — состояния из арт-реестров 07 §7.23 и 08 §8.21. */
public final class PcProperties {
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");
    public static final BooleanProperty DAMAGED = BooleanProperty.create("damaged");
    public static final BooleanProperty ALARM = BooleanProperty.create("alarm");
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    public static final BooleanProperty TOP = BooleanProperty.create("top");
    public static final BooleanProperty HAS_DOCUMENT = BooleanProperty.create("has_document");

    public static final EnumProperty<OreHost> HOST = EnumProperty.create("host", OreHost.class);
    public static final EnumProperty<CoalRank> RANK = EnumProperty.create("rank", CoalRank.class);
    public static final EnumProperty<BoilerSegment> BOILER_SEGMENT = EnumProperty.create("segment", BoilerSegment.class);
    public static final EnumProperty<EngineSegment> ENGINE_SEGMENT = EnumProperty.create("segment", EngineSegment.class);
    public static final EnumProperty<ChimneyPart> CHIMNEY_PART = EnumProperty.create("part", ChimneyPart.class);
    public static final EnumProperty<SupportKind> SUPPORT_KIND = EnumProperty.create("kind", SupportKind.class);
    public static final EnumProperty<DoorKind> DOOR_KIND = EnumProperty.create("kind", DoorKind.class);
    public static final EnumProperty<BarrierKind> BARRIER_KIND = EnumProperty.create("kind", BarrierKind.class);
    public static final EnumProperty<MoldStage> MOLD_STAGE = EnumProperty.create("stage", MoldStage.class);
    public static final EnumProperty<Bird> BIRD = EnumProperty.create("bird", Bird.class);

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

    /** Марка угля пласта (08 §8.1.4). */
    public enum CoalRank implements Named { LIGNITE, BITUMINOUS, ANTHRACITE }

    /** Сегменты котла 3×3×6: топочный (фасад) и задний. */
    public enum BoilerSegment implements Named { FRONT, REAR }

    /** Сегменты паровой машины: цилиндр и рама с маховиком. */
    public enum EngineSegment implements Named { CYLINDER, FRAME }

    /** Звено дымовой трубы; выбирается по соседям. */
    public enum ChimneyPart implements Named { BASE, MIDDLE, TOP }

    public enum SupportKind implements Named { TIMBER, ARCH }

    public enum DoorKind implements Named { DOOR, REGULATOR }

    public enum BarrierKind implements Named { STONE, WATER }

    public enum MoldStage implements Named { EMPTY, POURED, COOLING }

    public enum Bird implements Named { EMPTY, ALIVE, FAINTED }
}

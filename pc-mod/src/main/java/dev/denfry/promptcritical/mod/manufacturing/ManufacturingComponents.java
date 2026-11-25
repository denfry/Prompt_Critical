package dev.denfry.promptcritical.mod.manufacturing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.manufacturing.PartDefect;
import dev.denfry.promptcritical.sim.manufacturing.PartRoute;
import dev.denfry.promptcritical.sim.manufacturing.PartSpec;
import dev.denfry.promptcritical.sim.manufacturing.assembly.MachinePassport;
import dev.denfry.promptcritical.sim.metallurgy.HeatTreatState;
import io.netty.buffer.ByteBuf;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Компоненты предметов производства деталей: {@code part_spec}, паспорт детали (кузнечная заготовка, поковка,
 * проверенная деталь). Декодер терпимый: значения квантуются, чтобы паспорта из старых или чужих версий не
 * бросали исключений и не разбивали стеки; неизвестные дефекты и уровни вне 1..3 пропускаются.
 */
public final class ManufacturingComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PromptCritical.MOD_ID);

    /** Запасной Ra при нуле, отрицательном или нечисловом значении: низший класс ряда, мкм. */
    private static final double RA_FALLBACK_UM = 100;

    private static final Codec<PartRoute> ROUTE = Codec.STRING.xmap(
            s -> enumOr(PartRoute.class, s, PartRoute.FORGED), PartRoute::name);
    private static final Codec<HeatTreatState> HEAT_TREAT = Codec.STRING.xmap(
            s -> enumOr(HeatTreatState.class, s, HeatTreatState.AS_ROLLED), HeatTreatState::name);
    /** Дефекты по имени; неизвестные имена и уровни вне 1..3 пропускаются, а не роняют разбор. */
    private static final Codec<Map<PartDefect, Integer>> DEFECTS = Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(
            ManufacturingComponents::decodeDefects, ManufacturingComponents::encodeDefects);

    public static final Codec<PartSpec> PART_SPEC_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("kind").forGetter(PartSpec::kind),
            Codec.STRING.fieldOf("material").forGetter(PartSpec::material),
            ROUTE.fieldOf("route").forGetter(PartSpec::route),
            HEAT_TREAT.fieldOf("heat_treat").forGetter(PartSpec::heatTreat),
            Codec.INT.fieldOf("hardness_hrc").forGetter(PartSpec::hardnessHrc),
            Codec.INT.fieldOf("it_grade").forGetter(PartSpec::itGrade),
            Codec.DOUBLE.fieldOf("ra_um").forGetter(PartSpec::raUm),
            DEFECTS.optionalFieldOf("defects", Map.of()).forGetter(PartSpec::defects),
            Codec.BOOL.optionalFieldOf("inspected", false).forGetter(PartSpec::inspected)).apply(i,
            ManufacturingComponents::decode));
    public static final StreamCodec<ByteBuf, PartSpec> PART_SPEC_STREAM = ByteBufCodecs.fromCodec(PART_SPEC_CODEC);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PartSpec>> PART_SPEC =
            COMPONENTS.registerComponentType("part_spec", b -> b.persistent(PART_SPEC_CODEC).networkSynchronized(PART_SPEC_STREAM));
    /** Поковка (18 §18.7): заготовка с историей процесса. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WorkpieceData>> WORKPIECE =
            COMPONENTS.registerComponentType("workpiece", b -> b.persistent(WorkpieceData.CODEC)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.fromCodec(WorkpieceData.CODEC)));
    /** Пруток и проволока под волочение (18 §18.6.7). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WireData>> WIRE =
            COMPONENTS.registerComponentType("wire", b -> b.persistent(WireData.CODEC)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.fromCodec(WireData.CODEC)));

    /** Лист электротехнической стали (18 §18.6.8). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ElectricalData.Sheet>> ELECTRICAL_SHEET =
            COMPONENTS.registerComponentType("electrical_sheet", b -> b.persistent(ElectricalData.Sheet.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(ElectricalData.Sheet.CODEC)));
    /** Пачка вырубленных пластин статора (18 §18.6.8). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ElectricalData.Bundle>> LAMINATION =
            COMPONENTS.registerComponentType("lamination", b -> b.persistent(ElectricalData.Bundle.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(ElectricalData.Bundle.CODEC)));
    /** Шихтованный пакет статора (18 §18.6.8). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ElectricalData.Core>> STATOR_CORE =
            COMPONENTS.registerComponentType("stator_core", b -> b.persistent(ElectricalData.Core.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(ElectricalData.Core.CODEC)));
    /**
     * Паспорт машины (18 §18.8.3): начальное состояние модели обслуживания 13. Декодер терпимый: значения вне
     * допустимого диапазона прижимаются, а не роняют разбор.
     */
    public static final Codec<MachinePassport> MACHINE_PASSPORT_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("bearing_wear", 1.0).forGetter(MachinePassport::bearingWearFactor),
            Codec.DOUBLE.optionalFieldOf("imbalance_mm_s", 0.0).forGetter(MachinePassport::imbalanceMmS),
            Codec.DOUBLE.optionalFieldOf("misalignment_mm", 0.0).forGetter(MachinePassport::misalignmentMm),
            Codec.DOUBLE.optionalFieldOf("seal_leak", 0.0).forGetter(MachinePassport::sealLeak),
            Codec.DOUBLE.optionalFieldOf("flange_leak", 0.0).forGetter(MachinePassport::flangeLeak),
            Codec.DOUBLE.optionalFieldOf("insulation_mohm", -1.0)
                    .forGetter(p -> p.hasWinding() ? p.insulationMOhm() : -1.0),
            Codec.DOUBLE.optionalFieldOf("efficiency", 1.0).forGetter(MachinePassport::efficiencyFactor),
            Codec.INT.optionalFieldOf("score", 100).forGetter(MachinePassport::score)).apply(i,
            ManufacturingComponents::decodePassport));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MachinePassport>> MACHINE_PASSPORT =
            COMPONENTS.registerComponentType("machine_passport", b -> b.persistent(MACHINE_PASSPORT_CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(MACHINE_PASSPORT_CODEC)));

    /** Намотанный статор (18 §18.6.9, §18.8.3). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ElectricalData.Wound>> STATOR =
            COMPONENTS.registerComponentType("stator", b -> b.persistent(ElectricalData.Wound.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(ElectricalData.Wound.CODEC)));

    /**
     * Сделанные замеры детали до отметки «проверено» (18 §18.6.10): маска битов {@code PartInspection.Check}. Снимается,
     * когда паспорт получает отметку.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> INSPECTION_CHECKS =
            COMPONENTS.registerComponentType("inspection_checks", b -> b.persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    private ManufacturingComponents() {
    }

    /** Собирает паспорт так, чтобы валидирующий конструктор не мог бросить исключение. */
    private static PartSpec decode(String kind, String material, PartRoute route, HeatTreatState heatTreat, int hrc,
                                   int itGrade, double raUm, Map<PartDefect, Integer> defects, boolean inspected) {
        double ra = Double.isFinite(raUm) && raUm > 0 ? raUm : RA_FALLBACK_UM;
        return new PartSpec(kind, material, route, heatTreat, PartSpec.quantizeHrc(hrc), Math.max(0, itGrade),
                ra, defects, inspected);
    }

    private static Map<PartDefect, Integer> decodeDefects(Map<String, Integer> raw) {
        var out = new EnumMap<PartDefect, Integer>(PartDefect.class);
        for (var e : raw.entrySet()) {
            PartDefect d = enumOr(PartDefect.class, e.getKey(), null);
            int level = e.getValue();
            if (d != null && level >= 1 && level <= PartSpec.MAX_DEFECT_LEVEL) out.put(d, level);
        }
        return out;
    }

    private static Map<String, Integer> encodeDefects(Map<PartDefect, Integer> defects) {
        var out = new LinkedHashMap<String, Integer>();
        for (var e : defects.entrySet()) out.put(e.getKey().name(), e.getValue());
        return out;
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String name, E fallback) {
        for (E e : type.getEnumConstants()) if (e.name().equals(name)) return e;
        return fallback;
    }

    static MachinePassport decodePassport(double wear, double imbalance, double misalign, double seal, double flange,
            double insulation, double efficiency, int score) {
        return new MachinePassport(finite(wear, 1, 0.05, 100), finite(imbalance, 0, 0, 100), finite(misalign, 0, 0, 10),
                finite(seal, 0, 0, 10), finite(flange, 0, 0, 10),
                Double.isFinite(insulation) && insulation > 0 ? Math.min(insulation, 1e6) : Double.NaN,
                finite(efficiency, 1, 0.01, 1.05), Math.clamp(score, 0, 100));
    }

    private static double finite(double v, double fallback, double min, double max) {
        return Double.isFinite(v) ? Math.clamp(v, min, max) : fallback;
    }
}

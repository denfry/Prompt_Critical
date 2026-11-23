package dev.denfry.promptcritical.mod.manufacturing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.manufacturing.PartDefect;
import dev.denfry.promptcritical.sim.manufacturing.PartRoute;
import dev.denfry.promptcritical.sim.manufacturing.PartSpec;
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

    private ManufacturingComponents() {
    }

    /** Собирает паспорт так, чтобы валидирующий конструктор не мог бросить исключение. */
    private static PartSpec decode(String kind, String material, PartRoute route, HeatTreatState heatTreat, int hrc,
                                   int itGrade, double raUm, Map<PartDefect, Integer> defects, boolean inspected) {
        double ra = Double.isFinite(raUm) && raUm > 0 ? raUm : RA_FALLBACK_UM;
        return new PartSpec(kind, material, route, heatTreat, PartSpec.quantizeHrc(hrc), Math.max(0, itGrade),
                PartSpec.quantizeRa(ra), defects, inspected);
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
}

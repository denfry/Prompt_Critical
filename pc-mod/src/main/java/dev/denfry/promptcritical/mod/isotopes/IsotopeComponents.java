package dev.denfry.promptcritical.mod.isotopes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.environment.ActivityData;
import dev.denfry.promptcritical.sim.isotopes.TargetMaterial;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/**
 * Компоненты изотопов (12 §12.13): {@code irradiation_target} — состояние мишени капсулы, {@code shielded_source} —
 * источник внутри защитного контейнера (дефектоскоп), {@code film_result} — что показала проявленная плёнка.
 */
public final class IsotopeComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PromptCritical.MOD_ID);

    /**
     * Мишень капсулы: материал ({@link TargetMaterial#name()}), масса вещества, ядра звеньев цепочки, набранный флюенс,
     * игровое время, к которому относятся ядра, и герметичность. Распад между облучениями досчитывается по {@code ref_tick}.
     */
    public record IrradiationTarget(String material, double massKg, List<Double> atoms, double fluence, long refTick, boolean sealed) {
        public static final Codec<IrradiationTarget> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("material").forGetter(IrradiationTarget::material),
                Codec.DOUBLE.fieldOf("mass_kg").forGetter(IrradiationTarget::massKg),
                Codec.DOUBLE.listOf().fieldOf("atoms").forGetter(IrradiationTarget::atoms),
                Codec.DOUBLE.optionalFieldOf("fluence", 0.0).forGetter(IrradiationTarget::fluence),
                Codec.LONG.fieldOf("ref_tick").forGetter(IrradiationTarget::refTick),
                Codec.BOOL.optionalFieldOf("sealed", true).forGetter(IrradiationTarget::sealed)).apply(i, IrradiationTarget::new));
        public static final StreamCodec<ByteBuf, IrradiationTarget> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, IrradiationTarget::material, ByteBufCodecs.DOUBLE, IrradiationTarget::massKg,
                ByteBufCodecs.DOUBLE.apply(ByteBufCodecs.list()), IrradiationTarget::atoms, ByteBufCodecs.DOUBLE, IrradiationTarget::fluence,
                ByteBufCodecs.VAR_LONG, IrradiationTarget::refTick, ByteBufCodecs.BOOL, IrradiationTarget::sealed, IrradiationTarget::new);

        public IrradiationTarget {
            atoms = List.copyOf(atoms);
        }

        public static IrradiationTarget fresh(TargetMaterial m, double massKg, long tick) {
            List<Double> a = new ArrayList<>();
            for (double v : m.freshAtoms(massKg)) a.add(v);
            return new IrradiationTarget(m.name(), massKg, a, 0, tick, true);
        }

        @Nullable
        public TargetMaterial materialOrNull() {
            for (TargetMaterial m : TargetMaterial.values()) if (m.name().equals(material)) return m;
            return null;
        }

        public double[] atomsArray() {
            double[] a = new double[atoms.size()];
            for (int i = 0; i < a.length; i++) a[i] = atoms.get(i);
            return a;
        }

        /** Ядра на игровое время {@code tick}: распад вне потока с {@code ref_tick}. */
        public double[] atomsAt(long tick) {
            double[] a = atomsArray();
            TargetMaterial m = materialOrNull();
            if (m == null || a.length != m.chain.size()) return a;
            double dt = Math.max(0, tick - refTick) * ActivityData.GAME_S_PER_TICK;
            if (dt > 0) m.step(a, 0, 1, dt, a);
            return a;
        }

        public IrradiationTarget with(double[] a, double fluence, long tick, boolean sealed) {
            List<Double> l = new ArrayList<>(a.length);
            for (double v : a) l.add(v);
            return new IrradiationTarget(material, massKg, l, fluence, tick, sealed);
        }

        /** Облучалась ли мишень (есть продукт). */
        public boolean irradiated() {
            for (int i = 1; i < atoms.size(); i++) if (atoms.get(i) > 0) return true;
            return false;
        }
    }

    /** Проявленная плёнка: качество ({@link dev.denfry.promptcritical.sim.isotopes.Radiography.Film}), дефект, марка трубы. */
    public record FilmResult(int quality, int defectKind, String pipe) {
        public static final Codec<FilmResult> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("quality").forGetter(FilmResult::quality),
                Codec.INT.fieldOf("defect").forGetter(FilmResult::defectKind),
                Codec.STRING.optionalFieldOf("pipe", "").forGetter(FilmResult::pipe)).apply(i, FilmResult::new));
        public static final StreamCodec<ByteBuf, FilmResult> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT,
                FilmResult::quality, ByteBufCodecs.VAR_INT, FilmResult::defectKind, ByteBufCodecs.STRING_UTF8, FilmResult::pipe, FilmResult::new);
    }

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<IrradiationTarget>> IRRADIATION_TARGET =
            COMPONENTS.registerComponentType("irradiation_target", b -> b.persistent(IrradiationTarget.CODEC)
                    .networkSynchronized(IrradiationTarget.STREAM_CODEC));
    /** Источник в контейнере: та же активность, что у {@link ActivityData}, но излучает ослабленным защитой. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ActivityData>> SHIELDED_SOURCE =
            COMPONENTS.registerComponentType("shielded_source", b -> b.persistent(ActivityData.CODEC).networkSynchronized(ActivityData.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FilmResult>> FILM_RESULT =
            COMPONENTS.registerComponentType("film_result", b -> b.persistent(FilmResult.CODEC).networkSynchronized(FilmResult.STREAM_CODEC));

    /** Выгорание кассеты блочков T0, МВт·сут/кг U (12 §12.0.2). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Double>> BURNUP =
            COMPONENTS.registerComponentType("burnup", b -> b.persistent(Codec.DOUBLE).networkSynchronized(ByteBufCodecs.DOUBLE));

    private IsotopeComponents() {
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }

    /** Значение ключа конфигурации или значение по умолчанию, пока конфиг не загружен (тесты, датаген). */
    static double cfg(ModConfigSpec.DoubleValue v, double def) {
        return ServerConfig.SPEC.isLoaded() ? v.get() : def;
    }

    static double gammaHeatingWPerG() {
        return cfg(ServerConfig.ISOTOPES_GAMMA_HEATING_W_PER_G, 5.0);
    }

    static double filmExposureGy() {
        return cfg(ServerConfig.ISOTOPES_FILM_EXPOSURE_GY, 5e-3);
    }

    static double rtgSystemFactor() {
        return cfg(ServerConfig.ISOTOPES_RTG_TE_SYSTEM_FACTOR, 0.8);
    }

    static double rtgDegradationPerYear() {
        return cfg(ServerConfig.ISOTOPES_RTG_TE_DEGRADATION_PER_YEAR, 0.03);
    }
}

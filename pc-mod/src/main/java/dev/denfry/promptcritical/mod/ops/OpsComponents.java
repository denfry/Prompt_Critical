package dev.denfry.promptcritical.mod.ops;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.mod.PromptCritical;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Компоненты предметов эксплуатации: лента самописца {@code chart_trace} (13 §13.6.7). */
public final class OpsComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PromptCritical.MOD_ID);

    /**
     * Снятая лента: величина (ключ {@code quantity.*}), шкала, шаг записи, куда легла бы следующая точка, точки
     * ({@link dev.denfry.promptcritical.sim.ops.ChartDrum#EMPTY} — пусто) и тик мира, когда ленту сняли.
     */
    public record ChartTrace(String quantity, float min, float max, int sampleS, int position, List<Integer> points,
            long tornAt) {
        public static final Codec<ChartTrace> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("quantity").forGetter(ChartTrace::quantity),
                Codec.FLOAT.fieldOf("min").forGetter(ChartTrace::min),
                Codec.FLOAT.fieldOf("max").forGetter(ChartTrace::max),
                Codec.INT.fieldOf("sample_s").forGetter(ChartTrace::sampleS),
                Codec.INT.fieldOf("position").forGetter(ChartTrace::position),
                Codec.INT.listOf().fieldOf("points").forGetter(ChartTrace::points),
                Codec.LONG.optionalFieldOf("torn_at", 0L).forGetter(ChartTrace::tornAt)).apply(i, ChartTrace::new));
        public static final StreamCodec<ByteBuf, ChartTrace> STREAM = ByteBufCodecs.fromCodec(CODEC);

        public ChartTrace {
            points = List.copyOf(points);
        }

        /** Значение точки {@code i}; NaN — пусто. */
        public double value(int i) {
            int q = points.get(i);
            if (q == dev.denfry.promptcritical.sim.ops.ChartDrum.EMPTY) return Double.NaN;
            return min + (q + (double) Short.MAX_VALUE) / (2.0 * Short.MAX_VALUE) * (max - min);
        }
    }

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ChartTrace>> CHART_TRACE =
            COMPONENTS.registerComponentType("chart_trace", b -> b.persistent(ChartTrace.CODEC).networkSynchronized(ChartTrace.STREAM));

    private OpsComponents() {
    }
}

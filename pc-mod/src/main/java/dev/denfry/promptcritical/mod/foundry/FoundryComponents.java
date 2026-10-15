package dev.denfry.promptcritical.mod.foundry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.metallurgy.CastingDefect;
import dev.denfry.promptcritical.sim.metallurgy.CastingKind;
import dev.denfry.promptcritical.sim.metallurgy.CastingSpec;
import io.netty.buffer.ByteBuf;
import java.util.Locale;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Компоненты предметов литейки (08 §8.17, §8.20): {@code casting_spec} отливки, модель {@code casting_pattern} и
 * заформованная {@code sand_mold}.
 */
public final class FoundryComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PromptCritical.MOD_ID);

    private static final Codec<CastingKind> KIND = Codec.STRING.xmap(s -> CastingKind.valueOf(s.toUpperCase(Locale.ROOT)),
            k -> k.name().toLowerCase(Locale.ROOT));
    private static final Codec<CastingDefect> DEFECT = Codec.STRING.xmap(CastingDefect::valueOf, CastingDefect::name);
    private static final StreamCodec<ByteBuf, CastingKind> KIND_STREAM = ByteBufCodecs.VAR_INT
            .map(i -> CastingKind.values()[i], CastingKind::ordinal);

    public static final Codec<CastingSpec> CASTING_SPEC_CODEC = RecordCodecBuilder.create(i -> i.group(
            KIND.fieldOf("kind").forGetter(CastingSpec::kind),
            Codec.STRING.fieldOf("alloy").forGetter(CastingSpec::alloyId),
            Codec.INT.optionalFieldOf("dn", 0).forGetter(CastingSpec::dn),
            DEFECT.fieldOf("defect").forGetter(CastingSpec::defect),
            Codec.INT.optionalFieldOf("inspected", 0).forGetter(CastingSpec::inspectedMask),
            Codec.LONG.fieldOf("serial").forGetter(CastingSpec::serial),
            Codec.INT.optionalFieldOf("machined", 0).forGetter(CastingSpec::machinedMask)).apply(i, CastingSpec::new));
    public static final StreamCodec<ByteBuf, CastingSpec> CASTING_SPEC_STREAM = ByteBufCodecs.fromCodec(CASTING_SPEC_CODEC);

    /** Модель отливки: вид и прибыль. */
    public record Pattern(CastingKind kind, boolean riser) {
        public static final Pattern DEFAULT = new Pattern(CastingKind.VALVE_BODY, true);
        public static final Codec<Pattern> CODEC = RecordCodecBuilder.create(i -> i.group(
                KIND.fieldOf("kind").forGetter(Pattern::kind),
                Codec.BOOL.optionalFieldOf("riser", true).forGetter(Pattern::riser)).apply(i, Pattern::new));
        public static final StreamCodec<ByteBuf, Pattern> STREAM = StreamCodec.composite(
                KIND_STREAM, Pattern::kind, ByteBufCodecs.BOOL, Pattern::riser, Pattern::new);
    }

    /** Заформованная песчаная форма: модель и влажность смеси. */
    public record Mold(CastingKind kind, boolean riser, float moisture) {
        public static final Codec<Mold> CODEC = RecordCodecBuilder.create(i -> i.group(
                KIND.fieldOf("kind").forGetter(Mold::kind),
                Codec.BOOL.optionalFieldOf("riser", true).forGetter(Mold::riser),
                Codec.FLOAT.fieldOf("moisture").forGetter(Mold::moisture)).apply(i, Mold::new));
        public static final StreamCodec<ByteBuf, Mold> STREAM = StreamCodec.composite(
                KIND_STREAM, Mold::kind, ByteBufCodecs.BOOL, Mold::riser, ByteBufCodecs.FLOAT, Mold::moisture, Mold::new);
    }

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CastingSpec>> CASTING_SPEC =
            COMPONENTS.registerComponentType("casting_spec", b -> b.persistent(CASTING_SPEC_CODEC).networkSynchronized(CASTING_SPEC_STREAM));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Pattern>> CASTING_PATTERN =
            COMPONENTS.registerComponentType("casting_pattern", b -> b.persistent(Pattern.CODEC).networkSynchronized(Pattern.STREAM));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Mold>> SAND_MOLD =
            COMPONENTS.registerComponentType("sand_mold", b -> b.persistent(Mold.CODEC).networkSynchronized(Mold.STREAM));

    private FoundryComponents() {
    }
}

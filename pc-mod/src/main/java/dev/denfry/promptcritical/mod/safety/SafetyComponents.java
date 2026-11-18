package dev.denfry.promptcritical.mod.safety;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.ops.FilterType;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Компоненты СИЗ 13 §13.15: вставка каски, фильтр противогаза, остаток воздуха аппарата, точка крепления фала. */
public final class SafetyComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PromptCritical.MOD_ID);

    /** Фильтр: тип и поглощённое (моли газа, у P — мг частиц). */
    public record Filter(FilterType type, double loaded) {
        static final Codec<FilterType> TYPE = Codec.STRING.xmap(FilterType::valueOf, FilterType::name);
        public static final Codec<Filter> CODEC = RecordCodecBuilder.create(i -> i.group(
                TYPE.fieldOf("type").forGetter(Filter::type),
                Codec.DOUBLE.optionalFieldOf("loaded", 0.0).forGetter(Filter::loaded)).apply(i, Filter::new));
        public static final StreamCodec<ByteBuf, Filter> STREAM = ByteBufCodecs.fromCodec(CODEC);
    }

    /** Вставка в каску (13 §13.7): противогаз или противошумные наушники. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>> PPE_ATTACHMENTS =
            COMPONENTS.registerComponentType("ppe_attachments", b -> b.persistent(ItemContainerContents.CODEC)
                    .networkSynchronized(ItemContainerContents.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Filter>> FILTER_TYPE =
            COMPONENTS.registerComponentType("filter_type", b -> b.persistent(Filter.CODEC).networkSynchronized(Filter.STREAM));
    /** Остаток воздуха изолирующего аппарата, л. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Double>> AIR_REMAINING =
            COMPONENTS.registerComponentType("air_remaining", b -> b.persistent(Codec.DOUBLE).networkSynchronized(ByteBufCodecs.DOUBLE));
    /** Опора, к которой пристёгнут фал. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> LIFELINE_ANCHOR =
            COMPONENTS.registerComponentType("lifeline_anchor", b -> b.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));

    private SafetyComponents() {
    }

    /** Вставка каски (копия) или {@code null}. */
    @org.jetbrains.annotations.Nullable
    public static ItemStack insert(ItemStack hat) {
        ItemContainerContents c = hat.get(PPE_ATTACHMENTS.get());
        if (c == null) return null;
        ItemStack s = c.copyOne();
        return s.isEmpty() ? null : s;
    }

    /** Вставить в каску {@code insert} (одну штуку); пустой стек — вынуть. */
    public static void setInsert(ItemStack hat, ItemStack insert) {
        if (insert.isEmpty()) hat.remove(PPE_ATTACHMENTS.get());
        else hat.set(PPE_ATTACHMENTS.get(), ItemContainerContents.fromItems(java.util.List.of(insert.copyWithCount(1))));
    }
}

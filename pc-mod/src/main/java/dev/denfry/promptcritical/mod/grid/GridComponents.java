package dev.denfry.promptcritical.mod.grid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.grid.RelayGeneration;
import dev.denfry.promptcritical.sim.grid.RelaySetting;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Компоненты предметов сети (07 §7.22): {@code relay_settings} модуля РЗА (уставки переносятся вместе с ним) и
 * {@code wire_link} — начатый катушкой провода пролёт.
 */
public final class GridComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PromptCritical.MOD_ID);

    /** Уставки модуля; вид модуля задаёт предмет. */
    public record Settings(double a, double b) {
        public static final Codec<Settings> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("a").forGetter(Settings::a),
                Codec.DOUBLE.fieldOf("b").forGetter(Settings::b)).apply(i, Settings::new));
        public static final StreamCodec<ByteBuf, Settings> STREAM = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, Settings::a, ByteBufCodecs.DOUBLE, Settings::b, Settings::new);

        public static Settings of(RelaySetting.Kind kind) {
            return new Settings(kind.defaultA, kind.defaultB);
        }

        public RelaySetting toSetting(RelaySetting.Kind kind) {
            return new RelaySetting(kind, RelayGeneration.A1_ELECTROMECHANICAL, a, b);
        }
    }

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Settings>> RELAY_SETTINGS =
            COMPONENTS.registerComponentType("relay_settings", b -> b.persistent(Settings.CODEC).networkSynchronized(Settings.STREAM));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> WIRE_LINK =
            COMPONENTS.registerComponentType("wire_link", b -> b.persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC));

    private GridComponents() {
    }
}

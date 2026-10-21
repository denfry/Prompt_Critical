package dev.denfry.promptcritical.mod.automation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.mod.PromptCritical;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Компонент {@code terminal_link} — клемма, с которой начат провод {@code wiring_tool}. */
public final class AutomationComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PromptCritical.MOD_ID);

    public record TerminalLink(BlockPos pos, int terminal) {
        public static final Codec<TerminalLink> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(TerminalLink::pos),
                Codec.INT.fieldOf("terminal").forGetter(TerminalLink::terminal)).apply(i, TerminalLink::new));
        public static final StreamCodec<ByteBuf, TerminalLink> STREAM = StreamCodec.composite(
                BlockPos.STREAM_CODEC, TerminalLink::pos, ByteBufCodecs.VAR_INT, TerminalLink::terminal, TerminalLink::new);
    }

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TerminalLink>> TERMINAL_LINK =
            COMPONENTS.registerComponentType("terminal_link", b -> b.persistent(TerminalLink.CODEC).networkSynchronized(TerminalLink.STREAM));

    private AutomationComponents() {
    }
}

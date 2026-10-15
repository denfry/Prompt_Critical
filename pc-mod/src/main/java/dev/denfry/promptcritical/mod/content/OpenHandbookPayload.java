package dev.denfry.promptcritical.mod.content;

import dev.denfry.promptcritical.mod.PromptCritical;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Сервер → клиент: открыть справочник с готовым представлением ({@link HandbookService#view}) в JSON. */
public record OpenHandbookPayload(String json) implements CustomPacketPayload {
    public static final Type<OpenHandbookPayload> TYPE = new Type<>(PromptCritical.id("open_handbook"));
    /** Справочник целиком на одном языке — десятки килобайт; предел с запасом. */
    public static final int MAX_CHARS = 1 << 20;
    public static final StreamCodec<ByteBuf, OpenHandbookPayload> STREAM_CODEC =
            ByteBufCodecs.stringUtf8(MAX_CHARS).map(OpenHandbookPayload::new, OpenHandbookPayload::json);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

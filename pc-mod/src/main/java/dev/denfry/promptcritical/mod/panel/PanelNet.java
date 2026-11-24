package dev.denfry.promptcritical.mod.panel;

import dev.denfry.promptcritical.mod.PromptCritical;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Пакеты щита сверх кнопок ({@code clickMenuButton}): строка из поля ввода {@link PanelLayout.TextField}. */
public final class PanelNet {
    /** Предел строки в пакете; поле щита режет строже, по своему {@code maxLength}. */
    public static final int MAX_CHARS = 256;

    private PanelNet() {
    }

    /** Клиент → сервер: текст поля {@code field} открытого щита {@code containerId}. */
    public record Text(int containerId, int field, String text) implements CustomPacketPayload {
        public static final Type<Text> TYPE = new Type<>(PromptCritical.id("panel_text"));
        public static final StreamCodec<FriendlyByteBuf, Text> STREAM_CODEC = StreamCodec.of(
                (b, p) -> {
                    b.writeVarInt(p.containerId);
                    b.writeVarInt(p.field);
                    b.writeUtf(p.text, MAX_CHARS);
                },
                b -> new Text(b.readVarInt(), b.readVarInt(), b.readUtf(MAX_CHARS)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PromptCritical.MOD_ID).versioned("1").playToServer(Text.TYPE, Text.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            // только в открытый у игрока щит: чужое окно или закрытое меню пакет не достаёт
            if (ctx.player() instanceof ServerPlayer sp && sp.containerMenu instanceof PanelMenu m && m.containerId == p.containerId())
                m.panelText(sp, p.field(), p.text());
        }));
    }
}

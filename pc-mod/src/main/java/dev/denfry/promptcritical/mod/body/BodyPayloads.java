package dev.denfry.promptcritical.mod.body;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.body.BodyProfile;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Пакеты выбора тела: сервер просит выбрать (с текущим профилем), клиент присылает выбор. */
public final class BodyPayloads {
    private BodyPayloads() {
    }

    /** Сервер → клиент: открыть экран выбора; {@code current} — профиль, которым заполнить поля. */
    public record Request(BodyProfile current) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(PromptCritical.id("body_request"));
        public static final StreamCodec<ByteBuf, Request> STREAM_CODEC =
                PlayerBody.PROFILE_STREAM.map(Request::new, Request::current);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Клиент → сервер: выбранный профиль. Ввод недоверенный — значения прижимаются к диапазонам при чтении. */
    public record Submit(BodyProfile profile) implements CustomPacketPayload {
        public static final Type<Submit> TYPE = new Type<>(PromptCritical.id("body_submit"));
        public static final StreamCodec<ByteBuf, Submit> STREAM_CODEC =
                PlayerBody.PROFILE_STREAM.map(Submit::new, Submit::profile);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}

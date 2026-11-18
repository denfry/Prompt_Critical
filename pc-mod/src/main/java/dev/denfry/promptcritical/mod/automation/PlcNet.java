package dev.denfry.promptcritical.mod.automation;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.sim.automation.AutomationUnit;
import dev.denfry.promptcritical.sim.automation.plc.IlCompiler;
import dev.denfry.promptcritical.sim.automation.plc.IlProgram;
import dev.denfry.promptcritical.sim.automation.plc.PlcCpu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Сеть программатора ПЛК: сервер открывает окно с текстом программы и состоянием; клиент загружает программу,
 * поворачивает ключ RUN/STOP и раз в полсекунды спрашивает состояние для онлайн-индикации. Всё, что приходит от
 * клиента, проверяется: игрок рядом с блоком, блок — ПЛК, текст не длиннее {@link IlCompiler#MAX_CHARS}.
 */
public final class PlcNet {
    private PlcNet() {
    }

    /** Ошибка компиляции для окна: строка, вид (имя перечисления) и что не понравилось. */
    public record Error(int line, String kind, String token) {
    }

    /** Состояние ПЛК для окна: режим, ключ, биты входов (0…7) и выходов (8…15), аналоговые каналы, ошибки. */
    public record Status(int mode, boolean runKey, int io, float[] ai, float[] ao, int executed, List<Error> errors) {
        static Status of(PlcCpu cpu) {
            int io = 0;
            for (int i = 0; i < IlCompiler.IX; i++) if (cpu.input(i)) io |= 1 << i;
            for (int i = 0; i < IlCompiler.QX; i++) if (cpu.output(i)) io |= 1 << (8 + i);
            float[] ai = new float[IlCompiler.IW], ao = new float[IlCompiler.QW];
            for (int i = 0; i < ai.length; i++) ai[i] = (float) cpu.analogInput(i);
            for (int i = 0; i < ao.length; i++) ao[i] = (float) cpu.analogOutput(i);
            List<Error> errors = new ArrayList<>();
            for (IlProgram.IlError e : cpu.program().errors()) {
                if (errors.size() >= 16) break;
                errors.add(new Error(e.line(), e.kind().name(), e.token().length() > 40 ? e.token().substring(0, 40) : e.token()));
            }
            return new Status(cpu.mode().ordinal(), cpu.runKey(), io, ai, ao, cpu.lastExecuted(), errors);
        }

        void write(FriendlyByteBuf b) {
            b.writeVarInt(mode);
            b.writeBoolean(runKey);
            b.writeInt(io);
            for (float v : ai) b.writeFloat(v);
            for (float v : ao) b.writeFloat(v);
            b.writeVarInt(executed);
            b.writeVarInt(errors.size());
            for (Error e : errors) {
                b.writeVarInt(e.line);
                b.writeUtf(e.kind, 64);
                b.writeUtf(e.token, 64);
            }
        }

        static Status read(FriendlyByteBuf b) {
            int mode = b.readVarInt();
            boolean key = b.readBoolean();
            int io = b.readInt();
            float[] ai = new float[IlCompiler.IW], ao = new float[IlCompiler.QW];
            for (int i = 0; i < ai.length; i++) ai[i] = b.readFloat();
            for (int i = 0; i < ao.length; i++) ao[i] = b.readFloat();
            int executed = b.readVarInt();
            int n = Math.min(16, b.readVarInt());
            List<Error> errors = new ArrayList<>(n);
            for (int i = 0; i < n; i++) errors.add(new Error(b.readVarInt(), b.readUtf(64), b.readUtf(64)));
            return new Status(mode, key, io, ai, ao, executed, errors);
        }
    }

    /** Сервер → клиент: открыть программатор. */
    public record Open(BlockPos pos, String source, Status status) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(PromptCritical.id("plc_open"));
        public static final StreamCodec<FriendlyByteBuf, Open> STREAM_CODEC = StreamCodec.of(
                (b, p) -> {
                    b.writeBlockPos(p.pos);
                    b.writeUtf(p.source, IlCompiler.MAX_CHARS);
                    p.status.write(b);
                },
                b -> new Open(b.readBlockPos(), b.readUtf(IlCompiler.MAX_CHARS), Status.read(b)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Сервер → клиент: состояние для открытого окна. */
    public record State(BlockPos pos, Status status) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(PromptCritical.id("plc_state"));
        public static final StreamCodec<FriendlyByteBuf, State> STREAM_CODEC = StreamCodec.of(
                (b, p) -> {
                    b.writeBlockPos(p.pos);
                    p.status.write(b);
                },
                b -> new State(b.readBlockPos(), Status.read(b)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Клиент → сервер: загрузить программу; {@code key} — положение ключа RUN/STOP; {@code upload} — с текстом. */
    public record Command(BlockPos pos, boolean upload, String source, boolean key) implements CustomPacketPayload {
        public static final Type<Command> TYPE = new Type<>(PromptCritical.id("plc_command"));
        public static final StreamCodec<FriendlyByteBuf, Command> STREAM_CODEC = StreamCodec.of(
                (b, p) -> {
                    b.writeBlockPos(p.pos);
                    b.writeBoolean(p.upload);
                    b.writeUtf(p.source, IlCompiler.MAX_CHARS);
                    b.writeBoolean(p.key);
                },
                b -> new Command(b.readBlockPos(), b.readBoolean(), b.readUtf(IlCompiler.MAX_CHARS), b.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Клиент → сервер: дай состояние (окно открыто). */
    public record Poll(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Poll> TYPE = new Type<>(PromptCritical.id("plc_poll"));
        public static final StreamCodec<FriendlyByteBuf, Poll> STREAM_CODEC = StreamCodec.of((b, p) -> b.writeBlockPos(p.pos),
                b -> new Poll(b.readBlockPos()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar(PromptCritical.MOD_ID).versioned("1");
        r.playToClient(Open.TYPE, Open.STREAM_CODEC, (p, ctx) -> {
            if (FMLEnvironment.dist.isClient()) ctx.enqueueWork(() -> dev.denfry.promptcritical.mod.automation.client.PlcScreen.open(p));
        });
        r.playToClient(State.TYPE, State.STREAM_CODEC, (p, ctx) -> {
            if (FMLEnvironment.dist.isClient()) ctx.enqueueWork(() -> dev.denfry.promptcritical.mod.automation.client.PlcScreen.update(p));
        });
        r.playToServer(Command.TYPE, Command.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> command(p, ctx)));
        r.playToServer(Poll.TYPE, Poll.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            AutomationUnit u = unitFor(ctx, p.pos());
            if (u != null) PacketDistributor.sendToPlayer((ServerPlayer) ctx.player(), new State(p.pos(), Status.of(u.plc())));
        }));
    }

    public static void open(ServerPlayer player, BlockPos pos, AutomationUnit u) {
        PacketDistributor.sendToPlayer(player, new Open(pos, u.plc().program().source(), Status.of(u.plc())));
    }

    private static void command(Command c, IPayloadContext ctx) {
        AutomationUnit u = unitFor(ctx, c.pos());
        if (u == null) return;
        if (c.upload()) u.plc().load(c.source());
        u.plc().setRunKey(c.key());
        PacketDistributor.sendToPlayer((ServerPlayer) ctx.player(), new State(c.pos(), Status.of(u.plc())));
    }

    /** ПЛК в {@code pos}, если игрок стоит рядом; иначе {@code null}. */
    private static AutomationUnit unitFor(IPayloadContext ctx, BlockPos pos) {
        if (!(ctx.player() instanceof ServerPlayer p)) return null;
        if (p.distanceToSqr(pos.getCenter()) > PanelHost.REACH_SQ) return null;
        if (!(p.level().getBlockEntity(pos) instanceof PlcBlockEntity be)) return null;
        AutomationUnit u = be.unit();
        return u != null && u.plc() != null ? u : null;
    }
}

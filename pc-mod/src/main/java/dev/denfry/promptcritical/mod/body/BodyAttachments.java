package dev.denfry.promptcritical.mod.body;

import dev.denfry.promptcritical.mod.PromptCritical;
import java.util.function.Supplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Регистрация тела игрока: данные (переживают смерть), пакеты выбора, события входа и возрождения. */
public final class BodyAttachments {
    public static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, PromptCritical.MOD_ID);

    public static final Supplier<AttachmentType<PlayerBody>> BODY = TYPES.register("body",
            () -> AttachmentType.builder(() -> PlayerBody.UNSET).serialize(PlayerBody.CODEC).copyOnDeath().build());

    private BodyAttachments() {
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        modBus.addListener(BodyAttachments::payloads);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p) BodyService.onLogin(p);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> BodyService.apply(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> BodyService.apply(e.getEntity()));
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar(PromptCritical.MOD_ID).versioned("1").optional();
        r.playToClient(BodyPayloads.Request.TYPE, BodyPayloads.Request.STREAM_CODEC, (payload, ctx) -> {
            if (FMLEnvironment.dist.isClient())
                ctx.enqueueWork(() -> dev.denfry.promptcritical.mod.body.client.BodyClient.open(payload.current()));
        });
        r.playToServer(BodyPayloads.Submit.TYPE, BodyPayloads.Submit.STREAM_CODEC, (payload, ctx) ->
                ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer p) BodyService.submit(p, payload.profile());
                }));
    }
}

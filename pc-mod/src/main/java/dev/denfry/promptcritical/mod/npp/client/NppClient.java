package dev.denfry.promptcritical.mod.npp.client;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.foundry.client.FoundryMachineRenderer;
import dev.denfry.promptcritical.mod.foundry.client.FoundryMachineRenderer.Motion;
import dev.denfry.promptcritical.mod.npp.NppBlockEntities;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Клиент АЭС: подвижные части машин на валу Create и перегрузочная машина CANDU. Только на клиенте. */
public final class NppClient {
    /** Муфта ГЦН (art/blockbench/mechanics/primary_pump.author.js), ось z через x 8, y 7. */
    private static final PartialModel PUMP_COUPLING = PartialModel.of(PromptCritical.id("block/primary_pump/partial/coupling"));
    static final PartialModel CANDU_BRIDGE = partial("candu_reactor/partial/bridge");
    static final PartialModel CANDU_HEAD = partial("candu_reactor/partial/head");

    private NppClient() {
    }

    private static PartialModel partial(String path) {
        return PartialModel.of(PromptCritical.id("block/" + path));
    }

    /** Подвижные части цельных моделей ВВЭР реального масштаба. */
    static final PartialModel VVER_RODS = partial("vver_reactor/partial/rods"), VVER_PUMP_COUPLING = partial("vver_coolant_pump/partial/coupling");

    static {
        // цельные модели ВВЭР реального масштаба: partial-модели должны появиться до запекания
        for (String id : new String[] {"vver_reactor", "vver_steam_generator", "vver_coolant_pump", "vver_pressurizer", "bwr240_reactor"})
            dev.denfry.promptcritical.mod.client.whole.WholeModels.register(id);
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(NppClient::registerRenderers);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // ГЦН: у старой серии — муфта, у ГЦН-195 реального масштаба — цельная модель
        var coupling = new FoundryMachineRenderer<dev.denfry.promptcritical.mod.npp.PrimaryPumpBlockEntity>(PUMP_COUPLING, Motion.SPIN_Z, 8, 7, 1);
        var pumpWhole = new dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer<dev.denfry.promptcritical.mod.npp.PrimaryPumpBlockEntity>() {
            // муфта ГЦН-195 (art/blockbench/core/vver/vver_coolant_pump.author.js): на 5 блоков выше контроллера,
            // вращается вокруг вертикали через центр клетки со скоростью вала Create
            @Override
            protected void parts(dev.denfry.promptcritical.mod.npp.PrimaryPumpBlockEntity be, float pt, com.mojang.blaze3d.vertex.PoseStack ms,
                    com.mojang.blaze3d.vertex.VertexConsumer vc, int light) {
                float deg = net.createmod.catnip.animation.AnimationTickHolder.getRenderTime(be.getLevel()) * be.getSpeed() * 3f / 10 % 360;
                part(VVER_PUMP_COUPLING, be, be.wholeModelFacing(), 0, 5, 0).translate(0.5, 0, 0.5).rotateYDegrees(deg).translate(-0.5, 0, -0.5)
                        .light(light).renderInto(ms, vc);
            }
        };
        event.registerBlockEntityRenderer(NppBlockEntities.PRIMARY_PUMP.get(), c -> new net.minecraft.client.renderer.blockentity.BlockEntityRenderer<>() {
            @Override
            public void render(dev.denfry.promptcritical.mod.npp.PrimaryPumpBlockEntity be, float pt, com.mojang.blaze3d.vertex.PoseStack ms,
                    net.minecraft.client.renderer.MultiBufferSource buf, int light, int overlay) {
                if (be.wholeModelId() != null) pumpWhole.render(be, pt, ms, buf, light, overlay);
                else coupling.render(be, pt, ms, buf, light, overlay);
            }

            @Override
            public boolean shouldRenderOffScreen(dev.denfry.promptcritical.mod.npp.PrimaryPumpBlockEntity be) {
                return be.wholeModelId() != null;
            }

            @Override
            public int getViewDistance() {
                return dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer.VIEW_DISTANCE;
            }

            @Override
            public net.minecraft.world.phys.AABB getRenderBoundingBox(dev.denfry.promptcritical.mod.npp.PrimaryPumpBlockEntity be) {
                return be.wholeModelId() != null ? pumpWhole.getRenderBoundingBox(be) : new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(1);
            }
        });
        event.registerBlockEntityRenderer(NppBlockEntities.PWR_REACTOR.get(), c -> new dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer<dev.denfry.promptcritical.mod.npp.PwrReactorBlockEntity>() {
            // указатели положения стержней над приводами (art/blockbench/core/vver/vver_reactor.author.js): на 17 блоков
            // выше контроллера, выдвигаются вверх до 8 пикселей по выведенной доле регулирующей группы
            @Override
            protected void parts(dev.denfry.promptcritical.mod.npp.PwrReactorBlockEntity be, float pt, com.mojang.blaze3d.vertex.PoseStack ms,
                    com.mojang.blaze3d.vertex.VertexConsumer vc, int light) {
                part(VVER_RODS, be, be.wholeModelFacing(), 0, 17 + be.rodsOut() * 8 / 16.0, 0).light(light).renderInto(ms, vc);
            }
        });
        event.registerBlockEntityRenderer(NppBlockEntities.BWR_REACTOR.get(), c -> new dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer<>());
        event.registerBlockEntityRenderer(NppBlockEntities.STEAM_GENERATOR.get(), c -> new dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer<>());
        event.registerBlockEntityRenderer(NppBlockEntities.PRESSURIZER.get(), c -> new dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer<>());
        event.registerBlockEntityRenderer(dev.denfry.promptcritical.mod.site.SiteRegistry.SITE_BE.get(), c -> new dev.denfry.promptcritical.mod.site.SiteGhostRenderer());
        event.registerBlockEntityRenderer(NppBlockEntities.CANDU_REACTOR.get(), c -> new CanduRenderer());
    }
}

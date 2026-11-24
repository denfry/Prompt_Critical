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
    static final PartialModel CANDU6_HEAD = partial("candu6_reactor/partial/head"), CANDU6_BEAM = partial("candu6_reactor/partial/beam");
    static final PartialModel VVER_RODS = partial("vver_reactor/partial/rods"), VVER_PUMP_COUPLING = partial("vver_coolant_pump/partial/coupling");
    /** ВВЭР-70 (art/blockbench/core/vver70/*.author.js): указатели стержней, диск тахометра ГЦН, поплавки уровня, стрелка азота. */
    static final PartialModel V70_RODS = partial("vver70_reactor_real/partial/rods"),
            V70_PUMP_DISC = partial("vver70_coolant_pump_real/partial/speed_disc"),
            V70_SG_FLOAT = partial("vver70_steam_generator_real/partial/level_float"),
            V70_PZR_FLOAT = partial("vver70_pressurizer_real/partial/level_float"),
            V70_PZR_NEEDLE = partial("vver70_pressurizer_real/partial/gas_gauge_needle");

    static {
        // цельные модели ВВЭР реального масштаба: partial-модели должны появиться до запекания
        for (String id : new String[] {"vver_reactor", "vver_steam_generator", "vver_coolant_pump", "vver_pressurizer", "bwr240_reactor",
                "candu6_reactor", "bn600_reactor", "am1_reactor", "vver70_reactor_real", "vver70_steam_generator_real",
                "vver70_coolant_pump_real", "vver70_pressurizer_real", "vver70_fuel_pool_real", "vver70_refuelling_machine_real"})
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
                if ("vver70_coolant_pump_real".equals(be.wholeModelId())) {
                    // диск тахометра над экранированным двигателем: хранится на 48 px ниже, вращается вокруг (8, 8)
                    part(V70_PUMP_DISC, be, be.wholeModelFacing(), 0, 3, 0).translate(0.5, 0, 0.5).rotateYDegrees(deg).translate(-0.5, 0, -0.5)
                            .light(light).renderInto(ms, vc);
                    return;
                }
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
                boolean v70 = "vver70_reactor_real".equals(be.wholeModelId());
                part(v70 ? V70_RODS : VVER_RODS, be, be.wholeModelFacing(), 0, 17 + be.rodsOut() * 8 / 16.0, 0).light(light).renderInto(ms, vc);
            }
        });
        event.registerBlockEntityRenderer(NppBlockEntities.POWER_REACTOR.get(), c -> new dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer<>());
        event.registerBlockEntityRenderer(NppBlockEntities.FUEL_POOL.get(), c -> new Vver70FuelHandlingRenderers.Pool());
        event.registerBlockEntityRenderer(NppBlockEntities.REFUELLING_MACHINE.get(), c -> new Vver70FuelHandlingRenderers.Machine());
        event.registerBlockEntityRenderer(NppBlockEntities.SFR_REACTOR.get(), c -> new dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer<>());
        event.registerBlockEntityRenderer(NppBlockEntities.BWR_REACTOR.get(), c -> new dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer<>());
        event.registerBlockEntityRenderer(NppBlockEntities.STEAM_GENERATOR.get(), c -> new dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer<dev.denfry.promptcritical.mod.npp.SteamGeneratorBlockEntity>() {
            // ПГ ВВЭР-70: поплавок указателя уровня второго контура на нормальном уровне (середина хода 0..40 px); частичная
            // модель хранится на 48 px ниже — сдвиг возвращается здесь
            @Override
            protected void parts(dev.denfry.promptcritical.mod.npp.SteamGeneratorBlockEntity be, float pt, com.mojang.blaze3d.vertex.PoseStack ms,
                    com.mojang.blaze3d.vertex.VertexConsumer vc, int light) {
                if ("vver70_steam_generator_real".equals(be.wholeModelId()))
                    part(V70_SG_FLOAT, be, be.wholeModelFacing(), 0, (48 + 20) / 16.0, 0).light(light).renderInto(ms, vc);
            }
        });
        event.registerBlockEntityRenderer(NppBlockEntities.PRESSURIZER.get(), c -> new dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer<dev.denfry.promptcritical.mod.npp.PressurizerBlockEntity>() {
            // газовый КД ВВЭР-70: поплавок по уровню (ход 0..80 px), стрелка манометра азота 0..270° на 0..15 МПа
            // вокруг z в (26, 40, −13,4), хранится на 24 px ниже
            @Override
            protected void parts(dev.denfry.promptcritical.mod.npp.PressurizerBlockEntity be, float pt, com.mojang.blaze3d.vertex.PoseStack ms,
                    com.mojang.blaze3d.vertex.VertexConsumer vc, int light) {
                if (!"vver70_pressurizer_real".equals(be.wholeModelId())) return;
                var f = be.wholeModelFacing();
                part(V70_PZR_FLOAT, be, f, 0, be.level01() * 80 / 16.0, 0).light(light).renderInto(ms, vc);
                float deg = Math.min(270f, be.pressureMpa() / 15f * 270f);
                part(V70_PZR_NEEDLE, be, f, 0, 24 / 16.0, 0).translate(26 / 16.0, 16 / 16.0, -13.4 / 16).rotateZDegrees(-deg)
                        .translate(-26 / 16.0, -16 / 16.0, 13.4 / 16).light(light).renderInto(ms, vc);
            }
        });
        event.registerBlockEntityRenderer(dev.denfry.promptcritical.mod.site.SiteRegistry.SITE_BE.get(), c -> new dev.denfry.promptcritical.mod.site.SiteGhostRenderer());
        // CANDU: у старой серии — машина на торце 3×6×3, у CANDU 6 реального масштаба — цельная модель с мостом и машиной
        var canduOld = new CanduRenderer();
        var canduWhole = new dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer<dev.denfry.promptcritical.mod.npp.CanduReactorBlockEntity>() {
            @Override
            protected void parts(dev.denfry.promptcritical.mod.npp.CanduReactorBlockEntity be, float pt, com.mojang.blaze3d.vertex.PoseStack ms,
                    com.mojang.blaze3d.vertex.VertexConsumer vc, int light) {
                double t = be.getLevel().getGameTime() - be.refuelTick() + pt;
                double[] p = dev.denfry.promptcritical.mod.npp.CanduFuelling.offsetReal(be.refuelChannel(), t);
                var f = be.wholeModelFacing();
                double y = (72 + p[1]) / 16;
                for (int k = 0; k < 3; k++) part(CANDU6_BEAM, be, f, k * 3 - 3, y, -8).light(light).renderInto(ms, vc);
                part(CANDU6_HEAD, be, f, p[0] / 16, y, (-129 + p[2]) / 16).light(light).renderInto(ms, vc);
            }
        };
        event.registerBlockEntityRenderer(NppBlockEntities.CANDU_REACTOR.get(), c -> new net.minecraft.client.renderer.blockentity.BlockEntityRenderer<>() {
            @Override
            public void render(dev.denfry.promptcritical.mod.npp.CanduReactorBlockEntity be, float pt, com.mojang.blaze3d.vertex.PoseStack ms,
                    net.minecraft.client.renderer.MultiBufferSource buf, int light, int overlay) {
                if (be.wholeModelId() != null) canduWhole.render(be, pt, ms, buf, light, overlay);
                else canduOld.render(be, pt, ms, buf, light, overlay);
            }

            @Override
            public boolean shouldRenderOffScreen(dev.denfry.promptcritical.mod.npp.CanduReactorBlockEntity be) {
                return be.wholeModelId() != null;
            }

            @Override
            public int getViewDistance() {
                return dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer.VIEW_DISTANCE;
            }

            @Override
            public net.minecraft.world.phys.AABB getRenderBoundingBox(dev.denfry.promptcritical.mod.npp.CanduReactorBlockEntity be) {
                return be.wholeModelId() != null ? canduWhole.getRenderBoundingBox(be) : canduOld.getRenderBoundingBox(be);
            }
        });
    }
}

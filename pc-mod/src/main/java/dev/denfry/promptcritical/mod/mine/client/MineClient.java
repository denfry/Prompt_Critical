package dev.denfry.promptcritical.mod.mine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.foundry.client.FoundryMachineRenderer;
import dev.denfry.promptcritical.mod.foundry.client.FoundryMachineRenderer.Motion;
import dev.denfry.promptcritical.mod.mine.MainMineFanBlock;
import dev.denfry.promptcritical.mod.mine.MainMineFanBlockEntity;
import dev.denfry.promptcritical.mod.mine.MineBlockEntities;
import dev.denfry.promptcritical.mod.mine.MineDrainagePumpBlockEntity;
import dev.denfry.promptcritical.mod.mine.LongwallShearerBlockEntity;
import dev.denfry.promptcritical.mod.mine.MineHoisting;
import dev.denfry.promptcritical.mod.mine.PoweredRoofSupportBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Клиент шахты: крыльчатка вентилятора, роторы насосов, подъём (барабан, шкив, канат) и его панель. Только на клиенте. */
public final class MineClient {
    private static final PartialModel IMPELLER = partial("main_mine_fan/partial/impeller");
    private static final PartialModel ROTOR = partial("degasification_pump/partial/rotor");
    private static final PartialModel PUMP_SHAFT = partial("mine_drainage_pump/partial/shaft");
    private static final PartialModel DRUM_A = partial("longwall_shearer/partial/drum_a");
    private static final PartialModel DRUM_B = partial("longwall_shearer/partial/drum_b");
    private static final PartialModel REAL_ARM = partial("longwall_shearer_real/partial/arm");
    private static final PartialModel REAL_DRUM = partial("longwall_shearer_real/partial/drum");
    private static final PartialModel SUPPORT_CANOPY_N = partial("powered_roof_support_real/partial/canopy_n");
    private static final PartialModel SUPPORT_CANOPY_S = partial("powered_roof_support_real/partial/canopy_s");
    private static final PartialModel SUPPORT_RAM = partial("powered_roof_support_real/partial/ram");
    private static final PartialModel LEGS = partial("powered_roof_support/partial/legs");
    /** Барабан подъёма и шкив копра: частичные модели Flywheel объявляются до загрузки моделей, здесь, а не в рендерере. */
    static final PartialModel HOIST_DRUM = partial("mine_hoist/partial/drum");
    static final PartialModel HOIST_CURSOR = partial("mine_hoist/partial/cursor");
    static final PartialModel SHEAVE = partial("headframe/partial/sheave");
    static final PartialModel SHEAVE_STEEL = partial("headframe_steel/partial/sheave");
    static final PartialModel WINDER_CROSSHEAD = partial("winding_engine/partial/crosshead");
    static final PartialModel WINDER_CONROD = partial("winding_engine/partial/conrod");
    static final PartialModel WINDER_CURSOR = partial("winding_engine/partial/cursor");

    static {
        // стальной копёр реального масштаба — цельная модель; части — до запекания
        dev.denfry.promptcritical.mod.client.whole.WholeModels.register("headframe_steel");
        // паровая подъёмная машина: станины и машины — одна цельная модель, барабан с валом — вторая, вращается
        dev.denfry.promptcritical.mod.client.whole.WholeModels.register("winding_engine");
        dev.denfry.promptcritical.mod.client.whole.WholeModels.register("winding_engine_drum");
        dev.denfry.promptcritical.mod.client.whole.WholeModels.register("guibal_fan");
        GuibalFanRenderer.init();
        dev.denfry.promptcritical.mod.client.whole.WholeModels.register("mine_drainage_pump_geared");
        dev.denfry.promptcritical.mod.client.whole.WholeModels.register("longwall_shearer_real");
        dev.denfry.promptcritical.mod.client.whole.WholeModels.register("powered_roof_support_real");
        GearedPumpRenderer.init();
    }
    /** Шнеки в контрапции вращаются, град/тик (≈ 40 об/мин). */
    private static final float DRUM_DEG_PER_TICK = 12f;

    private MineClient() {
    }

    private static PartialModel partial(String path) {
        return PartialModel.of(PromptCritical.id("block/" + path));
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(MineClient::registerRenderers);
        modBus.addListener(MineClient::registerScreens);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(MineBlockEntities.MAIN_MINE_FAN.get(), c -> new FanRenderer());
        event.registerBlockEntityRenderer(MineBlockEntities.DEGASIFICATION_PUMP.get(),
                c -> new FoundryMachineRenderer<>(ROTOR, Motion.SPIN_Z, 8, 8, 1));
        // насос: ЦНС — вал на своём рендере, трёхплунжерный реального масштаба — цельная модель с механизмом
        event.registerBlockEntityRenderer(MineBlockEntities.MINE_DRAINAGE_PUMP.get(), c -> new BlockEntityRenderer<MineDrainagePumpBlockEntity>() {
            private final FoundryMachineRenderer<MineDrainagePumpBlockEntity> old = new FoundryMachineRenderer<>(PUMP_SHAFT, Motion.SPIN_X, 8, 8, 1);
            private final GearedPumpRenderer geared = new GearedPumpRenderer();

            @Override
            public void render(MineDrainagePumpBlockEntity be, float pt, PoseStack ms, MultiBufferSource buf, int light, int overlay) {
                if (be.real()) geared.render(be, pt, ms, buf, light, overlay);
                else old.render(be, pt, ms, buf, light, overlay);
            }

            @Override
            public boolean shouldRenderOffScreen(MineDrainagePumpBlockEntity be) {
                return be.real() || old.shouldRenderOffScreen(be);
            }

            @Override
            public int getViewDistance() {
                return dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer.VIEW_DISTANCE;
            }

            @Override
            public net.minecraft.world.phys.AABB getRenderBoundingBox(MineDrainagePumpBlockEntity be) {
                return be.real() ? geared.getRenderBoundingBox(be) : old.getRenderBoundingBox(be);
            }
        });
        event.registerBlockEntityRenderer(MineBlockEntities.MINE_HOIST.get(), c -> new HoistRenderer());
        event.registerBlockEntityRenderer(MineBlockEntities.HEADFRAME_STEEL.get(),
                c -> new dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer<>());
        event.registerBlockEntityRenderer(MineBlockEntities.LONGWALL_SHEARER.get(), c -> new ShearerRenderer());
        event.registerBlockEntityRenderer(MineBlockEntities.POWERED_ROOF_SUPPORT.get(), c -> new SupportRenderer());
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(MineHoisting.HOIST_MENU.get(), HoistScreen::new);
    }

    /** Крыльчатка на оси фасада, pivot [8, 8, 8] центрального блока; скорость — вала в клетке-входе × редуктор. */
    private static final class FanRenderer implements BlockEntityRenderer<MainMineFanBlockEntity> {
        /** Guibal реального масштаба: цельная модель и машина на кривошипе. */
        private final GuibalFanRenderer guibal = new GuibalFanRenderer();

        @Override
        public void render(MainMineFanBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
            if (be.wholeModelId() != null) {
                guibal.render(be, partialTicks, ms, buffers, light, overlay);
                return;
            }
            var state = be.getBlockState();
            if (!state.is(PcBlocks.MAIN_MINE_FAN.get())) return;
            Direction facing = PcBlocks.MAIN_MINE_FAN.get().facing(state);
            float speed = be.getLevel() != null
                    && be.getLevel().getBlockEntity(MainMineFanBlock.portPos(be.getBlockPos(), facing)) instanceof KineticBlockEntity k
                    ? k.getSpeed() : 0;
            float time = AnimationTickHolder.getRenderTime(be.getLevel());
            float angle = (float) ((time * speed * MainMineFanBlockEntity.GEAR * 3f / 10f) % 360f);
            SuperByteBuffer buf = CachedBuffers.partial(IMPELLER, state);
            buf.center().rotateYDegrees(-(facing.toYRot() + 180)).uncenter()
                    .center().rotateZDegrees(angle).uncenter()
                    .light(light).renderInto(ms, buffers.getBuffer(RenderType.cutoutMipped()));
        }

        @Override
        public boolean shouldRenderOffScreen(MainMineFanBlockEntity be) {
            return true;
        }

        @Override
        public int getViewDistance() {
            return dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer.VIEW_DISTANCE;
        }

        @Override
        public net.minecraft.world.phys.AABB getRenderBoundingBox(MainMineFanBlockEntity be) {
            return be.wholeModelId() != null ? guibal.getRenderBoundingBox(be) : new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(1);
        }
    }

    /**
     * Шнеки комбайна: ось вдоль лавы (X модели) через y = 16, z = 8 px. На месте стоят, в контрапции Create
     * (виртуальный мир рендера) крутятся — комбайн едет и режет. У комбайна реального масштаба — цельная модель,
     * поворотные рукояти и шнеки ⌀1,6 м (art/blockbench/mining/longwall_shearer_real.author.js): рукоять — partial
     * с осью шарнира в (28, 8, 8) px, западная — в (−44, 13, 16) px модели, восточная — зеркально, повёрнута на 180°;
     * шнек вращается вокруг z через (8, 8) px partial, его забойный торец — на z 0 модели.
     */
    private static final class ShearerRenderer extends dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer<LongwallShearerBlockEntity> {
        /** Ось шарнира рукояти от западной грани контроллера, её длина до оси шнека, px. */
        private static final float PIV_X = 44, PIV_Y = 13, PIV_Z = 16, ARM = 38;
        /** Подъём западной (режет кровлю) и восточной (зачищает почву) рукоятей, град. */
        private static final float WEST_PITCH = 9.4f, EAST_PITCH = 0f;

        @Override
        public void render(LongwallShearerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
            if (be.wholeModelId() != null) {
                super.render(be, partialTicks, ms, buffers, light, overlay);
                return;
            }
            BlockState state = be.getBlockState();
            if (!state.is(PcBlocks.LONGWALL_SHEARER.get())) return;
            Direction facing = PcBlocks.LONGWALL_SHEARER.get().facing(state);
            float angle = be.getLevel() instanceof VirtualRenderWorld
                    ? (AnimationTickHolder.getRenderTime(be.getLevel()) * DRUM_DEG_PER_TICK) % 360f : 0f;
            for (PartialModel drum : new PartialModel[] {DRUM_A, DRUM_B}) {
                SuperByteBuffer buf = CachedBuffers.partial(drum, state);
                buf.center().rotateYDegrees(-(facing.toYRot() + 180)).uncenter()
                        .translate(0, 1, 0.5).rotateXDegrees(angle).translate(0, -1, -0.5)
                        .light(light).renderInto(ms, buffers.getBuffer(RenderType.cutoutMipped()));
            }
        }

        @Override
        protected void parts(LongwallShearerBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
            Direction f = be.wholeModelFacing();
            float spin = be.getLevel() instanceof VirtualRenderWorld
                    ? (AnimationTickHolder.getRenderTime(be.getLevel()) * DRUM_DEG_PER_TICK) % 360f : 0f;
            for (int side = -1; side <= 1; side += 2) {
                float pitch = side < 0 ? WEST_PITCH : EAST_PITCH;
                double a = Math.toRadians(pitch);
                // рукоять: ось шарнира partial (28, 8, 8) — на шарнир модели; восточная развёрнута на 180° вокруг y
                part(REAL_ARM, be, f, (side * PIV_X - 28) / 16, (PIV_Y - 8) / 16, (PIV_Z - 8) / 16)
                        .translate(28 / 16f, 0.5, 0.5).rotateZDegrees(side < 0 ? -pitch : pitch).rotateYDegrees(side < 0 ? 0 : 180)
                        .translate(-28 / 16f, -0.5, -0.5).light(light).renderInto(ms, vc);
                // шнек: центр — на конце рукояти, торец к забою (z partial 1 → z модели 0)
                double cx = side * (PIV_X + ARM * Math.cos(a)), cy = PIV_Y + ARM * Math.sin(a);
                part(REAL_DRUM, be, f, (cx - 8) / 16, (cy - 8) / 16, -1 / 16.0)
                        .translate(0.5, 0.5, 0).rotateZDegrees(side * spin).translate(-0.5, -0.5, 0).light(light).renderInto(ms, vc);
            }
        }
    }

    /**
     * Гидростойки секции мехкрепи: распёрта — штоки упираются в перекрытие, разгружена — опущены на ход. У секции
     * реального масштаба — цельная модель, перекрытие из двух partial (задняя половина нарисована на 2 блока раньше
     * своего места) опускается на ход стоек, домкрат передвижки с балкой стоит на месте.
     */
    private static final class SupportRenderer extends dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer<PoweredRoofSupportBlockEntity> {
        @Override
        public void render(PoweredRoofSupportBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
            if (be.wholeModelId() != null) {
                super.render(be, partialTicks, ms, buffers, light, overlay);
                return;
            }
            BlockState state = be.getBlockState();
            if (!state.is(PcBlocks.POWERED_ROOF_SUPPORT.get())) return;
            Direction facing = PcBlocks.POWERED_ROOF_SUPPORT.get().facing(state);
            float drop = (1 - be.legs(partialTicks)) * PoweredRoofSupportBlockEntity.LEG_TRAVEL_PX / 16f;
            CachedBuffers.partial(LEGS, state)
                    .center().rotateYDegrees(-(facing.toYRot() + 180)).uncenter()
                    .translate(0, -drop, 0)
                    .light(light).renderInto(ms, buffers.getBuffer(RenderType.cutoutMipped()));
        }

        @Override
        protected void parts(PoweredRoofSupportBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
            Direction f = be.wholeModelFacing();
            float drop = (1 - be.legs(pt)) * PoweredRoofSupportBlockEntity.LEG_TRAVEL_PX / 16f;
            part(SUPPORT_CANOPY_N, be, f, 0, -drop, 0).light(light).renderInto(ms, vc);
            part(SUPPORT_CANOPY_S, be, f, 0, -drop, 2).light(light).renderInto(ms, vc);
            part(SUPPORT_RAM, be, f, 0, 0, 0).light(light).renderInto(ms, vc);
        }
    }
}

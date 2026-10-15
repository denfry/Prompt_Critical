package dev.denfry.promptcritical.mod.mine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.foundry.client.FoundryMachineRenderer;
import dev.denfry.promptcritical.mod.foundry.client.FoundryMachineRenderer.Motion;
import dev.denfry.promptcritical.mod.mine.MainMineFanBlock;
import dev.denfry.promptcritical.mod.mine.MainMineFanBlockEntity;
import dev.denfry.promptcritical.mod.mine.MineBlockEntities;
import dev.denfry.promptcritical.mod.mine.MineHoisting;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Клиент шахты: крыльчатка вентилятора, роторы насосов, подъём (барабан, шкив, канат) и его панель. Только на клиенте. */
public final class MineClient {
    private static final PartialModel IMPELLER = partial("main_mine_fan/partial/impeller");
    private static final PartialModel ROTOR = partial("degasification_pump/partial/rotor");
    private static final PartialModel PUMP_SHAFT = partial("mine_drainage_pump/partial/shaft");

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
        event.registerBlockEntityRenderer(MineBlockEntities.MINE_DRAINAGE_PUMP.get(),
                c -> new FoundryMachineRenderer<>(PUMP_SHAFT, Motion.SPIN_X, 8, 8, 1));
        event.registerBlockEntityRenderer(MineBlockEntities.MINE_HOIST.get(), c -> new HoistRenderer());
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(MineHoisting.HOIST_MENU.get(), HoistScreen::new);
    }

    /** Крыльчатка на оси фасада, pivot [8, 8, 8] центрального блока; скорость — вала в клетке-входе × редуктор. */
    private static final class FanRenderer implements BlockEntityRenderer<MainMineFanBlockEntity> {
        @Override
        public void render(MainMineFanBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
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
    }
}

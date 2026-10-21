package dev.denfry.promptcritical.mod.automation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.automation.AutomationBlockEntities;
import dev.denfry.promptcritical.mod.automation.AutomationBlockEntity;
import dev.denfry.promptcritical.mod.automation.RelayRackBlockEntity;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.sim.automation.AutomationKind;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Клиент автоматики: провода и клеммы ({@link WireRenderer}), подвижные части — стрелка уставки реле времени, щётки
 * искателя (1/25 оборота на шаг), барабан командоаппарата, ключ и рукоятка переключателя, дверь стойки.
 */
public final class AutomationClient {
    static final PartialModel DIAL = partial("time_relay/partial/dial");
    static final PartialModel WIPER = partial("stepping_switch/partial/wiper");
    static final PartialModel DRUM = partial("cam_controller/partial/drum");
    static final PartialModel KEY = partial("key_switch/partial/key");
    static final PartialModel KNOB = partial("selector_switch/partial/knob");
    static final PartialModel DOOR = partial("relay_rack/partial/door");

    private AutomationClient() {
    }

    private static PartialModel partial(String path) {
        return PartialModel.of(PromptCritical.id("block/" + path));
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(AutomationClient::registerRenderers);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (var type : AutomationBlockEntities.wiredTypes()) event.registerBlockEntityRenderer(type.get(), c -> new WireRenderer<>(new PartRenderer()));
        event.registerBlockEntityRenderer(AutomationBlockEntities.RELAY_POWER_SUPPLY.get(), c -> new WireRenderer<>(null));
        event.registerBlockEntityRenderer(AutomationBlockEntities.RELAY_RACK.get(), c -> new RackRenderer());
    }

    /** Подвижная часть блока: модель, ось и центр поворота (пиксели модели). */
    private record Part(PartialModel model, Direction.Axis axis, float px, float py, float pz, float offset) {
    }

    @Nullable
    private static Part part(AutomationKind kind) {
        return switch (kind) {
            case TIME_RELAY -> new Part(DIAL, Direction.Axis.Z, 8, 8, 8, -135);
            case STEPPING_SWITCH -> new Part(WIPER, Direction.Axis.Y, 8, 8, 8, 0);
            case CAM_CONTROLLER -> new Part(DRUM, Direction.Axis.X, 8, 8, 8, 0);
            case KEY_SWITCH -> new Part(KEY, Direction.Axis.Z, 8, 8.5f, 8, 0);
            case SELECTOR_SWITCH -> new Part(KNOB, Direction.Axis.Z, 8, 8, 8, 0);
            default -> null;
        };
    }

    /** Подвижная часть по углу из BE, плавно (у барабана — по кратчайшей дуге). */
    private static final class PartRenderer implements BlockEntityRenderer<AutomationBlockEntity> {
        @Override
        public void render(AutomationBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
            Part p = part(be.kind());
            if (p == null) return;
            float target = be.angle();
            if (Float.isNaN(be.shownAngle)) be.shownAngle = target;
            float delta = ((target - be.shownAngle) % 360 + 540) % 360 - 180;
            be.shownAngle += delta * 0.25f;
            BlockState state = be.getBlockState();
            Direction facing = state.getBlock() instanceof PcBlock pc ? pc.facing(state) : Direction.NORTH;
            SuperByteBuffer buf = CachedBuffers.partial(p.model, state);
            buf.center().rotateYDegrees(-(facing.toYRot() + 180)).uncenter().translate(p.px / 16, p.py / 16, p.pz / 16);
            float a = be.shownAngle + p.offset;
            switch (p.axis) {
                case X -> buf.rotateXDegrees(a);
                case Y -> buf.rotateYDegrees(-a);
                default -> buf.rotateZDegrees(-a);
            }
            buf.translate(-p.px / 16, -p.py / 16, -p.pz / 16).light(light).renderInto(ms, buffers.getBuffer(RenderType.cutoutMipped()));
        }
    }

    /** Дверь стойки: петля у левого края фасада, открыта — 110°. */
    private static final class RackRenderer implements BlockEntityRenderer<RelayRackBlockEntity> {
        private static final float HINGE_X = 16 / 16f, HINGE_Z = 0, OPEN_DEG = 110;

        @Override
        public void render(RelayRackBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
            BlockState state = be.getBlockState();
            float target = state.getValue(BlockStateProperties.OPEN) ? OPEN_DEG : 0;
            be.doorAngle = Float.isNaN(be.doorAngle) ? target : be.doorAngle + (target - be.doorAngle) * 0.2f;
            Direction facing = state.getBlock() instanceof PcBlock pc ? pc.facing(state) : Direction.NORTH;
            CachedBuffers.partial(DOOR, state).center().rotateYDegrees(-(facing.toYRot() + 180)).uncenter()
                    .translate(HINGE_X, 0, HINGE_Z).rotateYDegrees(-be.doorAngle).translate(-HINGE_X, 0, -HINGE_Z)
                    .light(light).renderInto(ms, buffers.getBuffer(RenderType.cutoutMipped()));
        }

        @Override
        public AABB getRenderBoundingBox(RelayRackBlockEntity be) {
            return new AABB(be.getBlockPos()).inflate(1, 2, 1);
        }
    }
}

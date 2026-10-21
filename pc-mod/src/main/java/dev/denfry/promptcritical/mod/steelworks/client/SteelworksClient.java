package dev.denfry.promptcritical.mod.steelworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.steelworks.BofVesselBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.OxygenLanceBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.RollerTableBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.SteelworksBlockEntities;
import dev.denfry.promptcritical.mod.steelworks.SteelworksComponents;
import dev.denfry.promptcritical.mod.steelworks.SteelworksText;
import dev.denfry.promptcritical.sim.metallurgy.Ferroalloy;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Клиент стали и проката: наклон корпуса конвертера, ход кислородной фурмы, вал и раскат на рольганге (неподвижные
 * части — в мультипарте состояний) и подсказки заготовок, труб и ферросплавов. Только на клиенте.
 */
public final class SteelworksClient {
    private static final PartialModel VESSEL = partial("bof_vessel/partial/vessel");
    private static final PartialModel LANCE = partial("oxygen_lance/partial/lance");
    /** Ось цапф корпуса в модели, пиксели (y, z). */
    private static final float TRUNNION_Y = 15 / 16f, TRUNNION_Z = 8 / 16f;

    private SteelworksClient() {
    }

    private static PartialModel partial(String path) {
        return PartialModel.of(PromptCritical.id("block/" + path));
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(SteelworksClient::registerRenderers);
        NeoForge.EVENT_BUS.addListener(SteelworksClient::tooltip);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(SteelworksBlockEntities.BOF_VESSEL.get(), c -> new VesselRenderer());
        event.registerBlockEntityRenderer(SteelworksBlockEntities.OXYGEN_LANCE.get(), c -> new LanceRenderer());
        event.registerBlockEntityRenderer(SteelworksBlockEntities.ROLLER_TABLE.get(), c -> new RollerTableRenderer());
    }

    private static SuperByteBuffer facingPartial(PartialModel part, BlockState state) {
        SuperByteBuffer buf = CachedBuffers.partial(part, state);
        Direction facing = state.getBlock() instanceof PcBlock pc ? pc.facing(state) : Direction.NORTH;
        return buf.center().rotateYDegrees(-(facing.toYRot() + 180)).uncenter();
    }

    /** Корпус на цапфах: положительный наклон — горловиной к фасаду (завалка), отрицательный — назад (выпуск). */
    private static final class VesselRenderer implements BlockEntityRenderer<BofVesselBlockEntity> {
        @Override
        public void render(BofVesselBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
            SuperByteBuffer buf = facingPartial(VESSEL, be.getBlockState());
            buf.translate(0, TRUNNION_Y, TRUNNION_Z).rotateXDegrees((float) -be.renderTiltDeg()).translate(0, -TRUNNION_Y, -TRUNNION_Z);
            buf.light(light).renderInto(ms, buffers.getBuffer(RenderType.cutoutMipped()));
        }

        @Override
        public boolean shouldRenderOffScreen(BofVesselBlockEntity be) {
            return true;
        }
    }

    /** Фурма уходит вниз из каретки: 1 м хода — блок. */
    private static final class LanceRenderer implements BlockEntityRenderer<OxygenLanceBlockEntity> {
        @Override
        public void render(OxygenLanceBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
            SuperByteBuffer buf = facingPartial(LANCE, be.getBlockState());
            buf.translate(0, (float) -be.renderDropM(), 0);
            buf.light(light).renderInto(ms, buffers.getBuffer(RenderType.cutoutMipped()));
        }

        @Override
        public boolean shouldRenderOffScreen(OxygenLanceBlockEntity be) {
            return true;
        }
    }

    /** Вал насквозь (два полувала Create) и раскат, лежащий на роликах в своём месте вдоль оси. */
    private static final class RollerTableRenderer implements BlockEntityRenderer<RollerTableBlockEntity> {
        @Override
        public void render(RollerTableBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
            BlockState state = be.getBlockState();
            VertexConsumer vc = buffers.getBuffer(RenderType.solid());
            Direction f = state.getBlock() instanceof PcBlock pc ? pc.facing(state) : Direction.NORTH;
            for (Direction face : new Direction[] {f, f.getOpposite()}) {
                SuperByteBuffer shaft = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, face);
                KineticBlockEntityRenderer.standardKineticRotationTransform(shaft, be, light).renderInto(ms, vc);
            }
            ItemStack piece = be.piece();
            if (piece.isEmpty()) return;
            double x = be.position();
            Direction.Axis axis = be.axis();
            ms.pushPose();
            ms.translate(axis == Direction.Axis.X ? x : 0.5, 9.5 / 16, axis == Direction.Axis.Z ? x : 0.5);
            if (axis == Direction.Axis.X) ms.mulPose(Axis.YP.rotationDegrees(90));
            ms.mulPose(Axis.XP.rotationDegrees(90));
            ms.scale(0.9f, 0.9f, 0.9f);
            Minecraft.getInstance().getItemRenderer().renderStatic(piece, ItemDisplayContext.FIXED, light, overlay, ms, buffers,
                    be.getLevel(), 0);
            ms.popPose();
        }

        @Override
        public boolean shouldRenderOffScreen(RollerTableBlockEntity be) {
            return true;
        }
    }

    private static void tooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        long now = event.getEntity() == null ? 0 : event.getEntity().level().getGameTime();
        SteelworksComponents.PipeData pipe = stack.get(SteelworksComponents.PIPE_SPEC.get());
        if (pipe != null) SteelworksText.pipeTooltip(pipe, event.getToolTip());
        SteelworksComponents.SteelStock st = stack.get(SteelworksComponents.STEEL_STOCK.get());
        if (st != null) SteelworksText.stockTooltip(st, now, event.getToolTip());
        String id = stack.get(SteelworksComponents.FERROALLOY.get());
        Ferroalloy alloy = id == null ? null : SteelworksComponents.alloy(id);
        if (alloy != null) SteelworksText.ferroalloyTooltip(alloy, event.getToolTip());
    }
}

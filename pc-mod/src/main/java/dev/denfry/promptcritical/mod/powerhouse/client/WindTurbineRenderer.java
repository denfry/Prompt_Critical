package dev.denfry.promptcritical.mod.powerhouse.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.powerhouse.WindTurbineBlockEntity;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * Гондола и колесо ветроэлектростанции над оголовком башни: гондола поворачивается на курс, присланный сервером,
 * колесо — три лопасти через 120° — вращается с оборотами колеса. Модели лежат вокруг ступицы (8, 8, 8) пикселей;
 * колесо масштабируется ×{@link #ROTOR_SCALE}, гондола ×{@link #NACELLE_SCALE} (Ø колеса 9 блоков против 30 м у
 * Д-30 — мир в масштабе ≈ 1 : 3, гондола соразмерна колесу).
 */
final class WindTurbineRenderer implements BlockEntityRenderer<WindTurbineBlockEntity> {
    static final float ROTOR_SCALE = 3, NACELLE_SCALE = 1.5f;
    private final PartialModel nacelle, blade;

    WindTurbineRenderer(PartialModel nacelle, PartialModel blade) {
        this.nacelle = nacelle;
        this.blade = blade;
    }

    @Override
    public void render(WindTurbineBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        VertexConsumer vc = buffers.getBuffer(RenderType.cutoutMipped());
        float hub = (float) WindTurbineBlockEntity.HUB_ABOVE_CONTROLLER;
        BlockPos top = be.getBlockPos().above((int) Math.ceil(hub));
        int lit = be.getLevel() == null ? light : LevelRenderer.getLightColor(be.getLevel(), top);
        float yaw = -be.yawDeg();

        SuperByteBuffer n = CachedBuffers.partial(nacelle, be.getBlockState());
        n.translate(0.5f, hub, 0.5f).rotateYDegrees(yaw).scale(NACELLE_SCALE).translate(-0.5f, -0.5f, -0.5f);
        n.light(lit).renderInto(ms, vc);

        float time = AnimationTickHolder.getRenderTime(be.getLevel());
        float spin = (time * be.rotorRpm() * 0.3f) % 360f; // об/мин → градусы за тик: 360/60/20
        for (int k = 0; k < 3; k++) {
            SuperByteBuffer b = CachedBuffers.partial(blade, be.getBlockState());
            b.translate(0.5f, hub, 0.5f).rotateYDegrees(yaw).rotateZDegrees(spin + 120 * k).scale(ROTOR_SCALE)
                    .translate(-0.5f, -0.5f, -0.5f);
            b.light(lit).renderInto(ms, vc);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(WindTurbineBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(WindTurbineBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(5, 0, 5).expandTowards(0, 14, 0);
    }
}

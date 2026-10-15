package dev.denfry.promptcritical.mod.content.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.content.WallClockBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Matrix4f;

/**
 * Стрелки настенных часов: игровое время (тик 0 — 6:00), часовая 3,5 px, минутная 5 px поверх циферблата модели
 * (плоскость z = 14,4 фасадом на север).
 */
public class WallClockRenderer implements BlockEntityRenderer<WallClockBlock.WallClockBlockEntity> {
    private static final float FACE_Z = 14.35f / 16, CX = 0.5f, CY = 0.5f;

    public WallClockRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(WallClockBlock.WallClockBlockEntity be, float pt, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) return;
        long t = be.getLevel().getDayTime() % 24000;
        double hours = ((t / 1000.0) + 6) % 12;
        double minutes = (t % 1000) * 60 / 1000.0;
        var facing = be.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        int rotY = ((int) facing.toYRot() + 180) % 360;
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(PromptCritical.id("block/common/steel_dark"));
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-rotY));
        ps.translate(-0.5, -0.5, -0.5);
        hand(ps, vc, sprite, hours / 12 * 2 * Math.PI, 3.5f / 16, 0.9f / 16, FACE_Z, light);
        hand(ps, vc, sprite, minutes / 60 * 2 * Math.PI, 5.0f / 16, 0.6f / 16, FACE_Z - 0.002f, light);
        ps.popPose();
    }

    /** Стрелка от центра; угол — от 12 часов по часовой стрелке для смотрящего на циферблат с севера. */
    private static void hand(PoseStack ps, VertexConsumer vc, TextureAtlasSprite s, double angle, float len, float width, float z, int light) {
        // смотрящий с севера видит +x слева: «по часовой» — к −x
        float dx = (float) -Math.sin(angle), dy = (float) Math.cos(angle);
        float px = -dy * width / 2, py = dx * width / 2;
        Matrix4f m = ps.last().pose();
        float tx = CX + dx * len, ty = CY + dy * len;
        vertex(vc, ps, m, CX - px, CY - py, z, s.getU0(), s.getV0(), light);
        vertex(vc, ps, m, tx - px, ty - py, z, s.getU0(), s.getV1(), light);
        vertex(vc, ps, m, tx + px, ty + py, z, s.getU1(), s.getV1(), light);
        vertex(vc, ps, m, CX + px, CY + py, z, s.getU1(), s.getV0(), light);
    }

    private static void vertex(VertexConsumer vc, PoseStack ps, Matrix4f m, float x, float y, float z, float u, float v, int light) {
        vc.addVertex(m, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(ps.last(), 0, 0, -1);
    }
}

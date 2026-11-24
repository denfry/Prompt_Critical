package dev.denfry.promptcritical.mod.npp.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer;
import dev.denfry.promptcritical.mod.client.whole.WholeModels;
import dev.denfry.promptcritical.mod.npp.RefuellingMachineBlockEntity;
import dev.denfry.promptcritical.mod.npp.SpentFuelPoolBlockEntity;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Бассейн выдержки и перегрузочная машина ВВЭР-70 (art/blockbench/core/vver70/vver70_fuel_handling.author.js):
 * сдвиги хранения partial-моделей из шапки возвращаются здесь. Только на клиенте.
 */
final class Vver70FuelHandlingRenderers {
    private static final PartialModel GATE = partial("vver70_fuel_pool_real/partial/gate");
    private static final PartialModel[] BRIDGE = {partial("vver70_refuelling_machine_real/partial/bridge_0"),
            partial("vver70_refuelling_machine_real/partial/bridge_1"), partial("vver70_refuelling_machine_real/partial/bridge_2"),
            partial("vver70_refuelling_machine_real/partial/bridge_3")};
    /** Левые края кусков моста, px: кусок n хранится сдвинутым на (−x0 − 16, −17, 0). */
    private static final int[] BRIDGE_X0 = {-64, -30, 8, 46};
    private static final PartialModel TROLLEY = partial("vver70_refuelling_machine_real/partial/trolley"),
            CABIN = partial("vver70_refuelling_machine_real/partial/cabin"),
            MAST_OUTER = partial("vver70_refuelling_machine_real/partial/mast_outer"),
            GRIPPER = partial("vver70_refuelling_machine_real/partial/gripper");
    private static final PartialModel[] MAST_INNER = {partial("vver70_refuelling_machine_real/partial/mast_inner_0"),
            partial("vver70_refuelling_machine_real/partial/mast_inner_1"), partial("vver70_refuelling_machine_real/partial/mast_inner_2"),
            partial("vver70_refuelling_machine_real/partial/mast_inner_3")};
    /** ТВС в захвате — цельная модель стоящей ТВС из топливного цикла. */
    private static final String ASSEMBLY = "vver440_fuel_assembly_real";
    private static final ResourceLocation WATER = ResourceLocation.withDefaultNamespace("block/water_still");
    /** Зеркало бассейна в модели, блоки от угла контроллера (внутри облицовки x, z −32…48 px). */
    private static final float POOL_MIN = -2, POOL_MAX = 3;

    private Vver70FuelHandlingRenderers() {
    }

    private static PartialModel partial(String path) {
        return PartialModel.of(PromptCritical.id("block/" + path));
    }

    static final class Pool extends WholeModelRenderer<SpentFuelPoolBlockEntity> {
        @Override
        public void render(SpentFuelPoolBlockEntity be, float pt, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
            super.render(be, pt, ms, buffers, light, overlay);
            if (be.getLevel() == null || be.wholeModelId() == null) return;
            water(be.levelM(), be.wholeModelFacing(), ms, buffers.getBuffer(RenderType.translucent()), light);
        }

        @Override
        protected void parts(SpentFuelPoolBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
            // затвор проёма: хранится на −32 y и +40 z, поднимается до 64 px
            part(GATE, be, be.wholeModelFacing(), 0, (32 + be.gate01() * 64) / 16.0, -40 / 16.0).light(light).renderInto(ms, vc);
        }

        /** Зеркало воды на уровне {@code levelM} над низом модели, в повороте модели. */
        private static void water(float levelM, Direction facing, PoseStack ms, VertexConsumer vc, int light) {
            TextureAtlasSprite s = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(WATER);
            ms.pushPose();
            ms.translate(0.5, 0, 0.5);
            ms.mulPose(Axis.YP.rotationDegrees(-(facing.toYRot() + 180)));
            ms.translate(-0.5, 0, -0.5);
            PoseStack.Pose pose = ms.last();
            float y = levelM, a = POOL_MIN, b = POOL_MAX;
            // вода бассейна: голубая, полупрозрачная, без эффекта плотности
            int r = 0x3F, g = 0x76, bl = 0xE4, al = 0xB0;
            for (int ix = 0; ix < (int) (b - a); ix++)
                for (int iz = 0; iz < (int) (b - a); iz++) {
                    float x0 = a + ix, x1 = x0 + 1, z0 = a + iz, z1 = z0 + 1;
                    vc.addVertex(pose, x0, y, z0).setColor(r, g, bl, al).setUv(s.getU0(), s.getV0()).setLight(light).setNormal(pose, 0, 1, 0);
                    vc.addVertex(pose, x0, y, z1).setColor(r, g, bl, al).setUv(s.getU0(), s.getV1()).setLight(light).setNormal(pose, 0, 1, 0);
                    vc.addVertex(pose, x1, y, z1).setColor(r, g, bl, al).setUv(s.getU1(), s.getV1()).setLight(light).setNormal(pose, 0, 1, 0);
                    vc.addVertex(pose, x1, y, z0).setColor(r, g, bl, al).setUv(s.getU1(), s.getV0()).setLight(light).setNormal(pose, 0, 1, 0);
                }
            ms.popPose();
        }
    }

    static final class Machine extends WholeModelRenderer<RefuellingMachineBlockEntity> {
        @Override
        protected void parts(RefuellingMachineBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
            Direction f = be.wholeModelFacing();
            RefuellingMachineBlockEntity.Pose p = be.pose(pt);
            double z = p.z(), x = p.x();
            for (int n = 0; n < 4; n++)
                part(BRIDGE[n], be, f, (BRIDGE_X0[n] + 16) / 16.0, 17 / 16.0, z).light(light).renderInto(ms, vc);
            part(CABIN, be, f, 34 / 16.0, 4 / 16.0, z).light(light).renderInto(ms, vc);
            part(TROLLEY, be, f, x, 28 / 16.0, z).light(light).renderInto(ms, vc);
            part(MAST_OUTER, be, f, x, 28 / 16.0, z).light(light).renderInto(ms, vc);
            // телескоп: низ захвата на глубине depth под полом; трубы выдвигаются по очереди, нижняя первой
            double e = Math.max(0, Math.min(4 * 48, p.depth() * 16 + 4));
            for (int n = 0; n < 4; n++) {
                double raise = 48 * (n + 1) - Math.min(48 * (n + 1), e);
                part(MAST_INNER[n], be, f, x, (-(48 * n + 20) + raise) / 16.0, z).light(light).renderInto(ms, vc);
            }
            part(GRIPPER, be, f, x, (12 - e) / 16.0, z).light(light).renderInto(ms, vc);
            if (be.clientCarry()) assembly(be, f, x, (4 - e) / 16.0, z, ms, vc, light);
        }

        /** Сборка висит под захватом: верх модели — у низа захвата. */
        private static void assembly(RefuellingMachineBlockEntity be, Direction f, double x, double topY, double z, PoseStack ms,
                VertexConsumer vc, int light) {
            WholeModels.Model m = WholeModels.get(ASSEMBLY);
            if (m == null) return;
            double dy = topY - m.max()[1];
            float yRot = -(f.toYRot() + 180);
            for (WholeModels.Chunk c : m.chunks())
                CachedBuffers.partial(c.partial(), be.getBlockState()).center().rotateYDegrees(yRot).uncenter()
                        .translate(x + c.x(), dy + c.y(), z + c.z()).light(light).renderInto(ms, vc);
        }
    }
}

package dev.denfry.promptcritical.mod.foundry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer;
import dev.denfry.promptcritical.mod.client.whole.WholeModels;
import dev.denfry.promptcritical.mod.foundry.CoalJigBlockEntity;
import dev.denfry.promptcritical.mod.foundry.CoalJigRealBlock;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/**
 * Отсадочная машина: старая — {@link FoundryMachineRenderer} с пульсатором, машина Баума реального масштаба
 * (art/blockbench/coal_prep/coal_jig/coal_jig_real.author.js) — цельная модель, линия вала воздушных клапанов,
 * которую крутит вал Create в клетке порта, и вода над постелью, пульсирующая с ходом клапанов. Обе половины вала
 * нарисованы на блок ниже своего места (z 0…31 и 32…61 px вдоль оси на x 24, y 40), вода — со сдвигом (0, −16, −28).
 */
final class CoalJigRenderer extends WholeModelRenderer<CoalJigBlockEntity> {
    static final PartialModel SHAFT_N = partial("shaft_n"), SHAFT_S = partial("shaft_s"), WATER = partial("water");
    /** Ось вала в partial (на блок ниже модели), блоков. */
    private static final float AXIS_X = 24 / 16f, AXIS_Y = 24 / 16f;
    /** Период пульсации воды, тиков (≈ 60 ходов в минуту у машин Баума), и её размах, блоков. */
    private static final float PULSE_TICKS = 20, PULSE = 1 / 16f;

    static {
        WholeModels.register("coal_jig_real");
    }

    private final FoundryMachineRenderer<CoalJigBlockEntity> old;

    CoalJigRenderer(FoundryMachineRenderer<CoalJigBlockEntity> old) {
        this.old = old;
    }

    private static PartialModel partial(String name) {
        return PartialModel.of(PromptCritical.id("block/coal_jig_real/partial/" + name));
    }

    /** Загрузить класс до запекания моделей: цельная модель и partial-модели регистрируются в статике. */
    static void init() {
    }

    @Override
    public void render(CoalJigBlockEntity be, float pt, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        if (be.wholeModelId() == null) old.render(be, pt, ms, buffers, light, overlay);
        else super.render(be, pt, ms, buffers, light, overlay);
    }

    @Override
    protected void parts(CoalJigBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
        Direction f = be.wholeModelFacing();
        float a = shaftAngle(be);
        part(SHAFT_N, be, f, 0, 1, 0).translate(AXIS_X, AXIS_Y, 0).rotateZDegrees(a).translate(-AXIS_X, -AXIS_Y, 0)
                .light(light).renderInto(ms, vc);
        part(SHAFT_S, be, f, 0, 1, 2).translate(AXIS_X, AXIS_Y, 0).rotateZDegrees(a).translate(-AXIS_X, -AXIS_Y, 0)
                .light(light).renderInto(ms, vc);
        boolean running = be.getBlockState().getValue(PcProperties.RUNNING) && be.getLevel() != null;
        double dy = running ? PULSE * Math.sin(AnimationTickHolder.getRenderTime(be.getLevel()) * 2 * Math.PI / PULSE_TICKS) : 0;
        part(WATER, be, f, 0, 1 + dy, 1.75).light(light).renderInto(ms, vc);
    }

    /** Угол вала, градусы: по скорости клетки порта (её синхронизирует Create), той же формулой, что у валов Create. */
    private static float shaftAngle(CoalJigBlockEntity be) {
        if (be.getLevel() == null) return 0;
        var port = CoalJigRealBlock.portPos(be.getBlockPos(), be.wholeModelFacing());
        if (!(be.getLevel().getBlockEntity(port) instanceof KineticBlockEntity k)) return 0;
        return (AnimationTickHolder.getRenderTime(be.getLevel()) * k.getSpeed() * 3f / 10) % 360;
    }

    @Override
    public AABB getRenderBoundingBox(CoalJigBlockEntity be) {
        return be.wholeModelId() == null ? old.getRenderBoundingBox(be) : super.getRenderBoundingBox(be);
    }

    @Override
    public boolean shouldRenderOffScreen(CoalJigBlockEntity be) {
        return be.wholeModelId() == null ? old.shouldRenderOffScreen(be) : super.shouldRenderOffScreen(be);
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }
}

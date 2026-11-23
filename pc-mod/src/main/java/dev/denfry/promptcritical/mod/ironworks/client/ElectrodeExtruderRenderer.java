package dev.denfry.promptcritical.mod.ironworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer;
import dev.denfry.promptcritical.mod.client.whole.WholeModels;
import dev.denfry.promptcritical.mod.foundry.client.FoundryMachineRenderer;
import dev.denfry.promptcritical.mod.ironworks.ElectrodeExtruderBlockEntity;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/**
 * Электродный пресс: старый — {@link FoundryMachineRenderer} со шнеком, пресс реального масштаба
 * (art/blockbench/coke/electrode_extruder_real.author.js) — цельная модель, плунжер с траверсой и сырой электрод на
 * рольганге. Пока пресс работает, плунжер за цикл проходит ход 24 px к мундштуку и отходит, электрод выходит из
 * мундштука на рольганг; стоит — плунжер отведён, электрода нет. Partial плунжера нарисован на 2 px восточнее места,
 * электрода — на 48 px.
 */
final class ElectrodeExtruderRenderer extends WholeModelRenderer<ElectrodeExtruderBlockEntity> {
    static final PartialModel RAM = partial("ram"), ELECTRODE = partial("electrode");
    /** Цикл пресса на экране, тиков; ход плунжера и выход электрода, px. */
    private static final float CYCLE_TICKS = 160, STROKE = 24, EXTRUDE = 12;

    static {
        WholeModels.register("electrode_extruder_real");
    }

    private final FoundryMachineRenderer<ElectrodeExtruderBlockEntity> old;

    ElectrodeExtruderRenderer(FoundryMachineRenderer<ElectrodeExtruderBlockEntity> old) {
        this.old = old;
    }

    private static PartialModel partial(String name) {
        return PartialModel.of(PromptCritical.id("block/electrode_extruder_real/partial/" + name));
    }

    /** Загрузить класс до запекания моделей: цельная модель и partial-модели регистрируются в статике. */
    static void init() {
    }

    @Override
    public void render(ElectrodeExtruderBlockEntity be, float pt, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        if (be.wholeModelId() == null) old.render(be, pt, ms, buffers, light, overlay);
        else super.render(be, pt, ms, buffers, light, overlay);
    }

    @Override
    protected void parts(ElectrodeExtruderBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
        Direction f = be.wholeModelFacing();
        boolean running = be.getBlockState().getValue(PcProperties.RUNNING) && be.getLevel() != null;
        float phase = running ? (AnimationTickHolder.getRenderTime(be.getLevel()) % CYCLE_TICKS) / CYCLE_TICKS : 0;
        part(RAM, be, f, (-2 - STROKE * phase) / 16, 0, 0).light(light).renderInto(ms, vc);
        if (running) part(ELECTRODE, be, f, (-48 + EXTRUDE * (1 - phase)) / 16, 0, 0).light(light).renderInto(ms, vc);
    }

    @Override
    public AABB getRenderBoundingBox(ElectrodeExtruderBlockEntity be) {
        return be.wholeModelId() == null ? old.getRenderBoundingBox(be) : super.getRenderBoundingBox(be);
    }

    @Override
    public boolean shouldRenderOffScreen(ElectrodeExtruderBlockEntity be) {
        return be.wholeModelId() == null ? old.shouldRenderOffScreen(be) : super.shouldRenderOffScreen(be);
    }
}

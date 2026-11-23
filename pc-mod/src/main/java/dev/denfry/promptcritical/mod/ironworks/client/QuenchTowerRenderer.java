package dev.denfry.promptcritical.mod.ironworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer;
import dev.denfry.promptcritical.mod.client.whole.WholeModels;
import dev.denfry.promptcritical.mod.ironworks.QuenchTowerBlockEntity;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;

/**
 * Башня мокрого тушения реального масштаба (art/blockbench/coke/quench_tower/quench_tower_real.author.js): цельная
 * модель и завесы воды из форсунок, пока идёт тушение ({@code running}); partial нарисована на блок выше своего места.
 * У старой башни BER ничего не рисует: модели у неё блочные.
 */
final class QuenchTowerRenderer extends WholeModelRenderer<QuenchTowerBlockEntity> {
    static final PartialModel SPRAY = PartialModel.of(PromptCritical.id("block/quench_tower_real/partial/spray"));

    static {
        WholeModels.register("quench_tower_real");
    }

    /** Загрузить класс до запекания моделей: цельная модель и partial регистрируются в статике. */
    static void init() {
    }

    @Override
    protected void parts(QuenchTowerBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
        if (be.getBlockState().getValue(PcProperties.RUNNING))
            part(SPRAY, be, be.wholeModelFacing(), 0, -1, 0).light(light).renderInto(ms, vc);
    }
}

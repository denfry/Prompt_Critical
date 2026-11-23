package dev.denfry.promptcritical.mod.foundry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer;
import dev.denfry.promptcritical.mod.foundry.CrucibleBlockEntity;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Тигельная печь реального масштаба (docs/research/crucible_furnace-dimensions.md): цельная модель ямы, крышка в
 * закрытом положении и жар кокса под решёткой, пока печь горит. Части в координатах цельной модели.
 */
final class CrucibleRealRenderer extends WholeModelRenderer<CrucibleBlockEntity> {
    static final PartialModel LID = partial("lid"), GLOW = partial("glow");

    private static PartialModel partial(String name) {
        return PartialModel.of(PromptCritical.id("block/crucible_furnace_real/partial/" + name));
    }

    /** Загрузить класс до запекания моделей: partial-модели регистрируются в статике. */
    static void init() {
    }

    @Override
    protected void parts(CrucibleBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
        Direction f = be.wholeModelFacing();
        part(LID, be, f, 0, 0, 0).light(light).renderInto(ms, vc);
        BlockState s = be.getBlockState();
        if (s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT))
            part(GLOW, be, f, 0, 0, 0).light(LightTexture.FULL_BRIGHT).renderInto(ms, vc);
    }
}

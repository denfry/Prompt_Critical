package dev.denfry.promptcritical.mod.ironworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer;
import dev.denfry.promptcritical.mod.client.whole.WholeModels;
import dev.denfry.promptcritical.mod.ironworks.HotBlastStoveRealBlockEntity;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Каупер реального масштаба (art/blockbench/ironmaking/hot_blast_stove/hot_blast_stove_real.author.js): цельная модель,
 * поверх — шпиндели трёх клапанов и свечение горелки. «На газе» горит горелка и поднят дымовой клапан; «на дутье»
 * подняты клапаны холодного и горячего дутья; отсечённый каупер — всё закрыто.
 */
final class HotBlastStoveRealRenderer extends WholeModelRenderer<HotBlastStoveRealBlockEntity> {
    static final PartialModel HOT_VALVE = partial("hot_valve"), COLD_VALVE = partial("cold_valve"),
            CHIMNEY_VALVE = partial("chimney_valve"), BURNER_GLOW = partial("burner_glow");
    /** Ход шпинделя при открытии, блоки: задвижка горячего дутья 13 px, грибовидные клапаны 6 px. */
    private static final double GATE_LIFT = 13 / 16.0, MUSHROOM_LIFT = 6 / 16.0;

    static {
        WholeModels.register("hot_blast_stove_real");
    }

    private static PartialModel partial(String name) {
        return PartialModel.of(PromptCritical.id("block/hot_blast_stove_real/partial/" + name));
    }

    /** Загрузить класс до запекания моделей: цельная модель и partial-модели регистрируются в статике. */
    static void init() {
    }

    @Override
    protected void parts(HotBlastStoveRealBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
        Direction f = be.wholeModelFacing();
        BlockState s = be.getBlockState();
        boolean onGas = s.getValue(BlockStateProperties.LIT), onBlast = s.getValue(PcProperties.RUNNING);
        // сдвиги partial → цельная модель, блоки (шапка author.js): горячая задвижка (0, 4, 0), холодный клапан
        // (0, 1, 4), дымовой (2, 1, 2), горелка (0, 1, 0)
        part(HOT_VALVE, be, f, 0, 4 + (onBlast ? GATE_LIFT : 0), 0).light(light).renderInto(ms, vc);
        part(COLD_VALVE, be, f, 0, 1 + (onBlast ? MUSHROOM_LIFT : 0), 4).light(light).renderInto(ms, vc);
        part(CHIMNEY_VALVE, be, f, 2, 1 + (onGas ? MUSHROOM_LIFT : 0), 2).light(light).renderInto(ms, vc);
        if (onGas) part(BURNER_GLOW, be, f, 0, 1, 0).light(LightTexture.FULL_BRIGHT).renderInto(ms, vc);
    }
}

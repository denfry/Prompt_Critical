package dev.denfry.promptcritical.mod.steelworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer;
import dev.denfry.promptcritical.mod.client.whole.WholeModels;
import dev.denfry.promptcritical.mod.steelworks.ReheatingFurnaceRealBlockEntity;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Методическая печь реального масштаба (docs/research/reheating_furnace-dimensions.md, «Partials»): цельная модель
 * кладки, поверх — заслонка окна выдачи (поднимается на 12 px на цепях), заслонка загрузочного окна (на 8 px) и
 * свечение окон, глазков и горелки, пока печь горит.
 */
final class ReheatingRealRenderer extends WholeModelRenderer<ReheatingFurnaceRealBlockEntity> {
    private static final PartialModel DOOR = partial("door"), CHARGE_DOOR = partial("charge_door"), GLOW = partial("glow");
    private static final double DOOR_RISE = 12 / 16.0, CHARGE_DOOR_RISE = 8 / 16.0;

    static {
        WholeModels.register("reheating_furnace_real");
    }

    private static PartialModel partial(String name) {
        return PartialModel.of(PromptCritical.id("block/reheating_furnace_real/partial/" + name));
    }

    /** Загрузить класс до запекания моделей: цельная модель и partial регистрируются в статике. */
    static void init() {
    }

    @Override
    protected void parts(ReheatingFurnaceRealBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
        Direction f = be.wholeModelFacing();
        part(DOOR, be, f, 0, DOOR_RISE * be.dischargeDoorLift(pt), 0).light(light).renderInto(ms, vc);
        part(CHARGE_DOOR, be, f, 0, CHARGE_DOOR_RISE * be.chargeDoorLift(pt), 0).light(light).renderInto(ms, vc);
        if (be.getBlockState().getValue(BlockStateProperties.LIT))
            part(GLOW, be, f, 0, 0, 0).light(LightTexture.FULL_BRIGHT).renderInto(ms, vc);
    }
}

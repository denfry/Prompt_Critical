package dev.denfry.promptcritical.mod.grid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer;
import dev.denfry.promptcritical.mod.grid.RealShaftMachineBlock;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Электрическая машина реального масштаба (shaft_generator_real, electric_motor_mv_real): цельная модель и вращающаяся
 * часть на оси вала — ротор генератора или конец вала с полумуфтой двигателя. Часть нарисована в клетке (0, 0) и
 * поднята на блок к оси (8, 24) px; крутится вокруг z модели со скоростью вала Create в клетке-порте.
 */
final class RealShaftRenderer<T extends BlockEntity & WholeModelHost> extends WholeModelRenderer<T> {
    private final PartialModel moving;
    private final BlockPos port;

    RealShaftRenderer(PartialModel part, BlockPos port) {
        this.moving = part;
        this.port = port;
    }

    /** Обороты вала с клетки-порта (контроллер вала не имеет), об/мин со знаком. */
    private float rpm(T be, Direction f) {
        return be.getLevel() != null && be.getLevel().getBlockEntity(RealShaftMachineBlock.at(be.getBlockPos(), f, port)) instanceof KineticBlockEntity k
                ? k.getSpeed() : 0;
    }

    @Override
    protected void parts(T be, float pt, PoseStack ms, VertexConsumer vc, int light) {
        Direction f = be.wholeModelFacing();
        // как у Create: град = время · об/мин · 3/10
        float deg = (AnimationTickHolder.getRenderTime(be.getLevel()) * rpm(be, f) * 0.3f) % 360f;
        part(moving, be, f, 0, 1, 0).translate(0.5, 0.5, 0).rotateZDegrees(deg).translate(-0.5, -0.5, 0).light(light).renderInto(ms, vc);
    }
}

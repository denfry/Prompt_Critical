package dev.denfry.promptcritical.mod.npp.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer;
import dev.denfry.promptcritical.mod.npp.NpdFuellingMachineBlockEntity;
import dev.denfry.promptcritical.mod.npp.NpdReactorBlockEntity;
import dev.denfry.promptcritical.sim.npp.NpdChannels;
import dev.denfry.promptcritical.sim.npp.NpdFuellingMachine;

/**
 * Подвижные части NPD (шапка art/blockbench/core/candu_npd/candu_npd.author.js). Реактор — уплотнительная пробка:
 * хранится у центрального канала северного торца со сдвигом (0, −40, +40) px; у перегружаемого канала она выходит
 * на 8 px наружу. Машина — мост из двух половин (поднимается до 56 px), тележка по мосту (±40 px), головка и хобот
 * (выдвигается на 12 px к торцу); положение — от автомата реактора, у южного торца поперёк зеркально.
 */
final class NpdRenderers {
    /**
     * Ось каландра над полом реактора (56 px) и ось хобота над полом машины при опущенном мосте (66 px); ход моста
     * 0…56 px, тележки ±40 px. На одном полу с реактором хобот достаёт только каналы выше оси — машину ставят в
     * приямок на два блока ниже, тогда достаёт почти все.
     */
    private static final double AXIS_PX = 56, SNOUT_PX = 66, BRIDGE_MAX = 56, CARRIAGE_MAX = 40;

    private NpdRenderers() {
    }

    static final class Reactor extends WholeModelRenderer<NpdReactorBlockEntity> {
        @Override
        protected void parts(NpdReactorBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
            int ch = be.viewPhase() == NpdFuellingMachine.Phase.IDLE.ordinal() ? -1 : be.viewChannel();
            double dx = 0, dy = 0, out = 0;
            if (ch >= 0) {
                dx = (NpdChannels.x(ch) - NpdChannels.x(0)) * 16;
                dy = (NpdChannels.y(ch) - NpdChannels.y(0)) * 16;
                out = be.viewPlug() * 8;
            }
            part(NppClient.NPD_PLUG, be, be.wholeModelFacing(), dx / 16, (40 + dy) / 16, (-40 - out) / 16).light(light).renderInto(ms, vc);
        }
    }

    static final class Machine extends WholeModelRenderer<NpdFuellingMachineBlockEntity> {
        @Override
        protected void parts(NpdFuellingMachineBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
            var f = be.wholeModelFacing();
            double lift = clamp(AXIS_PX + be.viewY() * 16 - SNOUT_PX - be.viewDy() * 16, 0, BRIDGE_MAX);
            double across = clamp(be.viewSign() * be.viewX() * 16, -CARRIAGE_MAX, CARRIAGE_MAX);
            double snout = be.viewSnout() * 12;
            part(NppClient.NPD_BRIDGE_W, be, f, -24 / 16.0, (24 + lift) / 16, 0).light(light).renderInto(ms, vc);
            part(NppClient.NPD_BRIDGE_E, be, f, 24 / 16.0, (24 + lift) / 16, 0).light(light).renderInto(ms, vc);
            part(NppClient.NPD_CARRIAGE, be, f, across / 16, (48 + lift) / 16, 0).light(light).renderInto(ms, vc);
            // барабан магазина: 1/8 оборота на пучок вокруг z через (8, 66) px
            part(NppClient.NPD_HEAD, be, f, across / 16, (48 + lift) / 16, 0).translate(8 / 16.0, 18 / 16.0, 0)
                    .rotateZDegrees(45f * be.viewPushed()).translate(-8 / 16.0, -18 / 16.0, 0).light(light).renderInto(ms, vc);
            part(NppClient.NPD_SNOUT, be, f, across / 16, (48 + lift) / 16, snout / 16).light(light).renderInto(ms, vc);
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}

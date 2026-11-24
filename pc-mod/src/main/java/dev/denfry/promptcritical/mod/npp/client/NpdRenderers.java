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
 * на 8 px наружу. Машина — мост из двух половин (поднимается до 54 px), тележка по мосту (±40 px), головка и хобот
 * (выдвигается на 12 px к торцу); положение — от автомата реактора, у южного торца поперёк зеркально.
 */
final class NpdRenderers {
    /**
     * Ось каландра над полом реактора (56 px) и ось хобота над полом машины при опущенном мосте (29 px); ход моста
     * 0…54 px — хобот проходит от нижнего ряда каналов (29 px) до верхнего (83 px), машина стоит на полу реактора.
     */
    private static final double AXIS_PX = 56, SNOUT_PX = 29, BRIDGE_MAX = 54, CARRIAGE_MAX = 40;

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
            // сдвиги хранения из скрипта: мост (±24, 0), тележка 0, головка (0, −16), хобот (0, −8) px
            part(NppClient.NPD_BRIDGE_W, be, f, -24 / 16.0, lift / 16, 0).light(light).renderInto(ms, vc);
            part(NppClient.NPD_BRIDGE_E, be, f, 24 / 16.0, lift / 16, 0).light(light).renderInto(ms, vc);
            part(NppClient.NPD_CARRIAGE, be, f, across / 16, lift / 16, 0).light(light).renderInto(ms, vc);
            // барабан магазина: 1/8 оборота на пучок вокруг z через (8, 29) px, в хранении (8, 13)
            part(NppClient.NPD_HEAD, be, f, across / 16, (16 + lift) / 16, 0).translate(8 / 16.0, 13 / 16.0, 0)
                    .rotateZDegrees(45f * be.viewPushed()).translate(-8 / 16.0, -13 / 16.0, 0).light(light).renderInto(ms, vc);
            part(NppClient.NPD_SNOUT, be, f, across / 16, (8 + lift) / 16, snout / 16).light(light).renderInto(ms, vc);
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}

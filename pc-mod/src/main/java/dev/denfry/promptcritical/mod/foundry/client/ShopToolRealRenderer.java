package dev.denfry.promptcritical.mod.foundry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer;
import dev.denfry.promptcritical.mod.client.whole.WholeModels;
import dev.denfry.promptcritical.mod.foundry.PortKineticMachineBlock;
import dev.denfry.promptcritical.mod.foundry.PortKineticMachineBlockEntity;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Станок цеха I0 реального масштаба (art/blockbench/machine_shop/real/machine_shop_real.author.js, MSRL): цельная
 * модель станка со стойками контрпривода, поверх — контрпривод (вал, ступенчатый шкив, муфта), который крутит вал
 * Create в клетке порта, и рабочая часть станка: шпиндель или оправка крутятся от контрпривода через ремень, ползун
 * строгального и стол шлифовального ходят взад-вперёд, сверло подаётся вниз, пока станок режет.
 *
 * <p>Контрпривод сохранён сдвинутым на (0, −16, 0) px, у токарного — на (−16, −16, 0): рисуется со сдвигом обратно.
 * Ось контрпривода — x через (y 40, z zc). Прочие части — в пикселях модели без сдвига.
 */
final class ShopToolRealRenderer extends WholeModelRenderer<PortKineticMachineBlockEntity<?>> {
    /** Как двигается рабочая часть станка. */
    enum Motion { SPIN_X, SPIN_Y, SPIN_Z, SLIDE_X, FEED_Y }

    /**
     * Рабочая часть: partial, движение, ось (px модели), передача к контрприводу (у FEED_Y — рад за тик подачи), ход (px).
     */
    record Part(PartialModel model, Motion motion, float a, float b, float gear, float travel) {}

    private final PartialModel countershaft;
    /** Обратный сдвиг контрпривода, блоков, и его ось (z, px). */
    private final double shiftX, shiftY;
    private final float zc;
    private final Part[] parts;

    ShopToolRealRenderer(String id, double shiftXPx, float zcPx, Part... parts) {
        this.countershaft = COUNTERSHAFTS.get(id);
        this.shiftX = shiftXPx / 16;
        this.shiftY = 1;
        this.zc = zcPx;
        this.parts = parts;
    }

    static PartialModel partial(String id, String name) {
        return PartialModel.of(PromptCritical.id("block/" + id + "/partial/" + name));
    }

    private static final String[] IDS = {"machine_lathe_real", "drill_press_real", "milling_machine_real", "shaping_machine_real",
            "surface_grinder_real"};
    private static final java.util.Map<String, PartialModel> COUNTERSHAFTS = new java.util.HashMap<>();
    private static final PartialModel LATHE_SPINDLE = partial("machine_lathe_real", "spindle"),
            DRILL_SPINDLE = partial("drill_press_real", "spindle"), MILL_ARBOR = partial("milling_machine_real", "arbor"),
            SHAPER_CONE = partial("shaping_machine_real", "cone"), SHAPER_RAM = partial("shaping_machine_real", "ram"),
            GRINDER_WHEEL = partial("surface_grinder_real", "wheel"), GRINDER_TABLE = partial("surface_grinder_real", "table");

    static {
        for (String id : IDS) {
            WholeModels.register(id);
            COUNTERSHAFTS.put(id, partial(id, "countershaft"));
        }
    }

    /** Загрузить класс до запекания моделей: цельные модели и partial-модели регистрируются в статике. */
    static void init() {
    }

    static ShopToolRealRenderer lathe() {
        return new ShopToolRealRenderer("machine_lathe_real", 16, 8, new Part(LATHE_SPINDLE, Motion.SPIN_X, 19, 8, 1, 0));
    }

    static ShopToolRealRenderer drillPress() {
        return new ShopToolRealRenderer("drill_press_real", 0, 8, new Part(DRILL_SPINDLE, Motion.SPIN_Y, 8, 8, 4, 0),
                new Part(DRILL_SPINDLE, Motion.FEED_Y, 0, 0, 0.05f, 1));
    }

    static ShopToolRealRenderer milling() {
        return new ShopToolRealRenderer("milling_machine_real", 0, 24, new Part(MILL_ARBOR, Motion.SPIN_X, 24, 24, 2, 0));
    }

    static ShopToolRealRenderer shaper() {
        return new ShopToolRealRenderer("shaping_machine_real", 0, 8, new Part(SHAPER_CONE, Motion.SPIN_X, 16, 8, 1, 0),
                new Part(SHAPER_RAM, Motion.SLIDE_X, 0, 0, 0.5f, 6));
    }

    static ShopToolRealRenderer grinder() {
        return new ShopToolRealRenderer("surface_grinder_real", 0, 8, new Part(GRINDER_WHEEL, Motion.SPIN_Z, 16, 19, 4, 0),
                new Part(GRINDER_TABLE, Motion.SLIDE_X, 0, 0, 0.1f, 6));
    }

    @Override
    protected void parts(PortKineticMachineBlockEntity<?> be, float pt, PoseStack ms, VertexConsumer vc, int light) {
        Direction f = be.wholeModelFacing();
        float time = AnimationTickHolder.getRenderTime(be.getLevel());
        float speed = portSpeed(be);
        // фаза до свёртки по 360°: у частей с передачей ≠ 1 иначе был бы скачок на каждом обороте вала
        float raw = time * speed * 3f / 10, angle = raw % 360;
        float ay = 40 / 16f, az = zc / 16f;
        part(countershaft, be, f, 0, 0, 0).translate(0, ay, az).rotateXDegrees(angle).translate(0, -ay, -az)
                .translate(shiftX, shiftY, 0).light(light).renderInto(ms, vc);
        boolean cutting = be.getBlockState().hasProperty(PcProperties.RUNNING) && be.getBlockState().getValue(PcProperties.RUNNING);
        // шпиндель со сверлом рисуется один раз: вращение и подача вместе
        float feed = 0;
        for (Part p : parts)
            if (p.motion() == Motion.FEED_Y && cutting) feed = (float) (0.5 * (1 - Math.cos(time * p.gear()))) * p.travel() / 16;
        for (Part p : parts) {
            float a = p.a() / 16, b = p.b() / 16, turn = (raw * p.gear()) % 360;
            switch (p.motion()) {
                case SPIN_X -> part(p.model(), be, f, 0, 0, 0).translate(0, a, b).rotateXDegrees(turn).translate(0, -a, -b)
                        .light(light).renderInto(ms, vc);
                case SPIN_Y -> part(p.model(), be, f, 0, -feed, 0).translate(a, 0, b).rotateYDegrees(turn).translate(-a, 0, -b)
                        .light(light).renderInto(ms, vc);
                case SPIN_Z -> part(p.model(), be, f, 0, 0, 0).translate(a, b, 0).rotateZDegrees(turn).translate(-a, -b, 0)
                        .light(light).renderInto(ms, vc);
                // ползун и стол ходят, только пока станок режет; стоят в среднем положении
                case SLIDE_X -> part(p.model(), be, f, cutting ? Math.sin(Math.toRadians(turn)) * p.travel() / 16 : 0, 0, 0)
                        .light(light).renderInto(ms, vc);
                case FEED_Y -> {
                }
            }
        }
    }

    /** Обороты вала Create в клетке порта (её синхронизирует Create), об/мин со знаком. */
    private static float portSpeed(PortKineticMachineBlockEntity<?> be) {
        if (be.getLevel() == null || !(be.getBlockState().getBlock() instanceof PortKineticMachineBlock b)) return 0;
        BlockPos port = be.getBlockPos().offset(b.port().rotate(Footprint.rotation(be.wholeModelFacing())));
        return be.getLevel().getBlockEntity(port) instanceof KineticBlockEntity k ? k.getSpeed() : 0;
    }
}

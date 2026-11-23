package dev.denfry.promptcritical.mod.nonferrous.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.client.whole.WholeModelRenderer;
import dev.denfry.promptcritical.mod.client.whole.WholeModels;
import dev.denfry.promptcritical.mod.nonferrous.AluminaPrecipitatorBlockEntity;
import dev.denfry.promptcritical.mod.nonferrous.NonferrousBlockEntities;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Клиент цветной металлургии реального масштаба (08 §8.8): цельные модели ванны электрорафинирования, конвертера
 * Пирса–Смита, электролизёра, автоклава, декомпозера и отражательной печи. Варианты состояний — отдельные цельные
 * модели (их выбирает BE); у декомпозера поверх — вал мешалки, который вращается, пока аппарат работает. Старые блоки
 * тех же BE рисуются моделью блока, как раньше: у них {@code wholeModelId()} — null.
 */
public final class NonferrousClient {
    private static final String[] MODELS = {
            "electrorefining_cell_real", "electrorefining_cell_real_ni",
            "peirce_smith_converter_real", "peirce_smith_converter_real_running",
            "reduction_pot_real", "reduction_pot_real_running", "reduction_pot_real_alarm",
            "bayer_digester_real", "alumina_precipitator_real",
            "matte_smelter_real", "matte_smelter_real_lit"};

    private NonferrousClient() {
    }

    public static void register(IEventBus modBus) {
        for (String id : MODELS) WholeModels.register(id);
        PrecipitatorRenderer.init();
        modBus.addListener(NonferrousClient::registerRenderers);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(NonferrousBlockEntities.ELECTROREFINING_CELL.get(), c -> new WholeModelRenderer<>());
        event.registerBlockEntityRenderer(NonferrousBlockEntities.PEIRCE_SMITH_CONVERTER.get(), c -> new WholeModelRenderer<>());
        event.registerBlockEntityRenderer(NonferrousBlockEntities.REDUCTION_POT.get(), c -> new WholeModelRenderer<>());
        event.registerBlockEntityRenderer(NonferrousBlockEntities.BAYER_DIGESTER.get(), c -> new WholeModelRenderer<>());
        event.registerBlockEntityRenderer(NonferrousBlockEntities.ALUMINA_PRECIPITATOR.get(), c -> new PrecipitatorRenderer());
        event.registerBlockEntityRenderer(NonferrousBlockEntities.MATTE_SMELTER.get(), c -> new WholeModelRenderer<>());
    }

    /** Декомпозер: вал мешалки с муфтой над мостом, ось y через (8, 8) px клетки контроллера, partial поднят на 8 блоков. */
    static final class PrecipitatorRenderer extends WholeModelRenderer<AluminaPrecipitatorBlockEntity> {
        private static final PartialModel DRIVE = PartialModel.of(PromptCritical.id("block/alumina_precipitator_real/partial/drive"));
        /** Мешалка ≈ 8 об/мин: 2,4° за тик. */
        private static final float DEG_PER_TICK = 2.4f;
        private static final double DRIVE_RISE = 8;

        /** Загрузить класс до запекания моделей: partial регистрируется в статике. */
        static void init() {
        }

        @Override
        protected void parts(AluminaPrecipitatorBlockEntity be, float pt, PoseStack ms, VertexConsumer vc, int light) {
            boolean running = be.getBlockState().getValue(PcProperties.RUNNING);
            float angle = running && be.getLevel() != null ? (be.getLevel().getGameTime() % 150 + pt) * DEG_PER_TICK : 0;
            part(DRIVE, be, be.wholeModelFacing(), 0, DRIVE_RISE, 0).center().rotateYDegrees(angle).uncenter()
                    .light(light).renderInto(ms, vc);
        }
    }
}

package dev.denfry.promptcritical.mod.data;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ItemModelBuilder;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * Состояния блоков по готовым моделям арт-пайплайна (00 §0.12, {@code art/README.md}): {@code <id>} и
 * {@code <id>_<состояние>} для визуально различимых вариантов, руды — по породе {@code host}. Модели только
 * читаются (ExistingFileHelper проверяет, что файл есть); поворот — по {@code facing}, фасад модели на север.
 */
public final class PcBlockStateProvider extends BlockStateProvider {
    public PcBlockStateProvider(PackOutput output, ExistingFileHelper efh) {
        super(output, PromptCritical.MOD_ID, efh);
    }

    @Override
    protected void registerStatesAndModels() {
        for (DeferredBlock<? extends Block> db : PcBlocks.withItems()) {
            Block block = db.get();
            String id = db.getId().getPath();
            PcBlocks.Form form = PcBlocks.decorForms().get(db);
            if (form != null) {
                decorForm(block, id, form);
                continue;
            }
            getVariantBuilder(block).forAllStates(state -> ConfiguredModel.builder()
                    .modelFile(existing(modelName(id, state)))
                    .rotationY(rotationY(state))
                    .build());
            itemModel(id, block);
        }
        // второй предмет двери — регулятор (08 §8.2.7)
        itemModel(PcItems.VENTILATION_REGULATOR.getId().getPath(), "ventilation_door_regulator", PcBlocks.VENTILATION_DOOR.get());
        ModelFile part = models().getBuilder("multiblock_part").texture("particle", modLoc("block/common/steel_dark"));
        simpleBlock(PcBlocks.MULTIBLOCK_PART.get(), part);
        simpleBlock(PcBlocks.KINETIC_PORT.get(), part);
        // жидкость рисует рендер жидкостей; модели нужна только частица
        simpleBlock(PcBlocks.MINE_WATER.get(), models().getBuilder("mine_water").texture("particle", modLoc("block/fluid/mine_water_still")));
    }

    /** Плита, ступени и стенка по ванильным шаблонам из текстуры базового блока декора. */
    private void decorForm(Block block, String id, PcBlocks.Form form) {
        var tex = modLoc("block/decor/" + form.base());
        switch (form.kind()) {
            case "slab" -> slabBlock((SlabBlock) block, modLoc("block/" + form.base()), tex);
            case "stairs" -> stairsBlock((StairBlock) block, tex);
            default -> {
                wallBlock((WallBlock) block, tex);
                itemModels().wallInventory(id, tex);
                return;
            }
        }
        itemModels().withExistingParent(id, modLoc("block/" + id));
    }

    private ModelFile existing(String name) {
        return models().getExistingFile(modLoc("block/" + name));
    }

    private static int rotationY(BlockState state) {
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return 0;
        Direction f = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        return ((int) f.toYRot() + 180) % 360;
    }

    /** Имя модели состояния; варианты без отдельной модели рисуются базовой. */
    static String modelName(String id, BlockState s) {
        return switch (id) {
            case "fire_tube_boiler" -> {
                boolean exploded = s.getValue(PcProperties.EXPLODED);
                if (s.getValue(PcProperties.BOILER_SEGMENT) == PcProperties.BoilerSegment.REAR)
                    yield exploded ? id + "_rear_exploded" : id + "_rear";
                if (exploded) yield id + "_exploded";
                String lit = s.getValue(BlockStateProperties.LIT) ? "_lit" : "";
                yield id + lit + (s.getValue(PcProperties.LOW_WATER) ? "_low_water" : "");
            }
            case "steam_engine_cylinder" -> s.getValue(PcProperties.ENGINE_SEGMENT) == PcProperties.EngineSegment.FRAME ? id + "_frame" : id;
            case "grate_stoker", "main_mine_fan" -> s.getValue(PcProperties.RUNNING) ? id + "_running" : id;
            case "chimney" -> switch (s.getValue(PcProperties.CHIMNEY_PART)) {
                case BASE -> id + "_base";
                case TOP -> id + "_top";
                case MIDDLE -> id;
            };
            case "coal_seam" -> switch (s.getValue(PcProperties.RANK)) {
                case LIGNITE -> id + "_lignite";
                case ANTHRACITE -> id + "_anthracite";
                case BITUMINOUS -> hosted(id, s);
            };
            case "mine_support" -> s.getValue(PcProperties.SUPPORT_KIND) == PcProperties.SupportKind.ARCH ? id + "_arch"
                    : s.getValue(PcProperties.DAMAGED) ? id + "_damaged" : id;
            case "ventilation_door" -> s.getValue(PcProperties.DOOR_KIND) == PcProperties.DoorKind.REGULATOR ? id + "_regulator"
                    : s.getValue(BlockStateProperties.OPEN) ? id + "_open" : id;
            case "explosion_barrier" -> s.getValue(PcProperties.BARRIER_KIND) == PcProperties.BarrierKind.WATER ? id + "_water" : id;
            case "mine_dispatch_cabinet" -> s.getValue(PcProperties.ALARM) ? id + "_alarm" : id;
            case "headframe" -> s.getValue(PcProperties.TOP) ? id + "_top" : id;
            case "mine_cage" -> s.getValue(BlockStateProperties.OPEN) ? id + "_open" : id;
            case "cupola_furnace", "crucible_furnace" -> s.getValue(BlockStateProperties.LIT) ? id + "_lit" : id;
            case "sand_mold" -> switch (s.getValue(PcProperties.MOLD_STAGE)) {
                case EMPTY -> id;
                case POURED -> id + "_poured";
                case COOLING -> id + "_cooling";
            };
            case "document_lectern" -> s.getValue(PcProperties.HAS_DOCUMENT) ? id + "_document" : id;
            case "industrial_light", "industrial_light_street", "industrial_light_floodlight", "industrial_light_exit" ->
                    s.getValue(BlockStateProperties.LIT) ? id : id + "_off";
            default -> s.hasProperty(PcProperties.HOST) ? hosted(id, s) : id;
        };
    }

    private static String hosted(String id, BlockState s) {
        return switch (s.getValue(PcProperties.HOST)) {
            case STONE -> id;
            case DEEPSLATE -> id + "_deepslate";
            case TUFF -> id + "_tuff";
        };
    }

    /** Предмет блока — модель состояния по умолчанию; многоклеточные уменьшены по наибольшему габариту. */
    private void itemModel(String id, Block block) {
        itemModel(id, modelName(id, block.defaultBlockState()), block);
    }

    private void itemModel(String id, String model, Block block) {
        ItemModelBuilder item = itemModels().withExistingParent(id, modLoc("block/" + model));
        if (!(block instanceof PcBlock pc) || pc.spec().footprint().isSingle()) return;
        int extent = 1;
        for (Footprint.Segment seg : pc.spec().footprint().segments()) {
            extent = Math.max(extent, Math.max(seg.x1() - seg.x0(), Math.max(seg.y1() - seg.y0(), seg.z1() - seg.z0())) + 1);
        }
        float k = 1.0f / extent;
        item.transforms()
                .transform(ItemDisplayContext.GUI).rotation(30, 225, 0).scale(0.625f * k).end()
                .transform(ItemDisplayContext.GROUND).translation(0, 3, 0).scale(0.25f * k).end()
                .transform(ItemDisplayContext.FIXED).scale(0.5f * k).end()
                .transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).rotation(75, 45, 0).translation(0, 2.5f, 0).scale(0.375f * k).end()
                .transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND).rotation(0, 45, 0).scale(0.4f * k).end()
                .transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND).rotation(0, 225, 0).scale(0.4f * k).end();
    }
}

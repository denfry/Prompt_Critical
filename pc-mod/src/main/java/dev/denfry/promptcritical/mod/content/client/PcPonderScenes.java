package dev.denfry.promptcritical.mod.content.client;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.content.PonderLayouts;
import dev.denfry.promptcritical.mod.registry.PcItems;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;

/**
 * Сцены Ponder 28–31 и 36 (11 §11.1.4) по раскладкам {@link PonderLayouts}. Реплики идут строго по порядку текстов
 * сцены: Ponder нумерует их {@code text_1…} в порядке вызовов, переводы пишет datagen.
 */
final class PcPonderScenes {
    private static final int READ = 70;

    private PcPonderScenes() {
    }

    /** Сцена 28: цикл коксования. */
    static void cokeBattery(SceneBuilder scene, SceneBuildingUtil util) {
        Lines t = start(scene, PonderLayouts.COKE, 9);
        Selection battery = util.select().fromTo(1, 1, PonderLayouts.COKE_Z0, 7, 3, PonderLayouts.COKE_Z1);
        scene.world().showSection(battery, Direction.DOWN);
        scene.idle(15);
        t.say(scene, util.vector().blockSurface(new BlockPos(4, 3, 4), Direction.UP));
        scene.overlay().showOutline(PonderPalette.WHITE, "oven", util.select().fromTo(4, 1, 2, 4, 3, 6), READ);
        scene.idle(READ + 10);
        t.say(scene, util.vector().topOf(2, 3, 4));
        scene.idle(READ + 10);
        scene.world().showSection(util.select().fromTo(1, 4, 4, 7, 4, 4), Direction.DOWN);
        scene.idle(10);
        t.say(scene, util.vector().topOf(4, 4, 4));
        scene.effects().emitParticles(util.vector().topOf(4, 4, 4),
                scene.effects().simpleParticleEmitter(ParticleTypes.LARGE_SMOKE, new Vec3(0, 0.05, 0)), 2, 40);
        scene.idle(READ + 10);
        t.say(scene, util.vector().blockSurface(PonderLayouts.COKE_DOOR, Direction.SOUTH));
        scene.world().createItemEntity(util.vector().centerOf(PonderLayouts.COKE_DOOR), new Vec3(0, 0.1, 0.1),
                new ItemStack(PcItems.COKE.get(), 8));
        scene.idle(READ + 10);
        t.say(scene, util.vector().centerOf(PonderLayouts.COKE_DOOR));
        scene.effects().emitParticles(util.vector().centerOf(PonderLayouts.COKE_DOOR),
                scene.effects().simpleParticleEmitter(ParticleTypes.CLOUD, new Vec3(0, 0.1, 0)), 3, 30);
        scene.idle(READ + 10);
        scene.markAsFinished();
    }

    /** Сцена 29: шихта, дутьё и выпуск чугуна. */
    static void blastFurnaceTap(SceneBuilder scene, SceneBuildingUtil util) {
        Lines t = start(scene, PonderLayouts.BLAST_FURNACE, 9);
        scene.scaleSceneView(0.75f);
        scene.world().showSection(util.select().fromTo(2, 1, 2, 6, 7, 6), Direction.DOWN);
        scene.idle(20);
        t.say(scene, util.vector().blockSurface(new BlockPos(2, 5, 4), Direction.WEST));
        scene.idle(READ + 10);
        scene.world().showSection(util.select().position(PonderLayouts.BF_TOP), Direction.DOWN);
        scene.idle(10);
        t.say(scene, util.vector().topOf(PonderLayouts.BF_TOP));
        scene.idle(READ + 10);
        for (BlockPos p : PonderLayouts.BF_TUYERES)
            scene.overlay().showOutline(PonderPalette.RED, p, util.select().position(p), READ);
        t.say(scene, util.vector().centerOf(PonderLayouts.BF_TUYERES[0]));
        scene.idle(READ + 10);
        scene.world().showSection(util.select().fromTo(PonderLayouts.BF_RUNNER[0], PonderLayouts.BF_RUNNER[1]), Direction.DOWN);
        scene.idle(10);
        set(scene, PonderLayouts.BF_TAPHOLE, BlockStateProperties.OPEN, true);
        t.say(scene, util.vector().blockSurface(PonderLayouts.BF_TAPHOLE, Direction.SOUTH));
        for (BlockPos p : PonderLayouts.BF_RUNNER) set(scene, p, PcProperties.FILLED, true);
        scene.effects().emitParticles(util.vector().topOf(PonderLayouts.BF_RUNNER[0]),
                scene.effects().simpleParticleEmitter(ParticleTypes.LAVA, Vec3.ZERO), 1, 60);
        scene.idle(READ + 10);
        t.say(scene, util.vector().topOf(PonderLayouts.BF_RUNNER[1]));
        scene.idle(30);
        set(scene, PonderLayouts.BF_TAPHOLE, BlockStateProperties.OPEN, false);
        for (BlockPos p : PonderLayouts.BF_RUNNER) set(scene, p, PcProperties.FILLED, false);
        scene.idle(READ - 20);
        scene.markAsFinished();
    }

    /** Сцена 30: синхронизация генератора по синхроноскопу. */
    static void synchronization(SceneBuilder scene, SceneBuildingUtil util) {
        Lines t = start(scene, PonderLayouts.SYNC, 7);
        scene.world().showSection(util.select().fromTo(2, 1, 4, 6, 1, 4), Direction.DOWN);
        scene.idle(15);
        t.say(scene, util.vector().topOf(PonderLayouts.SYNC_GENERATOR));
        scene.idle(READ + 10);
        scene.world().showSection(util.select().fromTo(0, 1, 0, 6, 3, 0).add(util.select().position(PonderLayouts.SYNC_SCOPE))
                .add(util.select().position(PonderLayouts.SYNC_CELL)), Direction.DOWN);
        scene.idle(15);
        t.say(scene, util.vector().topOf(PonderLayouts.SYNC_CELL));
        scene.idle(READ + 10);
        t.say(scene, util.vector().blockSurface(PonderLayouts.SYNC_SCOPE, Direction.SOUTH));
        scene.overlay().showOutline(PonderPalette.BLUE, "scope", util.select().position(PonderLayouts.SYNC_SCOPE), READ);
        scene.idle(READ + 10);
        t.say(scene, util.vector().blockSurface(PonderLayouts.SYNC_SCOPE, Direction.SOUTH));
        scene.overlay().showOutline(PonderPalette.GREEN, "scope", util.select().position(PonderLayouts.SYNC_SCOPE), READ);
        scene.idle(READ + 10);
        set(scene, PonderLayouts.SYNC_CELL, BlockStateProperties.OPEN, false);
        t.say(scene, util.vector().topOf(PonderLayouts.SYNC_GENERATOR), PonderPalette.RED);
        scene.idle(READ + 10);
        scene.markAsFinished();
    }

    /** Сцена 31: частота при дефиците мощности и АЧР. */
    static void gridFrequency(SceneBuilder scene, SceneBuildingUtil util) {
        Lines t = start(scene, PonderLayouts.FREQUENCY, 9);
        scene.world().showSection(util.select().fromTo(2, 1, 4, 4, 1, 4), Direction.DOWN);
        scene.idle(10);
        for (BlockPos p : PonderLayouts.FREQ_CONSUMERS) {
            scene.world().showSection(util.select().position(p), Direction.DOWN);
            scene.idle(5);
        }
        t.say(scene, util.vector().topOf(PonderLayouts.FREQ_GENERATOR));
        scene.idle(READ + 10);
        t.say(scene, util.vector().topOf(PonderLayouts.FREQ_GENERATOR), PonderPalette.RED);
        scene.idle(READ + 10);
        scene.overlay().showOutline(PonderPalette.OUTPUT, "ufls", util.select().position(PonderLayouts.FREQ_CONSUMERS[1]), READ);
        t.say(scene, util.vector().topOf(PonderLayouts.FREQ_CONSUMERS[1]));
        scene.idle(READ + 10);
        set(scene, PonderLayouts.FREQ_CONSUMERS[1], BlockStateProperties.LIT, false);
        t.say(scene, util.vector().topOf(PonderLayouts.FREQ_CONSUMERS[1]), PonderPalette.GREEN);
        scene.idle(READ + 10);
        set(scene, PonderLayouts.FREQ_CONSUMERS[1], BlockStateProperties.LIT, true);
        t.say(scene, util.vector().topOf(PonderLayouts.FREQ_CONSUMERS[1]));
        scene.idle(READ + 10);
        scene.markAsFinished();
    }

    /** Сцена 36: релейная лестница с самоподхватом. */
    static void relayLadder(SceneBuilder scene, SceneBuildingUtil util) {
        Lines t = start(scene, PonderLayouts.RELAY, 8);
        scene.world().showSection(util.select().fromTo(0, 1, 0, 7, 3, 1), Direction.DOWN);
        scene.idle(15);
        t.say(scene, util.vector().blockSurface(PonderLayouts.RL_PSU, Direction.SOUTH));
        scene.idle(READ + 10);
        t.say(scene, util.vector().blockSurface(PonderLayouts.RL_START, Direction.SOUTH));
        scene.idle(READ + 10);
        set(scene, PonderLayouts.RL_START, BlockStateProperties.LIT, true);
        set(scene, PonderLayouts.RL_K1, PcProperties.PICKED, true);
        t.say(scene, util.vector().blockSurface(PonderLayouts.RL_K1, Direction.SOUTH));
        scene.idle(READ + 10);
        set(scene, PonderLayouts.RL_START, BlockStateProperties.LIT, false);
        t.say(scene, util.vector().blockSurface(PonderLayouts.RL_K1, Direction.SOUTH), PonderPalette.GREEN);
        scene.idle(READ + 10);
        set(scene, PonderLayouts.RL_K2, PcProperties.PICKED, true);
        set(scene, PonderLayouts.RL_LAMP, BlockStateProperties.LIT, true);
        t.say(scene, util.vector().blockSurface(PonderLayouts.RL_LAMP, Direction.SOUTH));
        scene.idle(READ + 10);
        for (BlockPos p : new BlockPos[] {PonderLayouts.RL_K1, PonderLayouts.RL_K2}) set(scene, p, PcProperties.PICKED, false);
        set(scene, PonderLayouts.RL_LAMP, BlockStateProperties.LIT, false);
        t.say(scene, util.vector().blockSurface(PonderLayouts.RL_STOP, Direction.SOUTH), PonderPalette.RED);
        scene.idle(READ + 10);
        scene.markAsFinished();
    }

    // ---------------------------------------------------------------- общее

    private static Lines start(SceneBuilder scene, String id, int plate) {
        PonderLayouts.Texts texts = PonderLayouts.TEXTS.get(id);
        scene.title(id, texts.header()[0]);
        scene.configureBasePlate(0, 0, plate);
        scene.showBasePlate();
        scene.idle(10);
        return new Lines(texts);
    }

    /** Свойство блока в сцене, если оно у блока есть (заглушки моделей могут его не иметь). */
    private static void set(SceneBuilder scene, BlockPos pos, BooleanProperty prop, boolean value) {
        scene.world().modifyBlock(pos, s -> s.hasProperty(prop) ? s.setValue(prop, value) : s, false);
    }

    /** Реплики сцены по порядку. */
    private static final class Lines {
        private final PonderLayouts.Texts texts;
        private int next = 1;

        Lines(PonderLayouts.Texts texts) {
            this.texts = texts;
        }

        void say(SceneBuilder scene, Vec3 at) {
            say(scene, at, PonderPalette.WHITE);
        }

        void say(SceneBuilder scene, Vec3 at, PonderPalette color) {
            scene.overlay().showText(READ).text(texts.en(next++)).colored(color).pointAt(at).placeNearTarget().attachKeyFrame();
        }
    }
}

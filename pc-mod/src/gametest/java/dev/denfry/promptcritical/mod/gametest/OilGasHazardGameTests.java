package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.hazard.GasStorageBulletBlockEntity;
import dev.denfry.promptcritical.mod.hazard.HazardService;
import dev.denfry.promptcritical.mod.hazard.OilSkimmerBlockEntity;
import dev.denfry.promptcritical.mod.hazard.OilSpills;
import dev.denfry.promptcritical.mod.ops.FireService;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.hazard.Flammable;
import dev.denfry.promptcritical.sim.ops.FireSuppression.FireClass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Аварии нефтегаза (09 §9.11, 14 §14.5): ёмкость СУГ греется в огне и рвётся (BLEVE), облако утечки видит
 * газоанализатор и поджигает пламя, разлив на воде раскладывается плёнкой и собирается скиммером, разлив на суше горит
 * как пролив класса B.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OilGasHazardGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos BULLET = new BlockPos(6, 1, 5);

    private OilGasHazardGameTests() {
    }

    private static GasStorageBulletBlockEntity bullet(GameTestHelper h, int litres) {
        h.setBlock(BULLET, PcBlocks.GAS_STORAGE_BULLET.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        GasStorageBulletBlockEntity b = (GasStorageBulletBlockEntity) h.getBlockEntity(BULLET);
        b.propane().fill(new FluidStack(PcFluids.PROPANE.source.get(), litres), IFluidHandler.FluidAction.EXECUTE);
        return b;
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void anEngulfedBulletHeatsUp(GameTestHelper h) {
        GasStorageBulletBlockEntity b = bullet(h, 20_000);
        for (int x = 4; x <= 8; x++) {
            h.setBlock(new BlockPos(x, 0, 3), Blocks.NETHERRACK);
            h.setBlock(new BlockPos(x, 1, 3), Blocks.FIRE);
        }
        h.runAfterDelay(120, () -> {
            h.assertTrue(b.fireKwM2() == HazardService.ENGULFMENT_KW_M2, "fire next to the bullet engulfs it: " + b.fireKwM2());
            h.assertTrue(b.vessel().wallK() > 296, "the vapour-space wall heats about 1 K a second: " + b.vessel().wallK());
            h.assertTrue(b.vessel().liquidK() > 293.2, "the liquid heats through the wetted wall: " + b.vessel().liquidK());
            h.succeed();
        });
    }

    @GameTest(template = LARGE, timeoutTicks = 200, batch = "hazard_blast_alone")
    public static void aBulletWithAWallAtSixHundredDegreesBleves(GameTestHelper h) {
        GasStorageBulletBlockEntity b = bullet(h, 2_000);
        // клапан на уставке, стенка над жидкостью — 600 °C: k_y = 0,47 < 2/3 — сосуд течёт
        b.heatLiquid(330);
        b.heatWall(873);
        h.succeedWhen(() -> h.assertBlockNotPresent(PcBlocks.GAS_STORAGE_BULLET.get(), BULLET));
    }

    @GameTest(template = LARGE, timeoutTicks = 1600, batch = "hazard_blast_alone")
    public static void aGasCloudShowsOnTheDetectorAndBurnsBackWhenLit(GameTestHelper h) {
        String id = "gametest_leak@" + h.absolutePos(BULLET).toShortString();
        BlockPos src = h.absolutePos(new BlockPos(6, 2, 6));
        h.onEachTick(() -> {
            // источник обновляет утечку каждую секунду
            if (h.getLevel().getGameTime() % 20 == 0) HazardService.vent(h.getLevel(), id, src, Flammable.PROPANE, 5, 0, false);
        });
        h.startSequence()
                .thenExecuteAfter(25, () -> HazardService.holdIgnition(h.getLevel(), id))
                .thenWaitUntil(() -> {
                    var r = HazardService.release(h.getLevel(), id);
                    h.assertTrue(r != null && r.cloud().flammableKg() > 0, "a flammable cloud forms downwind");
                    // по оси облака (на центр горючей части) где-то не меньше НКПР: газоанализатор там покажет ≥ 100 %
                    var c = r.cloud();
                    double len = Math.hypot(c.centroidEast(), c.centroidSouth()), max = 0;
                    for (int d = 1; d <= 2 * len + 2; d++)
                        max = Math.max(max, HazardService.lelFraction(h.getLevel(), src.offset((int) Math.round(c.centroidEast() / len * d), 0,
                                (int) Math.round(c.centroidSouth() / len * d))));
                    h.assertTrue(max >= 1, "the detector reads at least the LEL in the cloud: " + max);
                })
                .thenExecute(() -> HazardService.ignite(h.getLevel(), id))
                .thenExecute(() -> {
                    var r = HazardService.release(h.getLevel(), id);
                    h.assertTrue(r.burning(), "the cloud burnt back to the leak: it is a jet fire now");
                    h.assertTrue(r.cloud().flammableKg() == 0, "no cloud left after the burn");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void aSpillOnWaterSpreadsAndTheSkimmerRecoversIt(GameTestHelper h) {
        for (int x = 1; x <= 10; x++)
            for (int z = 1; z <= 10; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.WATER);
        h.setBlock(new BlockPos(0, 2, 0), PcBlocks.OIL_SKIMMER.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        OilSpills.Spill[] spill = new OilSpills.Spill[1];
        h.startSequence()
                .thenExecute(() -> {
                    spill[0] = OilSpills.spill(h.getLevel(), h.absolutePos(new BlockPos(5, 1, 5)), "promptcritical:crude_oil", 850, 2000);
                    h.assertTrue(spill[0] != null && spill[0].onWater(), "a spill into the pool is on water");
                    OilSpills.age(h.getLevel(), spill[0], 10);
                    h.assertTrue(!spill[0].blocks().isEmpty() && spill[0].blocks().size() <= 100, "a slick lies on the pool: " + spill[0].blocks().size());
                    for (BlockPos p : spill[0].blocks())
                        h.assertTrue(h.getLevel().getBlockState(p.below()).is(Blocks.WATER), "slick only over water: " + p);
                    h.assertTrue(spill[0].thicknessM() > 0.005, "the pool walls the slick in: " + spill[0].thicknessM());
                    ((OilSkimmerBlockEntity) h.getBlockEntity(new BlockPos(0, 2, 0))).setBenchRpm(64);
                })
                .thenIdle(100)
                .thenExecute(() -> {
                    OilSkimmerBlockEntity s = (OilSkimmerBlockEntity) h.getBlockEntity(new BlockPos(0, 2, 0));
                    h.assertTrue(s.tank().amount() > 5, "the skimmer recovered emulsion: " + s.tank().amount());
                    h.assertTrue(spill[0].model().recoveredKg() > 0, "the spill lost what was recovered");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 100)
    public static void aSpillOnLandIsABurningPoolWhenLit(GameTestHelper h) {
        for (int x = 0; x <= 11; x++)
            for (int z = 0; z <= 11; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        OilSpills.Spill s = OilSpills.spill(h.getLevel(), h.absolutePos(new BlockPos(6, 1, 6)), "promptcritical:crude_oil", 850, 5000);
        h.assertTrue(s != null && !s.onWater() && s.blocks().size() == 5, "5 m³ on land is five blocks of crude: " + (s == null ? 0 : s.blocks().size()));
        BlockPos oil = s.blocks().getFirst();
        h.assertTrue(h.getLevel().getBlockState(oil).is(PcBlocks.CRUDE_OIL.get()), "crude oil lies on the ground");
        h.assertTrue(FireService.classAt(h.getLevel(), oil.above()) == FireClass.B, "fire over spilt crude is a class B pool fire");
        h.succeed();
    }
}

package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.item.CoalAssayData;
import dev.denfry.promptcritical.mod.item.OreAssayData;
import dev.denfry.promptcritical.mod.mine.MineScanner;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.worldgen.VeinFields;
import dev.denfry.promptcritical.mod.worldgen.VeinOreFeature;
import dev.denfry.promptcritical.sim.mining.ExplosionFront;
import dev.denfry.promptcritical.sim.mining.MineGas;
import dev.denfry.promptcritical.sim.mining.MineNetwork;
import dev.denfry.promptcritical.sim.mining.MineTopology;
import dev.denfry.promptcritical.sim.mining.OreGrade;
import dev.denfry.promptcritical.sim.mining.VeinBody;
import dev.denfry.promptcritical.sim.mining.VeinCatalog;
import dev.denfry.promptcritical.sim.mining.VeinField;
import dev.denfry.promptcritical.sim.mining.VeinHit;
import dev.denfry.promptcritical.sim.mining.VeinType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Шахта в мире (этап 7 M2, 08 §8.1–8.2): взрыв метана с остановкой на заслоне (критерий вехи), проветривание
 * вентилятором и дверью, канарейка в угарном газе, рудные блоки всех ролей жил, анализ в луте.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MineGameTests {
    private static final String LONG = "gametest/empty_40x10x10";
    private static final String SMALL = "gametest/empty_3x3x3";
    /** Штрек 2×1 вдоль x на y = 2…3, z = 5 в каменном массиве y = 1…5, z = 3…7. */
    private static final int Z = 5;

    private MineGameTests() {
    }

    private static SimLevelBinding binding(GameTestHelper h) {
        SimLevelBinding b = SimBindings.get(h.getLevel());
        if (b == null) throw new IllegalStateException("no simulation binding for the test level");
        return b;
    }

    /** Каменный массив со штреком от x0 до x1. */
    private static void drift(GameTestHelper h, int x0, int x1) {
        for (int x = 0; x < 40; x++)
            for (int y = 1; y <= 5; y++)
                for (int z = 3; z <= 7; z++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());
        for (int x = x0; x <= x1; x++) {
            h.setBlock(new BlockPos(x, 2, Z), Blocks.AIR.defaultBlockState());
            h.setBlock(new BlockPos(x, 3, Z), Blocks.AIR.defaultBlockState());
        }
    }

    private static String scanInfo(GameTestHelper h, MineNetwork n) {
        MineScanner.Scan s = binding(h).mines().scanOf(n);
        if (s == null) return "none";
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        StringBuilder sb = new StringBuilder("air=" + s.airBlocks + " box=" + (s.bounds.minX() - o.getX()) + ".." + (s.bounds.maxX() - o.getX())
                + "," + (s.bounds.minY() - o.getY()) + ".." + (s.bounds.maxY() - o.getY()) + "," + (s.bounds.minZ() - o.getZ()) + ".."
                + (s.bounds.maxZ() - o.getZ()) + " anchors");
        for (long a : n.anchors()) sb.append(' ').append(BlockPos.of(a).subtract(o).toShortString());
        return sb.toString();
    }

    private static MineNetwork mine(GameTestHelper h, BlockPos rel) {
        SimLevelBinding b = binding(h);
        MineNetwork n = b.mines().mineAt(h.absolutePos(rel));
        if (n == null || !n.ready()) {
            StringBuilder sb = new StringBuilder("mine traced at " + rel.toShortString() + "; near:");
            BlockPos o = h.absolutePos(BlockPos.ZERO);
            for (MineNetwork m : b.mines().sim().networks()) {
                MineScanner.Scan s = b.mines().scanOf(m);
                boolean near = false;
                for (long a : m.anchors()) near |= BlockPos.of(a).distManhattan(o) < 120;
                if (!near) continue;
                sb.append(" #").append(m.id()).append(" anchors=").append(m.anchors().length)
                        .append(" air=").append(s == null ? -1 : s.airBlocks).append(" fans=")
                        .append(m.ready() ? m.topology().fanCount() : -1);
                for (long a : m.anchors()) sb.append(' ').append(BlockPos.of(a).subtract(o).toShortString());
            }
            h.fail(sb.toString());
        }
        return n;
    }

    // --- критерий M2: взрыв метана --------------------------------------------------------------------------

    /**
     * T8.8 в мире: штрек с метаном 9 %, факел у забоя, заслон в середине. Фронт идёт по узлам от факела и гаснет на
     * узле заслона; узлы за заслоном целы; в сгоревших — рудничный газ; заявки на разрушение — только по сгоревшим.
     */
    @GameTest(template = LONG, timeoutTicks = 600)
    public static void methaneExplosionStopsAtBarrier(GameTestHelper h) {
        drift(h, 1, 38);
        // два блока заслона: 1000 кг инертного на 2 м² сечения штрека (нужно ≥ 400 кг/м²)
        BlockPos barrier = new BlockPos(24, 3, Z);
        h.setBlock(barrier, PcBlocks.EXPLOSION_BARRIER.get().defaultBlockState());
        h.setBlock(barrier.below(), PcBlocks.EXPLOSION_BARRIER.get().defaultBlockState());
        BlockPos torch = new BlockPos(2, 2, Z);
        SimLevelBinding b = binding(h);
        MineNetwork[] mine = new MineNetwork[1];
        int[] requests = new int[1];
        h.startSequence()
                .thenWaitUntil(() -> {
                    mine[0] = mine(h, new BlockPos(10, 2, Z));
                    h.assertTrue(mine[0].topology().nodeCount() >= 5, "drift split into nodes");
                })
                .thenExecute(() -> {
                    MineNetwork n = mine[0];
                    for (int i = 1; i < n.topology().nodeCount(); i++) n.field().setConcentration(i, MineGas.CH4, 0.09);
                    requests[0] = b.mines().blastRequests();
                    h.setBlock(torch, Blocks.TORCH.defaultBlockState());
                })
                .thenWaitUntil(() -> {
                    MineNetwork n = b.mines().mineAt(h.absolutePos(new BlockPos(10, 2, Z)));
                    h.assertTrue(n != null && n.explosions() > 0 && !n.exploding(), "explosion ran its course");
                    mine[0] = n;
                })
                .thenExecute(() -> {
                    MineNetwork n = mine[0];
                    ExplosionFront f = n.lastFront();
                    MineTopology ft = n.lastFrontTopology();
                    long originKey = h.absolutePos(new BlockPos(2, 3, Z)).asLong(), beyondKey = h.absolutePos(new BlockPos(37, 2, Z)).asLong();
                    int fo = ft.nodeOf(originKey), fb = ft.nodeOf(h.absolutePos(barrier).asLong()), fy = ft.nodeOf(beyondKey);
                    h.assertTrue(fo > 0 && fb > 0 && fy > 0, "drift nodes in the front graph");
                    h.assertTrue(f.burned(fo), "ignition node burned");
                    h.assertFalse(f.burned(fb), "barrier node protected");
                    h.assertFalse(f.burned(fy), "nodes beyond the barrier intact");
                    int origin = n.nodeOf(originKey), beyond = n.nodeOf(beyondKey);
                    h.assertTrue(f.quenchedByBarriers() >= 1, "front quenched by the barrier");
                    h.assertTrue(f.burnedCount() >= 2, "front travelled along the drift: " + f.burnedCount());
                    h.assertTrue(n.field().concentration(origin, MineGas.CH4) == 0, "methane burnt out");
                    h.assertTrue(n.field().concentration(origin, MineGas.CO) >= 0.005 && n.field().concentration(origin, MineGas.O2) < 0.1, "afterdamp: CO, O2 < 10 %");
                    h.assertTrue(n.field().concentration(beyond, MineGas.CH4) > 0.08, "methane beyond the barrier remains");
                    h.assertTrue(b.mines().blastRequests() - requests[0] == f.burnedCount(),
                            "one damage request per burned node");
                })
                .thenSucceed();
    }

    // --- проветривание --------------------------------------------------------------------------------------

    /**
     * Штрек между двумя стволами: открытый (воздухоподающий) и под вентилятором главного проветривания. Вентилятор от
     * красного камня гонит воздух; закрытая дверь в штреке режет расход больше чем вдвое.
     */
    @GameTest(template = LONG, timeoutTicks = 600)
    public static void fanVentilatesAndDoorThrottles(GameTestHelper h) {
        drift(h, 2, 37);
        for (int y = 4; y <= 5; y++) {
            h.setBlock(new BlockPos(2, y, Z), Blocks.AIR.defaultBlockState());
            h.setBlock(new BlockPos(37, y, Z), Blocks.AIR.defaultBlockState());
        }
        h.setBlock(new BlockPos(37, 7, Z), PcBlocks.MAIN_MINE_FAN.get().defaultBlockState());
        h.setBlock(new BlockPos(37, 9, Z), Blocks.REDSTONE_BLOCK.defaultBlockState());
        BlockPos door = new BlockPos(20, 2, Z);
        h.setBlock(door, PcBlocks.VENTILATION_DOOR.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.EAST));
        double[] closed = new double[1];
        h.startSequence()
                .thenWaitUntil(() -> {
                    MineNetwork n = mine(h, new BlockPos(10, 2, Z));
                    h.assertTrue(n.topology().fanCount() == 1 && n.topology().doorCount() == 1, "fan and door in the graph");
                    MineTopology t = n.topology();
                    int fb = t.fanBranch(0);
                    h.assertTrue(n.surfaceAirflowM3s() > 0.5, "fan draws air with the door shut: intake " + n.surfaceAirflowM3s()
                            + ", fan rpm " + n.ventilation().fanSpeed(0) + ", fan q " + n.ventilation().flow(fb) + " h "
                            + n.ventilation().fanPressurePa(0) + ", loops " + t.loopCount() + ", nodes " + t.nodeCount()
                            + ", branches " + t.branchCount() + ", it " + n.ventilation().iterations() + ", corr " + n.ventilation().lastCorrection()
                            + ", scan " + scanInfo(h, n));
                    h.assertTrue(h.getBlockState(new BlockPos(37, 7, Z)).getValue(PcProperties.RUNNING), "fan running");
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    closed[0] = mine(h, new BlockPos(10, 2, Z)).surfaceAirflowM3s();
                    BlockState s = h.getBlockState(door);
                    h.setBlock(door, s.setValue(BlockStateProperties.OPEN, true));
                })
                .thenWaitUntil(() -> {
                    double open = mine(h, new BlockPos(10, 2, Z)).surfaceAirflowM3s();
                    h.assertTrue(open > 2 * closed[0], "open door: " + open + " vs shut " + closed[0] + " m3/s; scan "
                            + scanInfo(h, mine(h, new BlockPos(10, 2, Z))));
                })
                .thenSucceed();
    }

    // --- канарейка ------------------------------------------------------------------------------------------

    /** Канарейка в клетке падает в угарном газе (CO 1 %: B = 0,6 за ≈ 7 с) — раньше людей. */
    @GameTest(template = LONG, timeoutTicks = 600)
    public static void canaryFaintsInCarbonMonoxide(GameTestHelper h) {
        drift(h, 1, 20);
        h.setBlock(new BlockPos(18, 3, Z), PcBlocks.EXPLOSION_BARRIER.get().defaultBlockState());
        BlockPos cage = new BlockPos(5, 2, Z);
        h.setBlock(cage, PcBlocks.CANARY_CAGE.get().defaultBlockState().setValue(PcProperties.BIRD, PcProperties.Bird.ALIVE));
        h.startSequence()
                .thenWaitUntil(() -> mine(h, cage))
                .thenExecute(() -> {
                    MineNetwork n = mine(h, cage);
                    for (int i = 1; i < n.topology().nodeCount(); i++) n.field().setConcentration(i, MineGas.CO, 0.01);
                })
                .thenWaitUntil(() -> h.assertTrue(h.getBlockState(cage).getValue(PcProperties.BIRD) == PcProperties.Bird.FAINTED,
                        "canary fainted"))
                .thenSucceed();
    }

    // --- жилы и анализ --------------------------------------------------------------------------------------

    /** Каждая роль каждой жилы каталога M2 ставит зарегистрированный блок во всех трёх породах. */
    @GameTest(template = SMALL)
    public static void everyVeinRoleHasABlock(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        VeinField field = VeinFields.get(level.getSeed());
        Set<String> seen = new HashSet<>();
        VeinField.BiomeProbe any = null;
        for (int cx = -40; cx <= 40 && seen.size() < 20; cx++)
            for (int cz = -40; cz <= 40; cz++) {
                VeinBody body = field.body(cx, cz, any);
                if (body == null) continue;
                VeinField.Sampler s = field.sampler(body.minX(), body.minZ(), body.maxX(), body.maxZ(), any);
                for (int k = 0; k < 400; k++) {
                    int x = body.minX() + (k * 7) % Math.max(1, body.maxX() - body.minX() + 1);
                    int y = body.minY() + (k * 3) % Math.max(1, body.maxY() - body.minY() + 1);
                    int z = body.minZ() + (k * 11) % Math.max(1, body.maxZ() - body.minZ() + 1);
                    VeinHit hit = s.sample(x, y, z);
                    if (hit == null || !seen.add(hit.mineral())) continue;
                    for (PcProperties.OreHost host : PcProperties.OreHost.values()) {
                        BlockState st = VeinOreFeature.oreState(hit, host, field, x, y, z);
                        h.assertTrue(st != null && !st.isAir(), hit.mineral() + " in " + host + " has a block");
                    }
                }
            }
        Set<String> expected = new HashSet<>();
        for (VeinType t : VeinCatalog.M2) for (OreGrade g : t.roles()) if (g != null) expected.add(g.mineral());
        h.assertTrue(seen.size() >= expected.size() / 2, "sampled most minerals: " + seen);
        h.succeed();
    }

    /** Уголь пласта несёт {@code coal_assay} точки добычи, руда — {@code ore_assay}; одинаковые куски стекуются. */
    @GameTest(template = SMALL)
    public static void minedOreCarriesAssay(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos coal = new BlockPos(1, 1, 1), ore = new BlockPos(0, 1, 1);
        h.setBlock(coal, PcBlocks.COAL_SEAM.get().defaultBlockState());
        h.setBlock(ore, PcBlocks.MAGNETITE_ORE.get().defaultBlockState());
        BlockPos ca = h.absolutePos(coal), oa = h.absolutePos(ore);
        List<ItemStack> coalDrops = Block.getDrops(level.getBlockState(ca), level, ca, null);
        List<ItemStack> oreDrops = Block.getDrops(level.getBlockState(oa), level, oa, null);
        h.assertTrue(!coalDrops.isEmpty() && coalDrops.get(0).is(PcItems.RAW_COAL.get()), "raw coal dropped");
        CoalAssayData expected = CoalAssayData.of(VeinFields.get(level.getSeed()).coalAssay(ca.getX(), ca.getY(), ca.getZ()));
        h.assertTrue(expected.equals(coalDrops.get(0).get(PcDataComponents.COAL_ASSAY.get())), "coal_assay of the mining point");
        h.assertTrue(!oreDrops.isEmpty() && oreDrops.get(0).is(PcItems.RAW_MAGNETITE.get()), "raw magnetite dropped");
        OreAssayData a = oreDrops.get(0).get(PcDataComponents.ORE_ASSAY.get());
        h.assertTrue(a != null && a.mineral().equals("magnetite_ore") && a.toAssay().gradeFrac() > 0.25, "ore_assay present");
        List<ItemStack> again = Block.getDrops(level.getBlockState(ca), level, ca, null);
        h.assertTrue(ItemStack.isSameItemSameComponents(coalDrops.get(0), again.get(0)), "same assay stacks");
        h.succeed();
    }

    /** Заливка без якорей ничего не находит; одиночная крепь в штреке заводит шахту. */
    @GameTest(template = LONG, timeoutTicks = 200)
    public static void anchorStartsMineAndRemovalEndsIt(GameTestHelper h) {
        drift(h, 1, 12);
        BlockPos anchor = new BlockPos(6, 3, Z);
        h.setBlock(anchor, PcBlocks.EXPLOSION_BARRIER.get().defaultBlockState());
        SimLevelBinding b = binding(h);
        h.startSequence()
                .thenWaitUntil(() -> {
                    MineNetwork n = mine(h, new BlockPos(2, 2, Z));
                    MineScanner.Scan s = b.mines().scanOf(n);
                    h.assertTrue(s != null && s.airBlocks == 24 && !s.truncated, "drift volume traced: " + (s == null ? -1 : s.airBlocks));
                    MineTopology t = n.topology();
                    h.assertTrue(t.nodeOf(h.absolutePos(new BlockPos(2, 2, Z)).asLong()) > 0, "drift assigned to nodes");
                })
                .thenExecute(() -> h.setBlock(anchor, Blocks.STONE.defaultBlockState()))
                .thenWaitUntil(() -> h.assertTrue(b.mines().mineAt(h.absolutePos(new BlockPos(2, 2, Z))) == null, "mine dropped"))
                .thenSucceed();
    }
}

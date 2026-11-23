package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.client.whole.WholeModelMath;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.site.ConstructionSiteBlockEntity;
import dev.denfry.promptcritical.mod.site.SitePlans;
import dev.denfry.promptcritical.mod.site.SiteRegistry;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Машины реального масштаба (план real-scale pilot): раскладка объёма на сегменты, поворот цельной модели,
 * стройплощадка — отказ на занятом месте и возведение компенсатора давления по этапам.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RealScaleGameTests {
    private static final String TALL = "gametest/empty_12x24x12";

    private RealScaleGameTests() {}

    /** Объём 5×20×5 раскладывается на сегменты без пропусков и наложений, контроллер — сегмент 0. */
    @GameTest(template = "gametest/empty_12x12x12", timeoutTicks = 20)
    public static void tiledFootprintCoversTheVolumeOnce(GameTestHelper h) {
        Footprint fp = Footprint.tiled(-2, 2, 0, 19, -2, 2);
        h.assertTrue(fp.segments().getFirst().ax() == 0 && fp.segments().getFirst().ay() == 0 && fp.segments().getFirst().az() == 0,
                "segment 0 is anchored at the controller");
        Set<BlockPos> seen = new HashSet<>();
        for (Footprint.Cell c : fp.cells(BlockPos.ZERO, Direction.NORTH)) h.assertTrue(seen.add(c.pos()), "cell twice: " + c.pos());
        h.assertTrue(seen.size() == 5 * 20 * 5, "volume covered, got " + seen.size());
        for (Footprint.Segment s : fp.segments())
            h.assertTrue(s.x0() <= 0 && s.x1() >= 0 && s.y0() <= 0 && s.y1() >= 0 && s.z0() <= 0 && s.z1() >= 0,
                    "every segment anchor is one of its own cells: " + s);
        double[] r = WholeModelMath.rotate(new double[] {3, 0, 1}, Direction.EAST);
        h.assertTrue(Math.abs(r[0] - 0) < 1e-9 && Math.abs(r[2] - 3) < 1e-9, "east rotation of a model point");
        h.succeed();
    }

    private static ConstructionSiteBlockEntity site(GameTestHelper h, BlockPos p) {
        return (ConstructionSiteBlockEntity) h.getBlockEntity(p);
    }

    /** Площадка с основанием: каменная плита 5×5 под фундаментом. */
    private static void placeSite(GameTestHelper h, BlockPos p) {
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++) h.setBlock(p.offset(x, -1, z), Blocks.STONE);
        h.setBlock(p, SiteRegistry.SITE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    /** Площадка не принимает проект, если в объёме машины стоит чужой блок, и называет его. */
    @GameTest(template = TALL, timeoutTicks = 40)
    public static void siteRefusesAnObstructedFootprint(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 1, 5);
        placeSite(h, p);
        h.setBlock(p.above(5), Blocks.STONE);
        var why = site(h, p).accept("vver_pressurizer");
        h.assertTrue(why != null, "obstructed site refused");
        h.assertTrue(site(h, p).plan() == null, "no plan accepted");
        h.setBlock(p.above(5), Blocks.AIR);
        var w = site(h, p).accept("vver_pressurizer");
        h.assertTrue(w == null, "free site accepted: " + (w == null ? "" : w.getString()));
        h.succeed();
    }

    /** Площадка ждёт материалы, по этапам возводит компенсатор давления и уступает место готовой машине. */
    @GameTest(template = TALL, timeoutTicks = 1400)
    public static void siteErectsTheUnitAfterAllStages(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 1, 5);
        placeSite(h, p);
        h.startSequence()
                .thenExecute(() -> {
                    var w = site(h, p).accept("vver_pressurizer");
                    h.assertTrue(w == null, "plan accepted: " + (w == null ? "" : w.getString()));
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    h.assertTrue(site(h, p).stage() == 0 && site(h, p).erectLeft() == 0, "waits for materials");
                    SitePlans.Plan plan = site(h, p).plan();
                    for (SitePlans.Stage st : plan.stages())
                        for (SitePlans.Need n : st.needs()) {
                            ItemStack s = new ItemStack(n.item().get(), n.count());
                            for (int i = 0; i < ConstructionSiteBlockEntity.SLOTS && !s.isEmpty(); i++) s = site(h, p).items().insertItem(i, s, false);
                        }
                })
                .thenWaitUntil(() -> h.assertBlockPresent(PcBlocks.VVER_PRESSURIZER.get(), p))
                .thenExecute(() -> h.assertBlockPresent(PcBlocks.VVER_PRESSURIZER.get(), p.above(3)))
                .thenSucceed();
    }

    /**
     * Кипящий реактор BWR-240 реального масштаба: площадка принимает его чертёж на свободном основании 5×5, а готовый
     * реактор — блок того же типа BE, что и старая серия, со своей цельной моделью; старый BWR по-прежнему работает.
     */
    @GameTest(template = TALL, timeoutTicks = 40)
    public static void bwr240SiteAcceptsAndLegacyBwrStillWorks(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 1, 5);
        placeSite(h, p);
        var w = site(h, p).accept("bwr240_reactor");
        h.assertTrue(w == null, "BWR-240 plan accepted: " + (w == null ? "" : w.getString()));
        h.assertTrue(site(h, p).plan().stages().size() == 4, "four erection stages");
        h.assertTrue(PcBlocks.FP_BWR240_REACTOR.cells(BlockPos.ZERO, Direction.NORTH).size() == 5 * 14 * 5, "5x14x5 footprint");
        h.assertTrue(PcBlocks.isWholeModel(PcBlocks.BWR240_REACTOR.get()) && PcBlocks.legacy().contains(PcBlocks.BWR_REACTOR),
                "BWR-240 is drawn whole, the old BWR is legacy");
        BlockPos old = new BlockPos(1, 1, 1);
        h.setBlock(old, PcBlocks.BWR_REACTOR.get().defaultBlockState());
        h.assertTrue(h.getBlockEntity(old) instanceof dev.denfry.promptcritical.mod.npp.BwrReactorBlockEntity b && b.wholeModelId() == null,
                "legacy BWR keeps its block entity and no whole model");
        h.succeed();
    }

    /**
     * CANDU 6 реального масштаба: у площадки свой проект в четыре этапа, сборка — блок того же типа BE, что и
     * старая серия; машина перегрузки на модели доходит до узла решётки 380 каналов и возвращается на стоянку.
     */
    @GameTest(template = TALL, timeoutTicks = 40)
    public static void candu6PlanAndFuellingMachineOnTheRealLattice(GameTestHelper h) {
        SitePlans.Plan plan = SitePlans.get("candu6_reactor");
        h.assertTrue(plan != null && plan.stages().size() == 4 && plan.unit().get() == PcBlocks.CANDU6_REACTOR.get(), "CANDU 6 site plan");
        h.assertTrue(PcBlocks.FP_CANDU6_REACTOR.cells(BlockPos.ZERO, Direction.NORTH).size() == 9 * 13 * 15, "9x13x15 footprint");
        h.assertTrue(PcBlocks.isWholeModel(PcBlocks.CANDU6_REACTOR.get()) && PcBlocks.legacy().contains(PcBlocks.CANDU_REACTOR),
                "CANDU 6 is drawn whole, the old CANDU is legacy");
        double[] home = dev.denfry.promptcritical.mod.npp.CanduFuelling.offsetReal(-1, 0);
        double[] at = dev.denfry.promptcritical.mod.npp.CanduFuelling.offsetReal(0, dev.denfry.promptcritical.mod.npp.CanduFuelling.TRAVEL + 40);
        double r = Math.hypot(at[0], at[1]) / dev.denfry.promptcritical.mod.npp.CanduFuelling.REAL_PITCH;
        h.assertTrue(home[0] == dev.denfry.promptcritical.mod.npp.CanduFuelling.REAL_HOME_X && at[2] > 0 && r < 11,
                "fuelling machine latched on a channel inside the 380 lattice, r = " + r);
        BlockPos old = new BlockPos(1, 1, 1);
        h.setBlock(old, PcBlocks.CANDU_REACTOR.get().defaultBlockState());
        h.assertTrue(h.getBlockEntity(old) instanceof dev.denfry.promptcritical.mod.npp.CanduReactorBlockEntity b && b.wholeModelId() == null,
                "legacy CANDU keeps its block entity and no whole model");
        h.succeed();
    }

    /** БН-600 реального масштаба: свой проект стройки, объём 15×19×15, старый натриевый реактор — в старой серии и работает. */
    @GameTest(template = "gametest/empty_12x12x12", timeoutTicks = 20)
    public static void bn600PlanAndLegacySfrStillWorks(GameTestHelper h) {
        SitePlans.Plan plan = SitePlans.get("bn600_reactor");
        h.assertTrue(plan != null && plan.stages().size() == 4 && plan.unit().get() == PcBlocks.BN600_REACTOR.get(), "BN-600 site plan");
        h.assertTrue(PcBlocks.FP_BN600_REACTOR.cells(BlockPos.ZERO, Direction.NORTH).size() == 15 * 19 * 15, "15x19x15 footprint");
        h.assertTrue(PcBlocks.isWholeModel(PcBlocks.BN600_REACTOR.get()) && PcBlocks.legacy().contains(PcBlocks.SFR_REACTOR),
                "BN-600 is drawn whole, the old SFR is legacy");
        BlockPos old = new BlockPos(1, 1, 1);
        h.setBlock(old, PcBlocks.SFR_REACTOR.get().defaultBlockState());
        h.assertTrue(h.getBlockEntity(old) instanceof dev.denfry.promptcritical.mod.npp.SfrReactorBlockEntity b && b.wholeModelId() == null,
                "legacy SFR keeps its block entity and no whole model");
        h.succeed();
    }

    /**
     * Клетки машины реального масштаба твёрдые только там, где их занимает модель: у поставленного компенсатора давления
     * угол коробки 5×18×5 проходим, ось сосуда — полный блок; поворот клеток совпадает с поворотом отрисовки.
     */
    @GameTest(template = TALL, timeoutTicks = 20)
    public static void wholeModelCellsFollowTheModel(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 1, 5);
        h.setBlock(p, PcBlocks.VVER_PRESSURIZER.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        BlockPos corner = h.absolutePos(p.offset(-2, 5, -2)), axis = h.absolutePos(p.offset(0, 5, 0));
        var level = h.getLevel();
        h.assertTrue(!level.getBlockState(corner).isAir(), "the corner cell is part of the unit");
        h.assertTrue(level.getBlockState(corner).getCollisionShape(level, corner).isEmpty(), "empty corner of the box is walkable");
        h.assertTrue(level.getBlockState(axis).getCollisionShape(level, axis).bounds().getYsize() == 1, "the vessel cell is solid");
        for (Direction f : Direction.Plane.HORIZONTAL)
            for (double[] q : new double[][] {{2.5, 0.5}, {-1.25, 3.75}, {0.5, -7.5}}) {
                double[] w = WholeModelMath.rotate(new double[] {q[0], 0, q[1]}, f);
                double[] m = dev.denfry.promptcritical.mod.multiblock.WholeShapes.unrotate(w[0], w[2], f);
                h.assertTrue(Math.abs(m[0] - q[0]) < 1e-9 && Math.abs(m[1] - q[1]) < 1e-9, "cells turn with the model, " + f);
            }
        // сломанная клетка на краю объёма находит контроллер, и машина уходит целиком, без невидимых остатков
        BlockPos top = h.absolutePos(p.offset(2, 17, 2));
        h.assertTrue(!level.getBlockState(top).isAir(), "the top corner cell is part of the unit");
        level.destroyBlock(top, false);
        h.assertTrue(level.getBlockState(h.absolutePos(p)).isAir() && level.getBlockState(axis).isAir() && level.getBlockState(corner).isAir(),
                "the whole unit is gone after breaking an edge cell");
        h.succeed();
    }

    /** АМ-1 реального масштаба: площадка принимает чертёж на основании 7×7, старый АМ-1 — в старой серии и работает. */
    @GameTest(template = TALL, timeoutTicks = 40)
    public static void am1SiteAcceptsAndLegacyAm1StillWorks(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 1, 5);
        for (int x = -3; x <= 3; x++)
            for (int z = -3; z <= 3; z++) h.setBlock(p.offset(x, -1, z), Blocks.STONE);
        h.setBlock(p, SiteRegistry.SITE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        var w = site(h, p).accept("am1_reactor");
        h.assertTrue(w == null, "AM-1 plan accepted: " + (w == null ? "" : w.getString()));
        h.assertTrue(PcBlocks.FP_AM1_REACTOR.cells(BlockPos.ZERO, Direction.NORTH).size() == 7 * 10 * 7, "7x10x7 footprint");
        h.assertTrue(PcBlocks.legacy().contains(PcBlocks.POWER_REACTOR) && !PcBlocks.legacy().contains(PcBlocks.PRIMARY_PUMP),
                "old AM-1 is legacy, the pump it shares with the real-scale reactors is not");
        BlockPos old = new BlockPos(1, 1, 1);
        h.setBlock(old, PcBlocks.POWER_REACTOR.get().defaultBlockState());
        h.assertTrue(h.getBlockEntity(old) instanceof dev.denfry.promptcritical.mod.npp.PowerReactorBlockEntity b && b.wholeModelId() == null,
                "legacy AM-1 keeps its block entity and no whole model");
        h.succeed();
    }

    /**
     * Горизонтальная паровая машина реального масштаба: тот же BE паровой машины с цельной моделью, площадка принимает
     * чертёж на основании 3×5, клетка вала — у маховика.
     */
    @GameTest(template = TALL, timeoutTicks = 40)
    public static void millEngineSiteAcceptsAndDrawsItsWholeModel(GameTestHelper h) {
        BlockPos p = new BlockPos(3, 1, 2);
        for (int x = 0; x <= 2; x++)
            for (int z = 0; z <= 4; z++) h.setBlock(p.offset(x, -1, z), Blocks.STONE);
        h.setBlock(p, SiteRegistry.SITE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        var w = site(h, p).accept("mill_engine");
        h.assertTrue(w == null, "mill engine plan accepted: " + (w == null ? "" : w.getString()));
        h.assertTrue(PcBlocks.FP_MILL_ENGINE.cells(BlockPos.ZERO, Direction.NORTH).size() == 3 * 3 * 5, "3x3x5 engine");
        h.setBlock(p, Blocks.AIR);
        h.setBlock(p, PcBlocks.MILL_ENGINE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        h.assertTrue(h.getBlockEntity(p) instanceof dev.denfry.promptcritical.mod.boiler.SteamEngineBlockEntity b
                && "mill_engine".equals(b.wholeModelId()), "the mill engine is a steam engine with its whole model");
        h.succeed();
    }

    /**
     * Вагранка реального масштаба (08 §8.9, docs/research/cupola-dimensions.md): площадка принимает чертёж на основании
     * 3×4, вагранка — 3×8×4 с цельной моделью и тем же юнитом симуляции, что старая; вал входит в клетку вентилятора
     * (1, 1, 1) с востока, колоша — только с порта площадки, у жёлоба склада нет; старая вагранка остаётся как была.
     */
    @GameTest(template = TALL, timeoutTicks = 100)
    public static void realCupolaSiteAcceptsAndOldCupolaStillWorks(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 1, 2);
        for (int x = -1; x <= 1; x++)
            for (int z = 0; z <= 3; z++) h.setBlock(p.offset(x, -1, z), Blocks.STONE);
        h.setBlock(p, SiteRegistry.SITE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        var w = site(h, p).accept("cupola_furnace_real");
        h.assertTrue(w == null, "cupola plan accepted: " + (w == null ? "" : w.getString()));
        h.assertTrue(PcBlocks.FP_CUPOLA_FURNACE_REAL.cells(BlockPos.ZERO, Direction.NORTH).size() == 3 * 8 * 4, "3x8x4 cupola");
        h.setBlock(p, Blocks.AIR);
        h.setBlock(p, PcBlocks.CUPOLA_FURNACE_REAL.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        h.assertTrue(h.getBlockEntity(p) instanceof dev.denfry.promptcritical.mod.foundry.CupolaRealBlockEntity b
                && "cupola_furnace_real".equals(b.wholeModelId())
                && dev.denfry.promptcritical.sim.metallurgy.FoundrySubsystem.CUPOLA.equals(b.deviceType()),
                "the real cupola draws its whole model and runs the cupola unit");
        h.assertTrue(h.getBlockState(p.offset(1, 1, 1)).is(PcBlocks.KINETIC_PORT.get())
                && h.getBlockState(p.offset(1, 1, 1)).getValue(dev.denfry.promptcritical.mod.boiler.KineticPortBlock.INPUT),
                "the blower cell takes the Create shaft");
        var items = net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK;
        h.assertTrue(h.getLevel().getCapability(items, h.absolutePos(p), Direction.NORTH) == null, "no charging at the spout");
        h.assertTrue(h.getLevel().getCapability(items, h.absolutePos(p.offset(0, 3, 3)), Direction.SOUTH) != null,
                "charging from the platform edge");
        h.assertTrue(h.getLevel().getCapability(items, h.absolutePos(p.offset(0, 3, 3)), Direction.EAST) == null,
                "the platform side is no charging port");
        BlockPos old = new BlockPos(1, 2, 9);
        h.setBlock(old, PcBlocks.CUPOLA_FURNACE.get().defaultBlockState());
        h.assertTrue(h.getBlockEntity(old) instanceof dev.denfry.promptcritical.mod.foundry.CupolaBlockEntity, "the old cupola keeps its block entity");
        // тип устройства — как у старой вагранки, иначе FoundrySubsystem не создаст CupolaUnit
        h.succeedWhen(() -> h.assertTrue(h.getBlockEntity(p) instanceof dev.denfry.promptcritical.mod.foundry.CupolaRealBlockEntity b
                && b.unit() != null, "the real cupola is bound to a CupolaUnit"));
    }

    /**
     * Стальной копёр реального масштаба: колонна ствола 3×3 пуста по всей высоте (там ходит клеть), подъёмная машина
     * видит его собранным, клеть на верхней посадке — на три ряда ниже контроллера, как у старого копра.
     */
    @GameTest(template = TALL, timeoutTicks = 20)
    public static void steelHeadframeKeepsTheShaftColumnFree(GameTestHelper h) {
        for (var c : PcBlocks.FP_HEADFRAME_STEEL.cells(BlockPos.ZERO, Direction.NORTH)) {
            BlockPos o = c.pos();
            h.assertTrue(o.equals(BlockPos.ZERO) || Math.abs(o.getX()) > 1 || Math.abs(o.getZ()) > 1, "shaft column is free at " + o);
        }
        BlockPos p = new BlockPos(5, 8, 5);
        h.setBlock(p, PcBlocks.HEADFRAME_STEEL.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        var hf = dev.denfry.promptcritical.mod.mine.HoistGeometry.stackAt(h.getLevel(), h.absolutePos(p));
        h.assertTrue(hf != null && hf.formed(), "the hoist sees the steel headframe formed");
        h.assertValueEqual(hf.cageTopY(), h.absolutePos(p).getY() - 3, "cage at the top landing three rows under the controller");
        h.assertTrue(h.getBlockEntity(p) instanceof dev.denfry.promptcritical.mod.mine.HeadframeSteelBlockEntity b
                && "headframe_steel".equals(b.wholeModelId()), "the steel headframe draws its whole model");
        h.succeed();
    }

    /**
     * Ланкаширский котёл реального масштаба (07 §7.9.4): площадка принимает чертёж на основании 5×10, котёл — тот же BE
     * жаротрубного котла с цельной моделью, лобовая клетка контроллера не патрубок; старый котёл 3×3×6 работает как был.
     */
    @GameTest(template = TALL, timeoutTicks = 40)
    public static void lancashireSiteAcceptsAndOldBoilerStillWorks(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 1, 1);
        for (int x = -2; x <= 2; x++)
            for (int z = 0; z <= 9; z++) h.setBlock(p.offset(x, -1, z), Blocks.STONE);
        h.setBlock(p, SiteRegistry.SITE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        var w = site(h, p).accept("lancashire_boiler");
        h.assertTrue(w == null, "Lancashire plan accepted: " + (w == null ? "" : w.getString()));
        h.assertTrue(PcBlocks.FP_LANCASHIRE_BOILER.cells(BlockPos.ZERO, Direction.NORTH).size() == 5 * 4 * 10, "5x4x10 setting");
        h.setBlock(p, Blocks.AIR);
        h.setBlock(p, PcBlocks.LANCASHIRE_BOILER.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        h.assertTrue(h.getBlockEntity(p) instanceof dev.denfry.promptcritical.mod.boiler.FireTubeBoilerBlockEntity b
                && "lancashire_boiler".equals(b.wholeModelId()), "the Lancashire boiler is a fire-tube boiler with its whole model");
        h.assertTrue(h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, h.absolutePos(p),
                Direction.NORTH) == null, "the controller cell is no feed port");
        BlockPos old = new BlockPos(1, 2, 8);
        h.setBlock(old, PcBlocks.FIRE_TUBE_BOILER.get().defaultBlockState());
        h.assertTrue(h.getBlockEntity(old) instanceof dev.denfry.promptcritical.mod.boiler.FireTubeBoilerBlockEntity b && b.wholeModelId() == null,
                "the old fire-tube boiler keeps its own renderer");
        h.succeed();
    }

    /**
     * Трубы цепляются к машине реального масштаба только у патрубков: при любом фасаде порт, повёрнутый как модель,
     * открыт со своей грани и закрыт с соседней; клетка без порта закрыта; стальные леса — лестница.
     */
    @GameTest(template = "gametest/empty_12x12x12", timeoutTicks = 20)
    public static void pipesConnectOnlyAtPorts(GameTestHelper h) {
        for (String id : new String[] {"vver_reactor", "vver_steam_generator", "vver_coolant_pump", "vver_pressurizer", "bwr240_reactor",
                "candu6_reactor", "bn600_reactor", "am1_reactor", "lancashire_boiler", "mill_engine", "cupola_furnace_real"}) {
            h.assertTrue(!dev.denfry.promptcritical.mod.multiblock.WholePorts.of(id).isEmpty(), id + " declares its ports");
            for (Direction f : Direction.Plane.HORIZONTAL)
                for (var port : dev.denfry.promptcritical.mod.multiblock.WholePorts.of(id)) {
                    double[] w = WholeModelMath.rotate(new double[] {port.x() + 0.5, 0, port.z() + 0.5}, f);
                    int wx = (int) Math.floor(w[0]), wz = (int) Math.floor(w[2]);
                    Direction side = Direction.NORTH;
                    for (Direction d : Direction.values()) {
                        double[] v = WholeModelMath.rotate(new double[] {port.x() + 0.5 + port.face().getStepX(), 0, port.z() + 0.5 + port.face().getStepZ()}, f);
                        if (port.face().getAxis().isVertical() ? d == port.face()
                                : (int) Math.round(v[0] - w[0]) == d.getStepX() && (int) Math.round(v[2] - w[2]) == d.getStepZ() && d.getStepY() == 0) side = d;
                    }
                    h.assertTrue(dev.denfry.promptcritical.mod.multiblock.WholePorts.allows(id, f, wx, port.y(), wz, side), id + " port open " + port + " " + f);
                    h.assertTrue(!dev.denfry.promptcritical.mod.multiblock.WholePorts.allows(id, f, wx, port.y(), wz, side.getAxis().isVertical() ? Direction.NORTH : side.getClockWise()),
                            id + " port closed from the side " + port + " " + f);
                }
            h.assertTrue(!dev.denfry.promptcritical.mod.multiblock.WholePorts.allows(id, Direction.NORTH, 0, 1, 0, Direction.EAST), id + " inner cell has no port");
        }
        BlockPos s = new BlockPos(3, 1, 3);
        h.setBlock(s, PcBlocks.STEEL_SCAFFOLD.get());
        h.assertTrue(h.getBlockState(s).is(net.minecraft.tags.BlockTags.CLIMBABLE), "steel scaffold is climbable");
        h.succeed();
    }

    /**
     * Материалы машин реального масштаба — по массе прототипа (решение владельца 2026-11-23): металл этапов каждого
     * проекта в пределах ±50 % массы из таблицы размеров, у каждого предмета есть масса.
     */
    @GameTest(template = "gametest/empty_12x12x12", timeoutTicks = 20)
    public static void sitePlansFollowTheRealMass(GameTestHelper h) {
        int n = 0;
        for (SitePlans.Plan plan : SitePlans.all()) {
            n++;
            for (SitePlans.Stage st : plan.stages())
                for (SitePlans.Need need : st.needs())
                    h.assertTrue(!Double.isNaN(dev.denfry.promptcritical.mod.site.ItemMass.kg(need.item().get())), plan.id() + ": mass of " + need.item().get());
            double r = plan.plannedMetalKg() / plan.metalKg();
            h.assertTrue(r > 1 / 1.5 && r < 1.5, plan.id() + ": planned metal " + Math.round(plan.plannedMetalKg() / 1000) + " t vs prototype "
                    + Math.round(plan.metalKg() / 1000) + " t");
        }
        h.assertTrue(n == 12, "twelve real-scale plans (eight reactors and NPP units, four I0 machines), got " + n);
        h.succeed();
    }

    /** Площадка берёт из воронки или Create только недостающее на этапе: лишнее и ненужное возвращается. */
    @GameTest(template = TALL, timeoutTicks = 40)
    public static void siteTakesOnlyWhatTheStageLacks(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 1, 5);
        placeSite(h, p);
        h.assertTrue(site(h, p).accept("vver_pressurizer") == null, "plan accepted");
        var items = site(h, p).items();
        SitePlans.Need beams = SitePlans.get("vver_pressurizer").stages().getFirst().needs().get(1);
        ItemStack rest = items.insertItem(0, new ItemStack(beams.item().get(), beams.count() + 5), false);
        h.assertTrue(rest.getCount() == 5, "only the lacking beams are taken, rest " + rest.getCount());
        h.assertTrue(items.insertItem(0, new ItemStack(net.minecraft.world.item.Items.DIRT, 3), false).getCount() == 3, "unneeded items are refused");
        h.assertTrue(site(h, p).missing().size() == 1, "only concrete is still missing");
        h.succeed();
    }

    /**
     * Клеть шахты (отзыв игрока rc.1: «в клеть не войти»): ни одна клетка клети не полный блок, а существо внутри
     * стоит на полу клети, а не выталкивается из сплошного блока.
     */
    @GameTest(template = "gametest/empty_12x12x12", timeoutTicks = 60)
    public static void mineCageCanBeEntered(GameTestHelper h) {
        BlockPos c = new BlockPos(5, 3, 5);
        h.setBlock(c, PcBlocks.MINE_CAGE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(BlockStateProperties.OPEN, true));
        var level = h.getLevel();
        double floorTop = 0;
        for (Footprint.Cell cell : ((dev.denfry.promptcritical.mod.block.PcBlock) PcBlocks.MINE_CAGE.get()).spec().footprint()
                .cells(h.absolutePos(c), Direction.NORTH)) {
            var shape = level.getBlockState(cell.pos()).getCollisionShape(level, cell.pos());
            h.assertTrue(!level.getBlockState(cell.pos()).isAir(), "cage cell placed " + cell.pos());
            h.assertTrue(shape.isEmpty() || shape.bounds().getXsize() * shape.bounds().getYsize() * shape.bounds().getZsize() < 1
                    || shape.toAabbs().stream().mapToDouble(b -> b.getXsize() * b.getYsize() * b.getZsize()).sum() < 0.5,
                    "cage cell is not a solid block: " + cell.pos());
        }
        BlockPos floor = c.below();
        var stand = h.spawn(net.minecraft.world.entity.EntityType.ARMOR_STAND, new net.minecraft.world.phys.Vec3(floor.getX() + 0.5, floor.getY() + 0.5, floor.getZ() + 0.5));
        h.runAfterDelay(30, () -> {
            double y = stand.getY() - h.absolutePos(floor).getY();
            h.assertTrue(stand.isAlive() && y > 0.05 && y < 0.3, "standing on the cage floor (2 px slab), y offset " + y);
            h.succeed();
        });
    }

    /** Предмет старой серии по-прежнему ставится и работает: компенсатор давления — блок того же типа BE. */
    @GameTest(template = "gametest/empty_12x12x12", timeoutTicks = 40)
    public static void legacyPressurizerStillWorks(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 1, 5);
        h.setBlock(p, PcBlocks.PRESSURIZER.get().defaultBlockState());
        h.assertTrue(h.getBlockEntity(p) instanceof dev.denfry.promptcritical.mod.npp.PressurizerBlockEntity, "legacy pressurizer has its block entity");
        h.assertTrue(PcItems.STEEL_PLATE.get() != null, "items available");
        h.succeed();
    }
}

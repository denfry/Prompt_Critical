package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.hydrogen.ElectrolyserBlockEntity;
import dev.denfry.promptcritical.mod.hydrogen.FuelCellBlockEntity;
import dev.denfry.promptcritical.mod.hydrogen.H2CompressorBlockEntity;
import dev.denfry.promptcritical.mod.hydrogen.H2StorageBlockEntity;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.denfry.promptcritical.mod.multiblock.WholePorts;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.site.ConstructionSiteBlockEntity;
import dev.denfry.promptcritical.mod.site.SiteRegistry;
import dev.denfry.promptcritical.sim.hydrogen.HydrogenStorage;
import dev.denfry.promptcritical.sim.hydrogen.PemFuelCell;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Водородная станция: порты сходятся с клетками при любом фасаде; электролизёр по газовой линии из труб Create наполняет
 * газгольдер, компрессор с тройника на этой линии перекачивает водород в рампу; топливный элемент без линии водорода
 * отключается по низкому давлению.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HydrogenGameTests {
    private static final String[] IDS = {"h2_electrolyser_real", "h2_compressor_real", "h2_tube_bank_real", "h2_gasholder_real", "h2_fuel_cell_real"};

    private HydrogenGameTests() {
    }

    private static BlockState facing(Block b, Direction f) {
        return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f);
    }

    /** Поставить машину по плану стройки (план должен принять площадку), на камне под нижними клетками. */
    private static void build(GameTestHelper h, BlockPos p, Direction f, String id, Footprint fp, Block block) {
        int minY = Integer.MAX_VALUE;
        for (Footprint.Cell c : fp.cells(BlockPos.ZERO, f)) minY = Math.min(minY, c.pos().getY());
        for (Footprint.Cell c : fp.cells(BlockPos.ZERO, f))
            if (c.pos().getY() == minY) h.setBlock(p.offset(c.pos()).below(), Blocks.STONE);
        h.setBlock(p, facing(SiteRegistry.SITE.get(), Direction.NORTH));
        var w = ((ConstructionSiteBlockEntity) h.getBlockEntity(p)).accept(id);
        h.assertTrue(w == null, id + " plan accepted: " + (w == null ? "" : w.getString()));
        h.setBlock(p, Blocks.AIR);
        h.setBlock(p, facing(block, f));
    }

    private static void pipe(GameTestHelper h, int x, int y, int z) {
        h.setBlock(new BlockPos(x, y, z), AllBlocks.FLUID_PIPE.get().defaultBlockState());
    }

    @GameTest(template = "gametest/empty_3x3x3", timeoutTicks = 20)
    public static void hydrogenPortsMapBackToThemselvesAtEveryFacing(GameTestHelper h) {
        BlockPos c = new BlockPos(100, 64, -40);
        for (String id : IDS) {
            h.assertTrue(!WholePorts.of(id).isEmpty(), id + " declares its ports");
            for (Direction f : Direction.Plane.HORIZONTAL)
                for (int i = 0; i < WholePorts.of(id).size(); i++) {
                    var port = WholePorts.of(id).get(i);
                    BlockPos cell = WholePorts.cell(c, f, port);
                    Direction side = WholePorts.worldFace(f, port);
                    int back = WholePorts.portIndex(id, f, cell.getX() - c.getX(), cell.getY() - c.getY(), cell.getZ() - c.getZ(), side);
                    h.assertTrue(back == i, id + " port " + i + " at " + f + " maps back to " + back);
                    h.assertTrue(WholePorts.allows(id, f, cell.getX() - c.getX(), cell.getY() - c.getY(), cell.getZ() - c.getZ(), side),
                            id + " port " + i + " open for pipes at " + f);
                }
        }
        h.succeed();
    }

    /**
     * Электролизёр (фасад на север) → линия по x = 9 на высоте 6 → газгольдер; от линии тройник вниз к всасу компрессора,
     * повёрнутого на юг; нагнетание — в рампу. Питание на стенде даром; вода охлаждения компрессора налита в его порт.
     */
    @GameTest(template = "gametest/empty_16x24x16", timeoutTicks = 3200)
    public static void electrolyserFillsTheGasholderAndTheCompressorFillsTheTubeBank(GameTestHelper h) {
        BlockPos e = new BlockPos(5, 2, 13), g = new BlockPos(12, 2, 3), c = new BlockPos(6, 2, 7), t = new BlockPos(4, 2, 2);
        build(h, e, Direction.NORTH, "h2_electrolyser_real", PcBlocks.FP_H2_ELECTROLYSER_REAL, PcBlocks.H2_ELECTROLYSER_REAL.get());
        build(h, g, Direction.NORTH, "h2_gasholder_real", PcBlocks.FP_H2_GASHOLDER_REAL, PcBlocks.H2_GASHOLDER_REAL.get());
        build(h, c, Direction.SOUTH, "h2_compressor_real", PcBlocks.FP_H2_COMPRESSOR_REAL, PcBlocks.H2_COMPRESSOR_REAL.get());
        build(h, t, Direction.NORTH, "h2_tube_bank_real", PcBlocks.FP_H2_TUBE_BANK_REAL, PcBlocks.H2_TUBE_BANK_REAL.get());
        // водород: с верха сепаратора (9, 5, 13) по x = 9 на высоте 6 до z = 3 и вниз к входу газгольдера (10, 3, 3)
        for (int z = 3; z <= 13; z++) pipe(h, 9, 6, z);
        for (int y = 3; y <= 5; y++) pipe(h, 9, y, 3);
        // тройник: от линии вниз к всасу компрессора (восточная грань (8, 2, 7) у повёрнутого на юг)
        for (int y = 2; y <= 5; y++) pipe(h, 9, y, 7);
        // нагнетание: с западной грани (4, 2, 7) в обход рампы к её патрубку (7, 3, 2)
        for (int z = 4; z <= 7; z++) pipe(h, 3, 2, z);
        for (int x = 4; x <= 8; x++) pipe(h, x, 2, 4);
        pipe(h, 8, 2, 3);
        pipe(h, 8, 2, 2);
        pipe(h, 8, 3, 2);

        ElectrolyserBlockEntity el = (ElectrolyserBlockEntity) h.getBlockEntity(e);
        H2StorageBlockEntity holder = (H2StorageBlockEntity) h.getBlockEntity(g);
        H2CompressorBlockEntity comp = (H2CompressorBlockEntity) h.getBlockEntity(c);
        H2StorageBlockEntity tubes = (H2StorageBlockEntity) h.getBlockEntity(t);
        h.assertTrue(tubes.tubeBank() && !holder.tubeBank(), "the tube bank and the gasholder are told apart by block");
        IFluidHandler coolingPort = comp.portFluids(3);
        h.assertTrue(coolingPort != null, "the compressor takes cooling water at its port");
        coolingPort.fill(new FluidStack(Fluids.WATER, 2000), IFluidHandler.FluidAction.EXECUTE);
        double tubesStart = tubes.storage().mol();
        el.cells().setOn(true);
        comp.compressor().setOn(true);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(el.cells().h2MolS() > 5 && holder.storage().pressurePa() > HydrogenStorage.ATM_PA + 2000,
                        "the electrolyser makes hydrogen into the gasholder: " + el.cells().h2Nm3h() + " Nm3/h, "
                                + Math.round(holder.storage().pressurePa()) + " Pa"))
                .thenWaitUntil(() -> h.assertTrue(comp.compressor().powerW() > 0,
                        "the compressor starts on its suction switch: gasholder " + Math.round(holder.storage().pressurePa()) + " Pa"))
                .thenWaitUntil(() -> h.assertTrue(tubes.storage().mol() > tubesStart + 50,
                        "hydrogen reaches the tube bank: " + Math.round(tubes.storage().mol() - tubesStart) + " mol"))
                .thenExecute(() -> {
                    h.assertTrue(el.cells().trip() == dev.denfry.promptcritical.sim.hydrogen.AlkalineElectrolyser.Trip.NONE,
                            "the electrolyser runs on: " + el.cells().trip());
                    h.assertTrue(el.releasedMol() == 0, "nothing goes to the air while the line is there");
                    h.assertTrue(holder.storage().pressurePa() < 1.3e5, "the gasholder stays below the electrolyser back-pressure trip");
                })
                .thenSucceed();
    }

    /**
     * Открытый конец трубы на линии рампы — разрыв: газ уходит в воздух, давление падает, газоанализатор звенит. Огня
     * рядом нет — без взрыва.
     */
    @GameTest(template = "gametest/empty_12x12x12", timeoutTicks = 200)
    public static void anOpenLineEndLeaksTheTubeBankAndRaisesTheGasAlarm(GameTestHelper h) {
        BlockPos t = new BlockPos(5, 2, 5);
        build(h, t, Direction.NORTH, "h2_tube_bank_real", PcBlocks.FP_H2_TUBE_BANK_REAL, PcBlocks.H2_TUBE_BANK_REAL.get());
        // патрубок рампы — восточная грань (8, 3, 5); прямая труба с раструбом на восток в воздух
        h.setBlock(new BlockPos(9, 3, 5), AllBlocks.FLUID_PIPE.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.PipeBlock.EAST, true).setValue(net.minecraft.world.level.block.PipeBlock.WEST, true));
        H2StorageBlockEntity tubes = (H2StorageBlockEntity) h.getBlockEntity(t);
        HydrogenStorage g = tubes.storage();
        g.add(dev.denfry.promptcritical.sim.hydrogen.HydrogenGas.molarDensity(50e5, 293.15) * g.spec().volumeM3() - g.mol(), 293.15);
        double start = g.pressurePa();
        h.succeedWhen(() -> {
            h.assertTrue(tubes.leaking(), "the open pipe end leaks");
            h.assertTrue(g.pressurePa() < start - 2e5, "the tube bank loses pressure: " + Math.round(g.pressurePa() / 1e5) + " bar");
            h.assertTrue(tubes.hazard().alarm(), "the gas alarm sounds: " + Math.round(tubes.hazard().room().lelFraction() * 100) + " % LEL");
            h.assertTrue(tubes.hazard().explosions() == 0, "nothing to light it");
        });
    }

    @GameTest(template = "gametest/empty_12x12x12", timeoutTicks = 60)
    public static void fuelCellWithoutAHydrogenLineTripsOnLowPressure(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 2, 5);
        build(h, p, Direction.NORTH, "h2_fuel_cell_real", PcBlocks.FP_H2_FUEL_CELL_REAL, PcBlocks.H2_FUEL_CELL_REAL.get());
        FuelCellBlockEntity fc = (FuelCellBlockEntity) h.getBlockEntity(p);
        fc.cell().setOn(true);
        h.succeedWhen(() -> {
            h.assertTrue(fc.cell().trip() == PemFuelCell.Trip.LOW_H2, "no hydrogen line: low hydrogen trip, got " + fc.cell().trip());
            h.assertTrue(fc.cell().acW() == 0, "no output");
        });
    }
}

package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.boiler.KineticPortBlock;
import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import dev.denfry.promptcritical.mod.ironworks.ByproductPlantBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.CastHouseMachineBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.DustCatcherBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.GasExhauster;
import dev.denfry.promptcritical.mod.ironworks.GasExhausterRealBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.LadleBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.SinterStrandBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.SlagGranulatorBlockEntity;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.site.ConstructionSiteBlockEntity;
import dev.denfry.promptcritical.mod.site.SitePlans;
import dev.denfry.promptcritical.mod.site.SiteRegistry;
import dev.denfry.promptcritical.sim.metallurgy.FoundrySubsystem;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Агломерация, газоочистка, улавливание и литейный двор реального масштаба (08 §8.4–8.5): площадка принимает проекты,
 * машины рисуют цельные модели на логике старой серии (батарея и домна находят их теми же классами), валы и патрубки —
 * только в объявленных клетках.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IronworksRealScaleGameTests {
    private static final String TALL = "gametest/empty_12x24x12";

    private IronworksRealScaleGameTests() {}

    private static BlockState north(Block b) {
        return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    }

    private record Machine(String id, Supplier<? extends Block> block, Footprint fp, int cells, Class<? extends BlockEntity> base) {}

    private static final Machine[] MACHINES = {
            new Machine("sinter_strand_real", PcBlocks.SINTER_STRAND_REAL, PcBlocks.FP_SINTER_STRAND_REAL, 3 * 3 * 9, SinterStrandBlockEntity.class),
            new Machine("dust_catcher_real", PcBlocks.DUST_CATCHER_REAL, PcBlocks.FP_DUST_CATCHER_REAL, 3 * 10 * 3, DustCatcherBlockEntity.class),
            new Machine("gas_exhauster_real", PcBlocks.GAS_EXHAUSTER_REAL, PcBlocks.FP_GAS_EXHAUSTER_REAL, 27, GasExhausterRealBlockEntity.class),
            new Machine("byproduct_plant_cooler_real", PcBlocks.BYPRODUCT_PLANT_COOLER_REAL, PcBlocks.FP_BYPRODUCT_COOLER_REAL, 90,
                    ByproductPlantBlockEntity.class),
            new Machine("byproduct_plant_precipitator_real", PcBlocks.BYPRODUCT_PLANT_PRECIPITATOR_REAL, PcBlocks.FP_BYPRODUCT_PRECIPITATOR_REAL, 81,
                    ByproductPlantBlockEntity.class),
            new Machine("byproduct_plant_saturator_real", PcBlocks.BYPRODUCT_PLANT_SATURATOR_REAL, PcBlocks.FP_BYPRODUCT_SATURATOR_REAL, 36,
                    ByproductPlantBlockEntity.class),
            new Machine("byproduct_plant_scrubber_real", PcBlocks.BYPRODUCT_PLANT_SCRUBBER_REAL, PcBlocks.FP_BYPRODUCT_SCRUBBER_REAL, 144,
                    ByproductPlantBlockEntity.class),
            new Machine("cast_house_mud_gun_real", PcBlocks.CAST_HOUSE_MUD_GUN_REAL, PcBlocks.FP_CAST_HOUSE_MACHINE_REAL, 27,
                    CastHouseMachineBlockEntity.class),
            new Machine("cast_house_drill_real", PcBlocks.CAST_HOUSE_DRILL_REAL, PcBlocks.FP_CAST_HOUSE_MACHINE_REAL, 27,
                    CastHouseMachineBlockEntity.class),
            new Machine("ladle_real", PcBlocks.LADLE_REAL, PcBlocks.FP_LADLE_REAL, 27, LadleBlockEntity.class),
            new Machine("slag_granulator_real", PcBlocks.SLAG_GRANULATOR_REAL, PcBlocks.FP_SLAG_GRANULATOR_REAL, 48, SlagGranulatorBlockEntity.class)};

    /** Каждый проект принимается на свободной площадке с основанием; объём модели — как в контракте art-24. */
    @GameTest(template = TALL, timeoutTicks = 40)
    public static void ironworksSitesAcceptTheRealMachines(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 1, 2);
        for (int x = -1; x <= 2; x++)
            for (int z = -1; z <= 8; z++) h.setBlock(p.offset(x, -1, z), Blocks.STONE);
        for (Machine m : MACHINES) {
            h.assertTrue(m.fp().cells(BlockPos.ZERO, Direction.NORTH).size() == m.cells(), m.id() + " covers " + m.cells() + " cells");
            h.setBlock(p, north(SiteRegistry.SITE.get()));
            var w = ((ConstructionSiteBlockEntity) h.getBlockEntity(p)).accept(m.id());
            h.assertTrue(w == null, m.id() + " plan accepted: " + (w == null ? "" : w.getString()));
            h.setBlock(p, Blocks.AIR);
        }
        h.succeed();
    }

    /** Поставленная машина рисует свою цельную модель и стоит на BE старой серии (или на общем интерфейсе). */
    @GameTest(template = TALL, timeoutTicks = 40)
    public static void ironworksRealMachinesKeepTheOldLogic(GameTestHelper h) {
        for (Machine m : MACHINES) {
            // BE контроллера без постановки в мир: машины не мешают друг другу
            BlockEntity be = ((net.minecraft.world.level.block.EntityBlock) m.block().get()).newBlockEntity(BlockPos.ZERO, north(m.block().get()));
            h.assertTrue(m.base().isInstance(be) && be instanceof WholeModelHost w && m.id().equals(w.wholeModelId()),
                    m.id() + " draws its whole model on " + m.base().getSimpleName());
            if (be instanceof ByproductPlantBlockEntity b)
                h.assertTrue(m.id().equals("byproduct_plant_" + b.kind().getSerializedName() + "_real"), m.id() + " kind " + b.kind());
            if (be instanceof CastHouseMachineBlockEntity c)
                h.assertTrue(m.id().equals("cast_house_" + c.kind().getSerializedName() + "_real"), m.id() + " kind " + c.kind());
        }
        h.succeed();
    }

    /**
     * Валы Create: агломашина — в клетку (−1, 0, 8), эксгаустер — в клетку (2, 1, 0); эксгаустер — юнит старого
     * эксгаустера, батарея видит его через {@link GasExhauster}, газ отдаёт только нагнетательный патрубок.
     */
    @GameTest(template = TALL, timeoutTicks = 100)
    public static void sinterStrandAndExhausterTakeTheirShafts(GameTestHelper h) {
        BlockPos s = new BlockPos(3, 1, 1);
        h.setBlock(s, north(PcBlocks.SINTER_STRAND_REAL.get()));
        BlockState sp = h.getBlockState(s.offset(-1, 0, 8));
        h.assertTrue(sp.is(PcBlocks.KINETIC_PORT.get()) && sp.getValue(KineticPortBlock.INPUT), "the strand drive takes the Create shaft");
        h.assertTrue(h.getBlockEntity(s) instanceof SinterStrandBlockEntity b && FoundrySubsystem.SINTER_STRAND.equals(b.deviceType()),
                "the real strand runs the sinter unit");
        var items = Capabilities.ItemHandler.BLOCK;
        h.assertTrue(h.getLevel().getCapability(items, h.absolutePos(s.offset(0, 2, 0)), Direction.UP) != null, "mix into the hopper");
        h.assertTrue(h.getLevel().getCapability(items, h.absolutePos(s.offset(0, 1, 4)), Direction.UP) == null, "the strand body is no port");
        BlockPos e = new BlockPos(7, 1, 4);
        h.setBlock(e, north(PcBlocks.GAS_EXHAUSTER_REAL.get()));
        BlockState ep = h.getBlockState(e.offset(2, 1, 0));
        h.assertTrue(ep.is(PcBlocks.KINETIC_PORT.get()) && ep.getValue(KineticPortBlock.INPUT), "the exhauster takes the Create shaft");
        var fluids = Capabilities.FluidHandler.BLOCK;
        h.assertTrue(h.getLevel().getCapability(fluids, h.absolutePos(e.offset(0, 2, -1)), Direction.UP) != null, "gas from the delivery branch");
        h.assertTrue(h.getLevel().getCapability(fluids, h.absolutePos(e.offset(0, 2, -1)), Direction.NORTH) == null, "only from above");
        h.succeedWhen(() -> h.assertTrue(h.getBlockEntity(e) instanceof GasExhauster g && g.machine() != null
                && FoundrySubsystem.GAS_EXHAUSTER.equals(((GasExhausterRealBlockEntity) g).deviceType()), "the exhauster is bound to its unit"));
    }

    /** Патрубки газоочистки, улавливания и грануляции — только объявленные клетки и грани. */
    @GameTest(template = TALL, timeoutTicks = 40)
    public static void ironworksRealPortsAreWhereTheModelsDrawThem(GameTestHelper h) {
        var fluids = Capabilities.FluidHandler.BLOCK;
        BlockPos d = new BlockPos(2, 1, 2);
        h.setBlock(d, north(PcBlocks.DUST_CATCHER_REAL.get()));
        h.assertTrue(h.getLevel().getCapability(fluids, h.absolutePos(d.offset(0, 8, 1)), Direction.SOUTH) != null, "clean gas from the main");
        h.assertTrue(h.getLevel().getCapability(fluids, h.absolutePos(d.offset(0, 4, 1)), Direction.SOUTH) == null, "the shell is no port");
        BlockPos g = new BlockPos(8, 1, 2);
        h.setBlock(g, north(PcBlocks.SLAG_GRANULATOR_REAL.get()));
        h.assertTrue(h.getLevel().getCapability(fluids, h.absolutePos(g.offset(1, 0, 1)), Direction.EAST) != null, "water into the jet box");
        h.assertTrue(h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(g.offset(-1, 3, 3)), Direction.WEST) != null,
                "granules from the elevator chute");
        BlockPos b = new BlockPos(2, 1, 8);
        h.setBlock(b, north(PcBlocks.BYPRODUCT_PLANT_SATURATOR_REAL.get()));
        h.assertTrue(h.getLevel().getCapability(fluids, h.absolutePos(b.offset(1, 2, 0)), Direction.EAST) != null, "acid into the saturator");
        h.assertTrue(h.getLevel().getCapability(fluids, h.absolutePos(b.offset(1, 1, 0)), Direction.EAST) == null, "below the acid branch no port");
        BlockPos l = new BlockPos(8, 1, 8);
        h.setBlock(l, north(PcBlocks.LADLE_REAL.get()));
        h.assertTrue(!h.getBlockState(l).getValue(PcProperties.FILLED), "an empty ladle");
        h.succeed();
    }

    /** Проекты — в общем списке и по реальной массе (±50 %). */
    @GameTest(template = "gametest/empty_12x12x12", timeoutTicks = 20)
    public static void ironworksPlansFollowTheRealMass(GameTestHelper h) {
        for (Machine m : MACHINES) {
            SitePlans.Plan plan = SitePlans.get(m.id());
            h.assertTrue(plan != null && plan.unit().get() == m.block().get(), m.id() + " has a site plan for its block");
            double r = plan.plannedMetalKg() / plan.metalKg();
            h.assertTrue(r > 1 / 1.5 && r < 1.5, m.id() + ": planned metal vs prototype " + r);
        }
        h.succeed();
    }
}

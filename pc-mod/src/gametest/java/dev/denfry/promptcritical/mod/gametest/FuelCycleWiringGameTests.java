package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.nuclear.AssemblyJigBlockEntity;
import dev.denfry.promptcritical.mod.nuclear.CascadeBlockEntity;
import dev.denfry.promptcritical.mod.nuclear.CentrifugeRealBlockEntity;
import dev.denfry.promptcritical.mod.nuclear.PelletPressBlockEntity;
import dev.denfry.promptcritical.mod.nuclear.PlacedItemBlockEntity;
import dev.denfry.promptcritical.mod.nuclear.RodStationBlockEntity;
import dev.denfry.promptcritical.mod.nuclear.SinteringFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.nuclear.Uf6AutoclaveBlockEntity;
import dev.denfry.promptcritical.mod.nuclear.Uf6CylinderItem;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.uranium.FluorinationBlockEntity;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Топливный цикл реального масштаба: пульт каскада считает реальные центрифуги и секции, автоклав прогревает баллон
 * выше тройной точки UF₆, положенный баллон 30B хранит заряд, реальные машины работают на BE старой серии. BE,
 * поставленный через setBlock, получает onLoad тиком позже — проверки после паузы.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FuelCycleWiringGameTests {
    private static final String ROOM = "gametest/empty_12x12x12";

    private FuelCycleWiringGameTests() {}

    private static BlockState north(Block b) {
        return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    }

    private static ItemStack full30b() {
        return Uf6CylinderItem.fill(new ItemStack(PcItems.UF6_CYLINDER_30B.get()), 2_000, 0.00711, 0.000054);
    }

    /** Закрытая центрифуга и секция на 16 машин — 17 машин у пульта; вскрытая центрифуга из счёта выпадает. */
    @GameTest(template = ROOM, timeoutTicks = 100)
    public static void cascadeCountsRealCentrifuges(GameTestHelper h) {
        BlockPos console = new BlockPos(6, 1, 6), a = new BlockPos(2, 1, 2), b = new BlockPos(3, 1, 2), section = new BlockPos(2, 1, 9);
        h.setBlock(console, north(PcBlocks.CENTRIFUGE_CASCADE.get()));
        h.setBlock(a, north(PcBlocks.GAS_CENTRIFUGE_REAL.get()));
        h.setBlock(b, north(PcBlocks.GAS_CENTRIFUGE_REAL.get()).setValue(BlockStateProperties.OPEN, true));
        h.setBlock(section, north(PcBlocks.CENTRIFUGE_CASCADE_SECTION_REAL.get()));
        h.runAfterDelay(45, () -> {
            CascadeBlockEntity c = (CascadeBlockEntity) h.getBlockEntity(console);
            h.assertTrue(c.machines() == 17, "one closed machine and a section of 16, got " + c.machines());
            CentrifugeRealBlockEntity s = (CentrifugeRealBlockEntity) h.getBlockEntity(section);
            h.assertTrue(s.machines() == CentrifugeRealBlockEntity.SECTION_MACHINES && s.real(), "section of 16");
            h.succeed();
        });
    }

    /** Полный 30B под 20 кВт пара за несколько часов проходит 64 °C и не перегревается выше пара; без пара стынет. */
    @GameTest(template = ROOM, timeoutTicks = 20)
    public static void autoclaveHeatsACylinderPastTheTriplePoint(GameTestHelper h) {
        double cap = Uf6AutoclaveBlockEntity.heatCapacity(full30b()), t = Uf6AutoclaveBlockEntity.AMBIENT_K, s = 0;
        while (t < Uf6AutoclaveBlockEntity.TRIPLE_K && s < 24 * 3600) {
            t = Uf6AutoclaveBlockEntity.step(t, cap, Uf6AutoclaveBlockEntity.HEAT_W, 3.6);
            s += 3.6;
        }
        h.assertTrue(s > 1800 && s < 4 * 3600, "a full 30B melts in 0.5–4 h of steam, took " + s / 3600 + " h");
        for (int i = 0; i < 20_000; i++) t = Uf6AutoclaveBlockEntity.step(t, cap, Uf6AutoclaveBlockEntity.HEAT_W, 3.6);
        h.assertTrue(t <= Uf6AutoclaveBlockEntity.STEAM_K + 1e-9, "never above the steam, got " + t);
        h.assertTrue(Uf6AutoclaveBlockEntity.step(350, cap, 0, 3.6) < 350, "no steam — it cools");
        h.succeed();
    }

    /** Баллон закатывают в автоклав рукой — дверь закрывается; Shift + ПКМ — выкатить тот же баллон. */
    @GameTest(template = ROOM, timeoutTicks = 40)
    public static void autoclaveTakesAndGivesBackTheCylinder(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 1, 5);
        h.setBlock(p, north(PcBlocks.UF6_AUTOCLAVE_REAL.get()));
        var player = TestPlayers.of(h);
        h.runAfterDelay(1, () -> {
            Uf6AutoclaveBlockEntity a = (Uf6AutoclaveBlockEntity) h.getBlockEntity(p);
            ItemStack cyl = full30b();
            a.useItem(player, InteractionHand.MAIN_HAND, cyl);
            h.assertTrue(cyl.isEmpty() && !a.slot().getStackInSlot(0).isEmpty(), "the cylinder rolled in");
        });
        h.runAfterDelay(15, () -> {
            Uf6AutoclaveBlockEntity a = (Uf6AutoclaveBlockEntity) h.getBlockEntity(p);
            h.assertTrue(a.loaded(), "door closed on a cylinder");
            player.setShiftKeyDown(true);
            a.use(player);
            h.assertTrue(a.slot().getStackInSlot(0).isEmpty(), "rolled out");
            h.assertTrue(player.getInventory().items.stream().anyMatch(s -> Uf6CylinderItem.charge(s).uf6Kg() > 1_999), "with its UF₆");
            h.succeed();
        });
    }

    /** Положенный баллон 30B отдаёт обратно свой заряд, блок снимается. */
    @GameTest(template = ROOM, timeoutTicks = 20)
    public static void placedCylinderKeepsItsCharge(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 1, 5);
        h.setBlock(p, north(PcBlocks.UF6_CYLINDER_30B_REAL.get()));
        var player = TestPlayers.of(h);
        h.runAfterDelay(1, () -> {
            PlacedItemBlockEntity be = (PlacedItemBlockEntity) h.getBlockEntity(p);
            be.contents().setStackInSlot(0, full30b());
            be.use(player);
            h.assertTrue(!h.getBlockState(p).is(PcBlocks.UF6_CYLINDER_30B_REAL.get()), "picked up");
            h.assertTrue(player.getInventory().items.stream().anyMatch(s -> Uf6CylinderItem.charge(s).uf6Kg() > 1_999), "charge kept");
            h.succeed();
        });
    }

    /** Реальные машины — на BE старой серии; стенд реального масштаба собирает ВВЭР-440. */
    @GameTest(template = ROOM, timeoutTicks = 20)
    public static void realFuelMachinesShareTheOldBlockEntities(GameTestHelper h) {
        BlockPos furnace = new BlockPos(5, 1, 1), press = new BlockPos(2, 1, 5), welder = new BlockPos(8, 1, 5), stand = new BlockPos(5, 1, 9),
                tower = new BlockPos(2, 1, 9);
        h.setBlock(furnace, north(PcBlocks.SINTERING_FURNACE_REAL.get()));
        h.setBlock(press, north(PcBlocks.PELLET_PRESS_REAL.get()));
        h.setBlock(welder, north(PcBlocks.FUEL_ROD_WELDER_REAL.get()));
        h.setBlock(stand, north(PcBlocks.FUEL_ASSEMBLY_STAND_REAL.get()));
        h.setBlock(tower, north(PcBlocks.FLUORINATOR_REAL.get()));
        h.runAfterDelay(1, () -> {
            h.assertTrue(h.getBlockEntity(furnace) instanceof SinteringFurnaceBlockEntity f && f.real(), "sintering furnace");
            h.assertTrue(h.getBlockEntity(press) instanceof PelletPressBlockEntity p && p.real(), "pellet press");
            h.assertTrue(h.getBlockEntity(welder) instanceof RodStationBlockEntity w && w.real(), "rod welder");
            h.assertTrue(h.getBlockEntity(stand) instanceof AssemblyJigBlockEntity j && j.real() && j.design() == FuelDesign.VVER_440,
                    "assembly stand builds VVER-440");
            h.assertTrue(h.getBlockEntity(tower) instanceof FluorinationBlockEntity t && t.real(), "fluorinator");
            h.succeed();
        });
    }
}

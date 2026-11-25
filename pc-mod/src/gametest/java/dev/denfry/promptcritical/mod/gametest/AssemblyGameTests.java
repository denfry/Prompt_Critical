package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.manufacturing.AssemblyStandBlockEntity;
import dev.denfry.promptcritical.mod.manufacturing.ManufacturingComponents;
import dev.denfry.promptcritical.mod.manufacturing.ManufacturingItems;
import dev.denfry.promptcritical.mod.mine.MineDrainagePumpBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.manufacturing.PartDefect;
import dev.denfry.promptcritical.sim.manufacturing.PartRoute;
import dev.denfry.promptcritical.sim.manufacturing.PartSpec;
import dev.denfry.promptcritical.sim.manufacturing.assembly.MachinePassport;
import dev.denfry.promptcritical.sim.metallurgy.HeatTreatState;
import dev.denfry.promptcritical.sim.ops.OpsSubsystem;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Сборка машин (18 §18.8, веха MF5): стенд собирает насос по спецификации из датапака и выдаёт его с паспортом; насос
 * с плохим валом, поставленный из такого предмета, изнашивает подшипник быстрее хорошего.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AssemblyGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final String PUMP_SPEC = "promptcritical:mine_drainage_pump";

    private AssemblyGameTests() {
    }

    /** Сырой кованый вал: не обработан (IT14), не закалён, обезуглерожен. */
    static PartSpec badShaft() {
        return PartSpec.of("shaft", "medium_carbon", PartRoute.FORGED, HeatTreatState.AS_ROLLED, 15, 0, 12.5,
                Map.of(PartDefect.DECARB, 0.6));
    }

    private static ItemStack part(Item item, int count) {
        return new ItemStack(item, count);
    }

    /** Детали насоса в порядке шаблона centrifugal_pump. */
    static void fillPump(AssemblyStandBlockEntity stand, ItemStack shaft) {
        var inv = stand.panelItems();
        inv.setStackInSlot(0, part(ManufacturingItems.PUMP_CASING.get(), 1));
        inv.setStackInSlot(1, part(PcItems.PUMP_IMPELLER.get(), 1));
        inv.setStackInSlot(2, shaft);
        inv.setStackInSlot(3, part(ManufacturingItems.ROLLING_BEARING.get(), 2));
        inv.setStackInSlot(4, part(PcItems.GLAND_PACKING.get(), 1));
        inv.setStackInSlot(5, part(ManufacturingItems.FASTENER_KIT.get(), 1));
        inv.setStackInSlot(6, part(ManufacturingItems.MACHINE_BED.get(), 1));
    }

    @GameTest(template = LARGE, timeoutTicks = AssemblyStandBlockEntity.RUN_IN_TICKS + 100)
    public static void standAssemblesAPumpWithAPassport(GameTestHelper h) {
        BlockPos at = new BlockPos(2, 2, 2);
        h.setBlock(at, PcBlocks.ASSEMBLY_STAND.get().defaultBlockState());
        AssemblyStandBlockEntity stand = h.getBlockEntity(at);
        stand.selectSpec(PUMP_SPEC);
        h.assertTrue(stand.spec() != null, "pump spec loaded from the datapack");
        ItemStack shaft = part(ManufacturingItems.SHAFT.get(), 1);
        shaft.set(ManufacturingComponents.PART_SPEC.get(), badShaft());
        fillPump(stand, shaft);
        h.assertTrue(stand.state(0) == AssemblyStandBlockEntity.PositionState.OK, "casing without a passport is nominal");
        // трещина не проходит ворота
        ItemStack cracked = shaft.copy();
        cracked.set(ManufacturingComponents.PART_SPEC.get(), PartSpec.of("shaft", "medium_carbon", PartRoute.FORGED,
                HeatTreatState.AS_ROLLED, 15, 0, 12.5, Map.of(PartDefect.CRACK, 0.5)));
        stand.panelItems().setStackInSlot(2, cracked);
        h.assertTrue(stand.state(2) == AssemblyStandBlockEntity.PositionState.GATE_DEFECT, "cracked shaft rejected");
        h.assertFalse(stand.assemble(null), "no assembly with a rejected part");
        stand.panelItems().setStackInSlot(2, shaft);
        h.assertTrue(stand.assemble(null), "assembled");
        h.assertTrue(stand.panelItems().getStackInSlot(2).isEmpty(), "parts consumed");
        h.succeedWhen(() -> {
            ItemStack out = stand.panelItems().getStackInSlot(AssemblyStandBlockEntity.OUTPUT_SLOT);
            h.assertTrue(out.is(PcBlocks.MINE_DRAINAGE_PUMP.get().asItem()), "pump after the run-in");
            MachinePassport p = out.get(ManufacturingComponents.MACHINE_PASSPORT.get());
            h.assertTrue(p != null && p.bearingWearFactor() > 3, "bad shaft shows in the passport: " + p);
            h.assertTrue(p.imbalanceMmS() > 0, "hand balancing leaves some imbalance");
        });
    }

    /** 18 §18.13 MF4–MF5: электродвигатель 0,4 кВ больше не крафтится, его собирают на стенде из статора и деталей. */
    @GameTest(template = LARGE, timeoutTicks = AssemblyStandBlockEntity.RUN_IN_TICKS + 100)
    public static void standAssemblesAnElectricMotor(GameTestHelper h) {
        h.assertTrue(h.getLevel().getRecipeManager().byKey(PromptCritical.id("grid/electric_motor")).isEmpty(),
                "no crafting recipe for the motor");
        BlockPos at = new BlockPos(2, 2, 2);
        h.setBlock(at, PcBlocks.ASSEMBLY_STAND.get().defaultBlockState());
        AssemblyStandBlockEntity stand = h.getBlockEntity(at);
        stand.selectSpec("promptcritical:electric_motor");
        h.assertTrue(stand.spec() != null, "motor spec loaded from the datapack");
        var inv = stand.panelItems();
        inv.setStackInSlot(0, part(ManufacturingItems.STATOR.get(), 1));
        inv.setStackInSlot(1, part(ManufacturingItems.ROTOR_CORE.get(), 1));
        inv.setStackInSlot(2, part(PcItems.KEYED_SHAFT.get(), 1));
        inv.setStackInSlot(3, part(ManufacturingItems.ROLLING_BEARING.get(), 2));
        inv.setStackInSlot(4, part(ManufacturingItems.END_COVER.get(), 2));
        inv.setStackInSlot(5, part(ManufacturingItems.FAN_WHEEL.get(), 1));
        inv.setStackInSlot(6, part(ManufacturingItems.FASTENER_KIT.get(), 1));
        h.assertTrue(stand.assemble(null), "assembled");
        h.succeedWhen(() -> h.assertTrue(inv.getStackInSlot(AssemblyStandBlockEntity.OUTPUT_SLOT).is(PcBlocks.ELECTRIC_MOTOR.get().asItem()),
                "motor after the run-in"));
    }

    private static BlockState north(BlockState s) {
        return s.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    }

    /** Насос из предмета с паспортом: компоненты предмета переходят в блок-сущность, как при установке рукой. */
    private static void placePump(GameTestHelper h, BlockPos pump, MachinePassport passport) {
        h.setBlock(pump, north(PcBlocks.MINE_DRAINAGE_PUMP.get().defaultBlockState()));
        ItemStack item = new ItemStack(PcBlocks.MINE_DRAINAGE_PUMP.get().asItem());
        if (passport != null) item.set(ManufacturingComponents.MACHINE_PASSPORT.get(), passport);
        h.getBlockEntity(pump).applyComponentsFromItemStack(item);
        BlockPos motor = pump.west();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
    }

    /** Износ подшипника, приведённый к одному ресурсу: D·u_b (у двух насосов разный случайный u_b). */
    private static double wear(GameTestHelper h, BlockPos pump) {
        OpsSubsystem.Unit u = OpsGameTests.ops(h).unit(h.<MineDrainagePumpBlockEntity>getBlockEntity(pump).simId());
        return u.wear().bearing().damage() * u.wear().bearing().lifeFactor();
    }

    @GameTest(template = LARGE, timeoutTicks = 1200)
    public static void pumpWithABadShaftWearsFaster(GameTestHelper h) {
        BlockPos good = new BlockPos(3, 2, 3), bad = new BlockPos(3, 2, 8);
        MachinePassport badPassport = new MachinePassport(3.0, 1.9, 0.03, 0, 0, Double.NaN, 1, 50);
        placePump(h, good, null);
        placePump(h, bad, badPassport);
        double[] start = new double[2];
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(OpsGameTests.defects(h, h.<MineDrainagePumpBlockEntity>getBlockEntity(good)), "good unit");
                    h.assertTrue(OpsGameTests.defects(h, h.<MineDrainagePumpBlockEntity>getBlockEntity(bad)), "bad unit");
                })
                .thenWaitUntil(() -> {
                    h.assertFalse(h.<MineDrainagePumpBlockEntity>getBlockEntity(good).opsStopped(), "good pump turns");
                    h.assertFalse(h.<MineDrainagePumpBlockEntity>getBlockEntity(bad).opsStopped(), "bad pump turns");
                })
                .thenExecute(() -> {
                    OpsSubsystem.Unit u = OpsGameTests.ops(h).unit(h.<MineDrainagePumpBlockEntity>getBlockEntity(bad).simId());
                    h.assertTrue(u.passport().equals(badPassport), "placed pump keeps the item passport");
                    h.assertTrue(u.wear().assemblyWearFactor() == 3.0, "assembly wear factor applied");
                    start[0] = wear(h, good);
                    start[1] = wear(h, bad);
                })
                .thenIdle(400)
                .thenExecute(() -> {
                    double dg = wear(h, good) - start[0], db = wear(h, bad) - start[1];
                    h.assertTrue(dg > 0, "good pump bearing wears");
                    double ratio = db / dg;
                    h.assertTrue(ratio > 2.5 && ratio < 3.5, "bad shaft wears about three times faster: " + ratio);
                })
                .thenSucceed();
    }
}

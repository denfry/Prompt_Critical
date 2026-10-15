package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.FoundryComponents;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlockEntity;
import dev.denfry.promptcritical.mod.foundry.SandMoldBlockEntity;
import dev.denfry.promptcritical.mod.item.CoalAssayData;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.metallurgy.CastingDefect;
import dev.denfry.promptcritical.sim.metallurgy.CastingKind;
import dev.denfry.promptcritical.sim.metallurgy.CastingSpec;
import dev.denfry.promptcritical.sim.metallurgy.CrucibleUnit;
import dev.denfry.promptcritical.sim.metallurgy.CupolaUnit;
import dev.denfry.promptcritical.sim.metallurgy.CyclicMachine;
import dev.denfry.promptcritical.sim.metallurgy.FoundrySubsystem;
import dev.denfry.promptcritical.sim.metallurgy.MachineToolUnit;
import dev.denfry.promptcritical.sim.metallurgy.SandMoldUnit;
import dev.denfry.promptcritical.sim.mining.CoalJig;
import dev.denfry.promptcritical.sim.mining.CoalPrepSubsystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Литейка, механический цех и углеобогащение в мире (этап 8 M2, 08 §8.3, §8.9–8.10): машины на валу креативного
 * мотора Create, материалы через склады машин (как воронки), результат — предметы с компонентами.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FoundryGameTests {
    private static final String SMALL = "gametest/empty_3x3x3";
    private static final String LARGE = "gametest/empty_12x12x12";

    private FoundryGameTests() {
    }

    private static SimLevelBinding binding(GameTestHelper h) {
        SimLevelBinding b = SimBindings.get(h.getLevel());
        if (b == null) throw new IllegalStateException("no simulation binding for the test level");
        return b;
    }

    private static BlockState facing(BlockState s, Direction d) {
        return s.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
    }

    /** Креативный мотор в {@code rel}, вал смотрит в {@code toward}; скорость ставится на следующем тике. */
    private static void motor(GameTestHelper h, BlockPos rel, Direction toward, int rpm) {
        h.setBlock(rel, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, toward));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(rel)).generatedSpeed.setValue(rpm));
    }

    private static IItemHandler items(GameTestHelper h, BlockPos rel) {
        return ((FoundryDevice) h.getBlockEntity(rel)).items(null);
    }

    private static void insert(GameTestHelper h, BlockPos rel, ItemStack stack) {
        ItemStack rest = stack;
        IItemHandler inv = items(h, rel);
        for (int i = 0; i < inv.getSlots() && !rest.isEmpty(); i++) rest = inv.insertItem(i, rest, false);
        h.assertTrue(rest.isEmpty(), "inserted " + stack);
    }

    private static ItemStack find(IItemHandler inv, net.minecraft.world.item.Item item) {
        for (int i = 0; i < inv.getSlots(); i++) if (inv.getStackInSlot(i).is(item)) return inv.getStackInSlot(i);
        return ItemStack.EMPTY;
    }

    private static ItemStack casting(CastingKind kind, String alloy, CastingDefect defect) {
        ItemStack s = new ItemStack(PcItems.CASTING.get());
        s.set(FoundryComponents.CASTING_SPEC.get(), new CastingSpec(kind, alloy, 0, defect, 0, 42));
        return s;
    }

    private static <U> U unit(GameTestHelper h, BlockPos rel, Class<U> type) {
        SimLevelBinding b = binding(h);
        BlockEntity be = h.getBlockEntity(rel);
        SimId id = ((dev.denfry.promptcritical.mod.binding.DeviceHost) be).simId();
        Object u = type == CoalJig.class
                ? b.world().subsystem(CoalPrepSubsystem.class).unit(b.world().topology(), id)
                : b.world().subsystem(FoundrySubsystem.class).unit(b.world().topology(), id);
        h.assertTrue(type.isInstance(u), "unit " + type.getSimpleName() + " at " + rel);
        return type.cast(u);
    }

    @GameTest(template = LARGE)
    public static void everyMachineGetsItsDomainTwin(GameTestHelper h) {
        h.setBlock(new BlockPos(1, 1, 1), PcBlocks.COAL_JIG.get().defaultBlockState());
        h.setBlock(new BlockPos(4, 2, 1), PcBlocks.CUPOLA_FURNACE.get().defaultBlockState());
        h.setBlock(new BlockPos(6, 1, 1), PcBlocks.CRUCIBLE_FURNACE.get().defaultBlockState());
        h.setBlock(new BlockPos(8, 1, 1), PcBlocks.SAND_MOLD.get().defaultBlockState());
        h.setBlock(new BlockPos(1, 1, 5), PcBlocks.MOLDING_STATION.get().defaultBlockState());
        h.setBlock(new BlockPos(4, 1, 5), PcBlocks.SHAKEOUT_GRATE.get().defaultBlockState());
        h.setBlock(new BlockPos(7, 1, 5), PcBlocks.MACHINE_LATHE.get().defaultBlockState());
        h.setBlock(new BlockPos(10, 1, 5), PcBlocks.DRILL_PRESS.get().defaultBlockState());
        h.succeedWhen(() -> {
            unit(h, new BlockPos(1, 1, 1), CoalJig.class);
            unit(h, new BlockPos(4, 2, 1), CupolaUnit.class);
            unit(h, new BlockPos(6, 1, 1), CrucibleUnit.class);
            unit(h, new BlockPos(8, 1, 1), SandMoldUnit.class);
            unit(h, new BlockPos(1, 1, 5), CyclicMachine.class);
            unit(h, new BlockPos(4, 1, 5), CyclicMachine.class);
            h.assertTrue(unit(h, new BlockPos(7, 1, 5), MachineToolUnit.class).kind() == MachineToolUnit.Kind.LATHE, "lathe");
            h.assertTrue(unit(h, new BlockPos(10, 1, 5), MachineToolUnit.class).kind() == MachineToolUnit.Kind.DRILL_PRESS, "drill");
        });
    }

    /** Токарный на валу 64 об/мин: корпус задвижки точится ≈ 41 с, резец изнашивается, нагрузка видна в сети Create. */
    @GameTest(template = LARGE, timeoutTicks = 1400)
    public static void latheTurnsACastingOnACreateShaft(GameTestHelper h) {
        BlockPos lathe = new BlockPos(6, 2, 6);
        h.setBlock(lathe, facing(PcBlocks.MACHINE_LATHE.get().defaultBlockState(), Direction.NORTH));
        motor(h, lathe.west(), Direction.EAST, 64);
        insert(h, lathe, new ItemStack(PcItems.CUTTING_TOOL.get()));
        h.assertTrue(items(h, lathe).insertItem(0, casting(CastingKind.WHEEL, "gray_iron", CastingDefect.CHILL), true).getCount() == 1,
                "chilled iron is refused");
        insert(h, lathe, casting(CastingKind.VALVE_BODY, "gray_iron", CastingDefect.NONE));
        h.runAfterDelay(100, () -> {
            KineticMachineBlockEntity<?> be = h.getBlockEntity(lathe);
            h.assertTrue(Math.abs(be.getSpeed()) == 64, "lathe gets 64 rpm from the shaft, got " + be.getSpeed());
            h.assertTrue(be.calculateStressApplied() > 0, "lathe declares stress");
            h.assertBlockProperty(lathe, PcProperties.RUNNING, true);
        });
        h.succeedWhen(() -> {
            ItemStack out = find(items(h, lathe), PcItems.CASTING.get());
            h.assertFalse(out.isEmpty(), "turned casting");
            CastingSpec c = out.get(FoundryComponents.CASTING_SPEC.get());
            h.assertTrue(c.machinedMask() == CastingSpec.TURNED && c.serial() == 42, "turned mark kept on the same casting");
            ItemStack tool = find(items(h, lathe), PcItems.CUTTING_TOOL.get());
            h.assertTrue(tool.getDamageValue() >= 3 && tool.getDamageValue() <= 6, "tool wear ≈ 4 %, damage " + tool.getDamageValue());
        });
    }

    /** Вагранка на дутье выпускает чугун в форму на выбивной решётке; остывшая форма выбивается в отливку и смесь. */
    @GameTest(template = LARGE, timeoutTicks = 1600)
    public static void cupolaPoursAMoldThatTheGrateKnocksOut(GameTestHelper h) {
        BlockPos grate = new BlockPos(3, 1, 3);
        BlockPos cupola = new BlockPos(5, 3, 3);                          // клетки y 2…4, лётка у нижней
        BlockPos mold = new BlockPos(4, 2, 3);                            // на решётке, рядом с низом вагранки
        h.setBlock(grate, facing(PcBlocks.SHAKEOUT_GRATE.get().defaultBlockState(), Direction.NORTH));
        motor(h, grate.west(), Direction.EAST, 64);
        h.setBlock(cupola, PcBlocks.CUPOLA_FURNACE.get().defaultBlockState());
        motor(h, cupola.east(), Direction.WEST, 64);
        h.setBlock(mold, PcBlocks.SAND_MOLD.get().defaultBlockState());
        h.runAfterDelay(2, () -> {
            ((SandMoldBlockEntity) h.getBlockEntity(mold)).setMold(new FoundryComponents.Mold(CastingKind.VALVE_BODY, true, 0.05f));
            insert(h, cupola, new ItemStack(PcItems.PIG_IRON.get(), 4));
            insert(h, cupola, new ItemStack(Items.CHARCOAL));
        });
        h.startSequence()
                .thenWaitUntil(() -> h.assertBlockProperty(mold, PcProperties.MOLD_STAGE, PcProperties.MoldStage.POURED))
                .thenExecute(() -> h.assertBlockProperty(cupola, BlockStateProperties.LIT, true))
                .thenWaitUntil(() -> h.assertBlockPresent(Blocks.AIR, mold))
                .thenExecute(() -> {
                    IItemHandler out = items(h, grate);
                    CastingSpec c = find(out, PcItems.CASTING.get()).get(FoundryComponents.CASTING_SPEC.get());
                    h.assertTrue(c != null && c.kind() == CastingKind.VALVE_BODY && c.alloyId().equals("gray_iron") && c.dn() == 100,
                            "valve body in grey iron, got " + c);
                    h.assertTrue(c.defect() == CastingDefect.NONE, "sound casting, got " + c.defect());
                    h.assertTrue(find(out, PcItems.FOUNDRY_SAND.get()).getCount() == 2, "90 % of the sand back");
                })
                .thenSucceed();
    }

    /** Тигель: медь на угле плавится и выливается в форму перед печью. */
    @GameTest(template = LARGE, timeoutTicks = 1200)
    public static void crucibleMeltsCopperAndPoursTheMoldInFront(GameTestHelper h) {
        BlockPos crucible = new BlockPos(5, 2, 5);
        BlockPos mold = crucible.south();
        h.setBlock(crucible, facing(PcBlocks.CRUCIBLE_FURNACE.get().defaultBlockState(), Direction.SOUTH));
        h.setBlock(mold, PcBlocks.SAND_MOLD.get().defaultBlockState());
        h.runAfterDelay(2, () -> {
            ((SandMoldBlockEntity) h.getBlockEntity(mold)).setMold(new FoundryComponents.Mold(CastingKind.BEARING_HOUSING, true, 0.05f));
            insert(h, crucible, new ItemStack(Items.COPPER_INGOT));
            insert(h, crucible, new ItemStack(Items.COAL));
        });
        h.succeedWhen(() -> {
            h.assertBlockProperty(mold, PcProperties.MOLD_STAGE, PcProperties.MoldStage.POURED);
            h.assertBlockProperty(crucible, BlockStateProperties.LIT, true);
            CrucibleUnit u = unit(h, crucible, CrucibleUnit.class);
            h.assertTrue(Math.abs(u.furnace().massKg() - 75) < 1e-6, "25 kg poured of 100");
        });
    }

    /** Формовочная машина: смесь и вода вокруг модели → заформованная форма с моделью и влажностью. */
    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void moldingMachineRamsASandMold(GameTestHelper h) {
        BlockPos station = new BlockPos(5, 2, 5);
        h.setBlock(station, facing(PcBlocks.MOLDING_STATION.get().defaultBlockState(), Direction.NORTH));
        motor(h, station.west(), Direction.EAST, 64);
        h.runAfterDelay(2, () -> {
            insert(h, station, new ItemStack(PcItems.FOUNDRY_SAND.get(), 2));
            ItemStack pattern = new ItemStack(PcItems.CASTING_PATTERN.get());
            pattern.set(FoundryComponents.CASTING_PATTERN.get(), new FoundryComponents.Pattern(CastingKind.VALVE_BODY, false));
            insert(h, station, pattern);
            IFluidHandler water = ((FoundryDevice) h.getBlockEntity(station)).fluids(null);
            h.assertTrue(water.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE) == 1000, "water");
        });
        h.succeedWhen(() -> {
            ItemStack mold = find(items(h, station), PcBlocks.SAND_MOLD.get().asItem());
            h.assertFalse(mold.isEmpty(), "rammed mould");
            FoundryComponents.Mold m = mold.get(FoundryComponents.SAND_MOLD.get());
            h.assertTrue(m != null && m.kind() == CastingKind.VALVE_BODY && !m.riser() && Math.abs(m.moisture() - 0.05f) < 1e-6,
                    "mould keeps the pattern and 5 % moisture, got " + m);
            h.assertTrue(find(items(h, station), PcItems.CASTING_PATTERN.get()).getCount() == 1, "pattern is not consumed");
        });
    }

    /** Отсадочная машина: рядовой уголь → мытый уголь чище питания, с анализом. */
    @GameTest(template = LARGE, timeoutTicks = 900)
    public static void coalJigWashesRunOfMineCoal(GameTestHelper h) {
        BlockPos jig = new BlockPos(5, 2, 5);
        h.setBlock(jig, facing(PcBlocks.COAL_JIG.get().defaultBlockState(), Direction.NORTH));
        motor(h, jig.west(), Direction.EAST, 64);
        h.runAfterDelay(2, () -> {
            insert(h, jig, new ItemStack(PcItems.RAW_COAL.get(), 3));
            IFluidHandler water = ((FoundryDevice) h.getBlockEntity(jig)).fluids(null);
            h.assertTrue(water.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE) == 1000, "water");
        });
        h.succeedWhen(() -> {
            ItemStack washed = find(items(h, jig), PcItems.WASHED_COAL.get());
            h.assertFalse(washed.isEmpty(), "washed coal");
            CoalAssayData a = washed.get(PcDataComponents.COAL_ASSAY.get());
            h.assertTrue(a != null && a.ashPct() < 21, "cleaner than the feed (21 % ash), got " + a);
            h.assertBlockProperty(jig, PcProperties.RUNNING, true);
        });
    }
}

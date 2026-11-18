package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.binding.DeviceHost;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.FoundryComponents;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.metallurgy.CastingDefect;
import dev.denfry.promptcritical.sim.metallurgy.CastingKind;
import dev.denfry.promptcritical.sim.metallurgy.CastingSpec;
import dev.denfry.promptcritical.sim.metallurgy.FoundrySubsystem;
import dev.denfry.promptcritical.sim.metallurgy.ProductionToolUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Станочный парк в мире: фрезерный, строгальный и плоскошлифовальный станки на валу креативного мотора Create, заготовка и
 * инструмент через склад станка (как воронкой), на выходе — деталь с компонентом {@code machined_finish}.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MachineShopGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";

    private MachineShopGameTests() {
    }

    /** Станок фасадом на север, мотор с запада (вал слева). */
    private static void machine(GameTestHelper h, BlockPos rel, Block block, int rpm) {
        h.setBlock(rel, block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        BlockPos motor = rel.west();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(rpm));
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

    private static ItemStack find(IItemHandler inv, Item item) {
        for (int i = 0; i < inv.getSlots(); i++) if (inv.getStackInSlot(i).is(item)) return inv.getStackInSlot(i);
        return ItemStack.EMPTY;
    }

    private static ItemStack casting(CastingKind kind, int machined) {
        ItemStack s = new ItemStack(PcItems.CASTING.get());
        s.set(FoundryComponents.CASTING_SPEC.get(), new CastingSpec(kind, "gray_iron", 0, CastingDefect.NONE, 0, 42, machined));
        return s;
    }

    private static ProductionToolUnit unit(GameTestHelper h, BlockPos rel) {
        SimLevelBinding b = SimBindings.get(h.getLevel());
        h.assertTrue(b != null, "simulation binding");
        Object u = b.world().subsystem(FoundrySubsystem.class).unit(b.world().topology(), ((DeviceHost) h.getBlockEntity(rel)).simId());
        h.assertTrue(u instanceof ProductionToolUnit, "machine-tool twin at " + rel);
        return (ProductionToolUnit) u;
    }

    private static FoundryComponents.Finish finish(ItemStack s) {
        return s.get(FoundryComponents.MACHINED_FINISH.get());
    }

    /**
     * Фрезерный на 64 об/мин: точёная заготовка шестерни — зубчатое колесо за ≈ 66 с (f_z 0,08, Ra 1,6, IT8); неточёную
     * станок не берёт, нагрузка видна в сети Create.
     */
    @GameTest(template = LARGE, timeoutTicks = 1700)
    public static void millingMachineCutsAGearFromATurnedBlank(GameTestHelper h) {
        BlockPos mill = new BlockPos(5, 2, 5);
        machine(h, mill, PcBlocks.MILLING_MACHINE.get(), 64);
        insert(h, mill, new ItemStack(PcItems.MILLING_CUTTER.get()));
        h.assertTrue(items(h, mill).insertItem(0, casting(CastingKind.GEAR_BLANK, 0), true).getCount() == 1,
                "an unturned gear blank is refused");
        insert(h, mill, casting(CastingKind.GEAR_BLANK, CastingSpec.TURNED));
        h.runAfterDelay(100, () -> {
            h.assertTrue(unit(h, mill).kind() == ProductionToolUnit.Kind.MILLING, "milling twin");
            KineticMachineBlockEntity<?> be = h.getBlockEntity(mill);
            h.assertTrue(Math.abs(be.getSpeed()) == 64, "64 rpm from the shaft, got " + be.getSpeed());
            h.assertTrue(be.calculateStressApplied() > 0, "milling machine declares stress");
            h.assertBlockProperty(mill, PcProperties.RUNNING, true);
            h.assertTrue(unit(h, mill).speed() == 128, "spindle ×2");
        });
        h.succeedWhen(() -> {
            ItemStack gear = find(items(h, mill), PcItems.SPUR_GEAR.get());
            h.assertFalse(gear.isEmpty(), "spur gear");
            FoundryComponents.Finish f = finish(gear);
            h.assertTrue(f != null && f.itGrade() == 8 && Math.abs(f.raUm() - 1.6f) < 0.01f && f.material().equals("cast_iron"),
                    "Ra 1.6, IT8, cast iron, got " + f);
        });
    }

    /** Строгальный на 64 об/мин (32 дв. хода/мин), подача 0,5: отливка плиты — строганая плита Ra 7,8 за ≈ 50 с. */
    @GameTest(template = LARGE, timeoutTicks = 1400)
    public static void shapingMachinePlanesABasePlateCasting(GameTestHelper h) {
        BlockPos shaper = new BlockPos(5, 2, 5);
        machine(h, shaper, PcBlocks.SHAPING_MACHINE.get(), 64);
        insert(h, shaper, new ItemStack(PcItems.CUTTING_TOOL.get()));
        h.runAfterDelay(2, () -> unit(h, shaper).setFeedStep(2));
        insert(h, shaper, casting(CastingKind.BASE_PLATE, 0));
        h.runAfterDelay(100, () -> {
            h.assertTrue(unit(h, shaper).speed() == 32, "32 double strokes a minute");
            h.assertBlockProperty(shaper, PcProperties.RUNNING, true);
        });
        h.succeedWhen(() -> {
            ItemStack plate = find(items(h, shaper), PcItems.PLANED_PLATE.get());
            h.assertFalse(plate.isEmpty(), "planed plate");
            FoundryComponents.Finish f = finish(plate);
            h.assertTrue(f != null && f.itGrade() == 12 && Math.abs(f.raUm() - 7.81f) < 0.02f, "Ra 7.8, IT12, got " + f);
        });
    }

    /** Шлифовальный на 64 об/мин (круг 2688 об/мин, 35 м/с), a 0,02: строганая плита — шлифованная Ra ≈ 0,49, IT7. */
    @GameTest(template = LARGE, timeoutTicks = 1800)
    public static void surfaceGrinderGrindsAPlanedPlate(GameTestHelper h) {
        BlockPos grinder = new BlockPos(5, 2, 5);
        machine(h, grinder, PcBlocks.SURFACE_GRINDER.get(), 64);
        insert(h, grinder, new ItemStack(PcItems.GRINDING_WHEEL.get()));
        h.runAfterDelay(2, () -> unit(h, grinder).setFeedStep(2));
        ItemStack planed = new ItemStack(PcItems.PLANED_PLATE.get());
        planed.set(FoundryComponents.MACHINED_FINISH.get(), new FoundryComponents.Finish("cast_iron", 7.81f, 12));
        insert(h, grinder, planed);
        h.runAfterDelay(100, () -> {
            h.assertTrue(unit(h, grinder).speed() == 2688, "wheel ×42");
            h.assertBlockProperty(grinder, PcProperties.RUNNING, true);
        });
        h.succeedWhen(() -> {
            ItemStack ground = find(items(h, grinder), PcItems.GROUND_PLATE.get());
            h.assertFalse(ground.isEmpty(), "ground plate");
            FoundryComponents.Finish f = finish(ground);
            h.assertTrue(f != null && f.itGrade() == 7 && f.raUm() > 0.4f && f.raUm() < 0.6f, "Ra ≈ 0.49, IT7, got " + f);
            ItemStack wheel = find(items(h, grinder), PcItems.GRINDING_WHEEL.get());
            h.assertTrue(wheel.getDamageValue() == 5, "wheel loaded 5 % by 1000 of 20 000 mm³, damage " + wheel.getDamageValue());
        });
    }

    /** Круг на валу 128 об/мин — 70 м/с, вдвое выше рабочей: его разрывает, слот инструмента пустеет. */
    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void overspedGrindingWheelBursts(GameTestHelper h) {
        BlockPos grinder = new BlockPos(5, 2, 5);
        machine(h, grinder, PcBlocks.SURFACE_GRINDER.get(), 128);
        insert(h, grinder, new ItemStack(PcItems.GRINDING_WHEEL.get()));
        h.succeedWhen(() -> {
            h.assertTrue(find(items(h, grinder), PcItems.GRINDING_WHEEL.get()).isEmpty(), "the wheel burst");
            h.assertTrue(Double.isNaN(unit(h, grinder).toolWear()), "no wheel in the twin");
        });
    }
}

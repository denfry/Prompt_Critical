package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.ironworks.BlastFurnaceControllerBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.CastHouseMachineBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.CokeBatteryShape;
import dev.denfry.promptcritical.mod.ironworks.CokeOvenBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.ironworks.IronworksComponents;
import dev.denfry.promptcritical.mod.ironworks.LadleBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.metallurgy.BlastFurnaceState;
import dev.denfry.promptcritical.sim.metallurgy.BlastFurnaceUnit;
import dev.denfry.promptcritical.sim.metallurgy.CokeBattery;
import dev.denfry.promptcritical.sim.metallurgy.CokeBatteryUnit;
import dev.denfry.promptcritical.sim.metallurgy.IronmakingConfig;
import dev.denfry.promptcritical.sim.metallurgy.OvenState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Коксохим и домна в мире (этап 10а M3, 08 §8.4–8.5): батарея и домна собираются свободно и находятся валидатором,
 * машины подготовки руды работают на валу Create, чугун в ковше остывает до «козла».
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IronworksGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final String TALL = "gametest/empty_12x24x12";

    private IronworksGameTests() {
    }

    private static SimLevelBinding binding(GameTestHelper h) {
        SimLevelBinding b = SimBindings.get(h.getLevel());
        if (b == null) throw new IllegalStateException("no simulation binding for the test level");
        return b;
    }

    private static BlockState facing(BlockState s, Direction d) {
        return s.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
    }

    private static void motor(GameTestHelper h, BlockPos rel, Direction toward, int rpm) {
        h.setBlock(rel, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, toward));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(rel)).generatedSpeed.setValue(rpm));
    }

    private static IItemHandler items(GameTestHelper h, BlockPos rel, Direction side) {
        return ((FoundryDevice) h.getBlockEntity(rel)).items(side);
    }

    private static int insert(IItemHandler inv, ItemStack stack) {
        ItemStack rest = stack;
        for (int i = 0; i < inv.getSlots() && !rest.isEmpty(); i++) rest = inv.insertItem(i, rest, false);
        return stack.getCount() - rest.getCount();
    }

    private static int count(IItemHandler inv, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < inv.getSlots(); i++) if (inv.getStackInSlot(i).is(item)) n += inv.getStackInSlot(i).getCount();
        return n;
    }

    // --- коксовая батарея ---

    /**
     * Батарея 3 камеры × 4 блока вдоль X: простенки z = 1, 3, 5, 7, камеры z = 2, 4, 6, x = 3…6, y = 1…3; стояки
     * газосборника над первой клеткой каждой камеры.
     */
    private static void buildBattery(GameTestHelper h) {
        for (int z = 1; z <= 7; z++)
            for (int x = 3; x <= 6; x++)
                for (int y = 1; y <= 3; y++)
                    h.setBlock(new BlockPos(x, y, z), z % 2 == 1 ? PcBlocks.COKE_BATTERY_WALL.get().defaultBlockState()
                            : PcBlocks.COKE_OVEN.get().defaultBlockState());
        for (int z = 2; z <= 6; z += 2) h.setBlock(new BlockPos(3, 4, z), PcBlocks.GAS_COLLECTOR_MAIN.get().defaultBlockState());
    }

    private static CokeBatteryUnit batteryUnit(GameTestHelper h, CokeOvenBlockEntity master) {
        CokeBatteryUnit u = Ironworks.unit(binding(h), master.simId(), CokeBatteryUnit.class);
        h.assertTrue(u != null && u.battery() != null, "battery twin");
        return u;
    }

    /** Простенки греются до 1300 °C со скоростью разогрева из конфига и держатся (в обход топок, для теста). */
    private static void cookUntilReady(CokeBattery bat, int oven) {
        IronmakingConfig cfg = IronmakingConfig.defaults();
        double dt = 600, allowed = (CokeBattery.WALL_HOT_K - 293.15) / (cfg.heatupDaysPhys() * 86_400);
        for (int k = 0; k < 20_000 && bat.oven(oven) != OvenState.READY; k++) {
            double t = bat.wallTempK();
            double rate = t < 1573.15 ? 0.9 * allowed : (1573.15 - t) / 3600;
            bat.setExtraHeat(Math.max(0, bat.wallHeatCapacityJK() * rate + bat.coolingW()));
            bat.stepSlow(dt, null);
        }
        bat.setExtraHeat(0);
    }

    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void cokeBatteryFormsChargesAndPushesIntoAChest(GameTestHelper h) {
        buildBattery(h);
        BlockPos origin = new BlockPos(3, 1, 2);
        BlockPos hatch = new BlockPos(5, 3, 2);
        BlockPos door = new BlockPos(7, 1, 2);
        h.startSequence()
                .thenWaitUntil(() -> {
                    CokeOvenBlockEntity m = ((CokeOvenBlockEntity) h.getBlockEntity(origin)).master();
                    h.assertTrue(m != null && m.shape() != null, "battery formed");
                })
                .thenExecute(() -> {
                    CokeOvenBlockEntity m = ((CokeOvenBlockEntity) h.getBlockEntity(origin)).master();
                    CokeBatteryShape s = m.shape();
                    h.assertTrue(s.ovens() == 3 && s.length() == 4 && s.axis() == Direction.Axis.X, "3 ovens × 4 along X, got " + s);
                    CokeBattery bat = batteryUnit(h, m).battery();
                    int took = insert(items(h, hatch, Direction.UP), new ItemStack(PcItems.WASHED_COAL.get(), 64));
                    h.assertTrue(took > 30 && took < 64, "hatch takes one oven charge, took " + took);
                    h.assertTrue(bat.chargeKg(0) >= bat.designChargeKg() - Ironworks.bulkKg(), "oven 0 charged");
                    h.assertTrue(m.ramPush(h.absolutePos(origin), Direction.EAST) == CokeOvenBlockEntity.PushOutcome.OVERLOAD,
                            "green oven overloads the pusher");
                    cookUntilReady(bat, 0);
                    h.assertTrue(bat.oven(0) == OvenState.READY, "oven 0 ready, state " + bat.oven(0));
                    h.setBlock(door, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST));
                })
                .thenExecuteAfter(2, () -> {
                    CokeOvenBlockEntity m = ((CokeOvenBlockEntity) h.getBlockEntity(origin)).master();
                    h.assertTrue(m.ramPush(h.absolutePos(origin), Direction.EAST) == CokeOvenBlockEntity.PushOutcome.PUSHED, "pushed");
                    ChestBlockEntity chest = (ChestBlockEntity) h.getBlockEntity(door);
                    int coke = 0, breeze = 0;
                    boolean hot = false;
                    for (int i = 0; i < chest.getContainerSize(); i++) {
                        ItemStack st = chest.getItem(i);
                        if (st.is(PcItems.COKE.get())) {
                            coke += st.getCount();
                            IronworksComponents.CokeAssay a = st.get(IronworksComponents.COKE_ASSAY.get());
                            hot |= a != null && a.hot();
                        }
                        if (st.is(PcItems.COKE_BREEZE.get())) breeze += st.getCount();
                    }
                    h.assertTrue(coke >= 20, "coke in the chest, got " + coke);
                    h.assertTrue(hot, "fresh coke is hot");
                    h.assertTrue(breeze >= 1, "breeze in the chest, got " + breeze);
                    h.assertTrue(batteryUnit(h, m).battery().oven(0) == OvenState.EMPTY, "oven empty after push");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 300)
    public static void cokeBatteryReportsAMissingWall(GameTestHelper h) {
        buildBattery(h);
        h.setBlock(new BlockPos(4, 2, 7), Blocks.STONE);
        BlockPos origin = new BlockPos(3, 1, 2);
        h.runAfterDelay(120, () -> {
            CokeOvenBlockEntity c = (CokeOvenBlockEntity) h.getBlockEntity(origin);
            h.assertTrue(c.master() == null, "no battery without the end wall");
            h.assertTrue(CokeBatteryShape.scan(h.getLevel(), h.absolutePos(origin)).problem().equals("wall_missing"),
                    "end wall missing: " + CokeBatteryShape.scan(h.getLevel(), h.absolutePos(origin)).problem());
            h.succeed();
        });
    }

    // --- домна ---

    /**
     * Домна 5×5 по середине (5, 5), лещадь y = 1, кожух до y = 12 (V_u = 99 м³), лётка (5, 2, 3) на севере, фурмы в
     * слое y = 3 по серединам сторон, колошник (5, 13, 5), пульт (2, 4, 5) у западной стенки.
     */
    private static void buildFurnace(GameTestHelper h) {
        BlockState shell = PcBlocks.BLAST_FURNACE_SHELL.get().defaultBlockState();
        for (int y = 1; y <= 12; y++)
            for (int x = 3; x <= 7; x++)
                for (int z = 3; z <= 7; z++) {
                    boolean ring = x == 3 || x == 7 || z == 3 || z == 7;
                    if (y == 1 || ring) h.setBlock(new BlockPos(x, y, z), shell);
                }
        h.setBlock(new BlockPos(5, 2, 3), facing(PcBlocks.BLAST_FURNACE_TAPHOLE.get().defaultBlockState(), Direction.NORTH));
        BlockState tuyere = PcBlocks.BLAST_FURNACE_TUYERE.get().defaultBlockState();
        h.setBlock(new BlockPos(5, 3, 3), facing(tuyere, Direction.NORTH));
        h.setBlock(new BlockPos(5, 3, 7), facing(tuyere, Direction.SOUTH));
        h.setBlock(new BlockPos(3, 3, 5), facing(tuyere, Direction.WEST));
        h.setBlock(new BlockPos(7, 3, 5), facing(tuyere, Direction.EAST));
        h.setBlock(new BlockPos(5, 13, 5), PcBlocks.BLAST_FURNACE_TOP.get().defaultBlockState());
        h.setBlock(new BlockPos(2, 4, 5), facing(PcBlocks.BLAST_FURNACE_CONTROLLER.get().defaultBlockState(), Direction.WEST));
    }

    @GameTest(template = TALL, timeoutTicks = 300)
    public static void blastFurnaceFormsTakesChargeAndRefusesToDrillCold(GameTestHelper h) {
        buildFurnace(h);
        BlockPos controller = new BlockPos(2, 4, 5);
        BlockPos top = new BlockPos(5, 13, 5);
        BlockPos drill = new BlockPos(4, 1, 1);
        h.setBlock(drill, facing(PcBlocks.CAST_HOUSE_MACHINE.get().defaultBlockState(), Direction.SOUTH));
        h.startSequence()
                .thenWaitUntil(() -> {
                    BlastFurnaceControllerBlockEntity c = (BlastFurnaceControllerBlockEntity) h.getBlockEntity(controller);
                    h.assertTrue(c.shape() != null && c.unit() != null && c.unit().furnace() != null, "furnace formed");
                })
                .thenExecute(() -> {
                    BlastFurnaceControllerBlockEntity c = (BlastFurnaceControllerBlockEntity) h.getBlockEntity(controller);
                    h.assertTrue(c.shape().usefulVolumeM3() == 99 && c.shape().tuyeres().size() == 4,
                            "V_u 99 m³ and 4 tuyeres, got " + c.shape());
                    BlastFurnaceUnit u = c.unit();
                    h.assertTrue(u.furnace().state() == BlastFurnaceState.COLD, "cold furnace");
                    ItemStack coke = new ItemStack(PcItems.COKE.get(), 5);
                    coke.set(IronworksComponents.COKE_ASSAY.get(), IronworksComponents.CokeAssay.DEFAULT);
                    IItemHandler hopper = items(h, top, Direction.UP);
                    h.assertTrue(insert(hopper, coke) == 5, "furnace top takes coke");
                    ItemStack hot = new ItemStack(PcItems.COKE.get());
                    hot.set(IronworksComponents.COKE_ASSAY.get(), new IronworksComponents.CokeAssay(11, 6, 82, 4, true));
                    h.assertTrue(insert(hopper, hot) == 0, "hot coke is refused");
                    h.assertTrue(insert(hopper, new ItemStack(PcItems.SINTER.get(), 3)) == 3, "furnace top takes sinter");
                    h.assertTrue(u.hopperCokeKg() >= 5 * Ironworks.bulkKg() - 1e-6 || u.furnace().layerCount() > 0, "coke in the hopper");
                    String r = ((CastHouseMachineBlockEntity) h.getBlockEntity(drill)).act();
                    h.assertTrue(r.equals("cast_house_drill_failed"), "cold hearth cannot be drilled, got " + r);
                })
                .thenSucceed();
    }

    @GameTest(template = TALL, timeoutTicks = 300)
    public static void blastFurnaceNeedsATaphole(GameTestHelper h) {
        buildFurnace(h);
        h.setBlock(new BlockPos(5, 2, 3), PcBlocks.BLAST_FURNACE_SHELL.get().defaultBlockState());
        BlockPos controller = new BlockPos(2, 4, 5);
        h.runAfterDelay(100, () -> {
            BlastFurnaceControllerBlockEntity c = (BlastFurnaceControllerBlockEntity) h.getBlockEntity(controller);
            h.assertTrue(c.shape() == null, "no furnace without a taphole");
            h.assertTrue(dev.denfry.promptcritical.mod.ironworks.BlastFurnaceShape.scan(h.getLevel(), h.absolutePos(controller))
                    .problem().equals("taphole_count"), "taphole problem");
            h.succeed();
        });
    }

    // --- ковш ---

    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void ladleChillsHotMetalIntoASkull(GameTestHelper h) {
        BlockPos ladle = new BlockPos(5, 1, 5);
        h.setBlock(ladle, PcBlocks.LADLE.get().defaultBlockState());
        h.runAfterDelay(2, () -> {
            LadleBlockEntity l = (LadleBlockEntity) h.getBlockEntity(ladle);
            h.assertTrue(l.receive(1000, 1700, 0.045, 0.006, 0.0003) == 1000, "ladle takes a tonne");
            h.assertTrue(l.tempK() < 1700, "cold lining chills the metal");
        });
        h.startSequence()
                .thenWaitUntil(() -> h.assertBlockProperty(ladle, PcProperties.FILLED, true))
                .thenWaitUntil(() -> h.assertTrue(((LadleBlockEntity) h.getBlockEntity(ladle)).frozen(), "frozen"))
                .thenExecute(() -> {
                    LadleBlockEntity l = (LadleBlockEntity) h.getBlockEntity(ladle);
                    h.assertTrue(l.tempK() < LadleBlockEntity.SOLIDUS_K && l.roomKg() == 0, "skull blocks the ladle");
                })
                .thenWaitUntil(() -> h.assertBlockProperty(ladle, PcProperties.FILLED, false))
                .thenSucceed();
    }

    // --- подготовка руды ---

    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void magneticSeparatorConcentratesMagnetite(GameTestHelper h) {
        BlockPos sep = new BlockPos(5, 1, 5);
        h.setBlock(sep, facing(PcBlocks.MAGNETIC_SEPARATOR.get().defaultBlockState(), Direction.NORTH));
        motor(h, sep.west(), Direction.EAST, 64);
        h.runAfterDelay(2, () -> h.assertTrue(insert(items(h, sep, null), new ItemStack(PcItems.RAW_MAGNETITE.get(), 8)) == 8, "feed"));
        h.succeedWhen(() -> {
            IItemHandler inv = items(h, sep, null);
            h.assertTrue(count(inv, PcItems.IRON_ORE_CONCENTRATE.get()) >= 1, "concentrate");
            h.assertTrue(count(inv, PcItems.STONE_DUST.get()) >= 1, "tailings");
        });
    }

    @GameTest(template = LARGE, timeoutTicks = 900)
    public static void sinterStrandSintersConcentrateWithBreezeAndFlux(GameTestHelper h) {
        BlockPos strand = new BlockPos(6, 1, 6);
        h.setBlock(strand, facing(PcBlocks.SINTER_STRAND.get().defaultBlockState(), Direction.NORTH));
        motor(h, strand.west(2), Direction.EAST, 64);
        h.runAfterDelay(2, () -> {
            IItemHandler inv = items(h, strand, null);
            h.assertTrue(insert(inv, new ItemStack(PcItems.IRON_ORE_CONCENTRATE.get(), 10)) == 10, "ore");
            h.assertTrue(insert(inv, new ItemStack(PcItems.COKE_BREEZE.get(), 2)) == 2, "breeze");
            h.assertTrue(insert(inv, new ItemStack(Items.CALCITE, 8)) == 8, "flux");
            IFluidHandler gas = ((FoundryDevice) h.getBlockEntity(strand)).fluids(null);
            h.assertTrue(gas.fill(new FluidStack(PcFluids.COKE_OVEN_GAS.source.get(), 100), IFluidHandler.FluidAction.EXECUTE) == 100,
                    "ignition gas");
        });
        h.succeedWhen(() -> {
            IItemHandler inv = items(h, strand, null);
            ItemStack sinter = ItemStack.EMPTY;
            for (int i = 0; i < inv.getSlots(); i++) if (inv.getStackInSlot(i).is(PcItems.SINTER.get())) sinter = inv.getStackInSlot(i);
            h.assertTrue(!sinter.isEmpty(), "sinter");
            IronworksComponents.SinterAssay a = sinter.get(IronworksComponents.SINTER_ASSAY.get());
            h.assertTrue(a != null && a.fePct() >= 50 && a.fePct() <= 64, "sinter Fe 50–64 %, got " + a);
        });
    }
}

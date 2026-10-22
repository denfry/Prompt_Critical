package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.ironworks.GasBuffer;
import dev.denfry.promptcritical.mod.ops.WaterTreatmentBlockEntity;
import dev.denfry.promptcritical.mod.powerhouse.DeaeratorBlockEntity;
import dev.denfry.promptcritical.mod.powerhouse.WaterTubeBoilerBlockEntity;
import dev.denfry.promptcritical.mod.powerhouse.WaterTubeBoilerShape;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.combustion.BmsResponse;
import dev.denfry.promptcritical.sim.combustion.BmsState;
import dev.denfry.promptcritical.sim.combustion.BoilerRupture;
import dev.denfry.promptcritical.sim.combustion.MftCause;
import dev.denfry.promptcritical.sim.metallurgy.ProcessGas;
import dev.denfry.promptcritical.sim.plant.SteamPlant;
import dev.denfry.promptcritical.sim.plant.WaterTubeUnit;
import dev.denfry.promptcritical.sim.utilities.WaterGrade;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Котёл I1 в мире (этап 11б M3, 07 §7.9.5–7.9.6): сборка топки 5×8×5 со щитом, вентиляторы на валу Create, газ и вода
 * в щит; T7.24 — топливо без продувки ({@code E_INTERLOCK}), проба без пламени (MFT ≤ 1 с после 10 с), байпас
 * датчиков пламени — взрыв топки; деаэратор на паре барабана.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PowerhouseGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    /** Топка: x 1…5, y 1…8, z 1…5; щит — у северной стены снаружи (z = 0). */
    private static final BlockPos MIN = new BlockPos(1, 1, 1), MAX = new BlockPos(5, 8, 5);
    private static final BlockPos PANEL = new BlockPos(3, 3, 0);
    private static final BlockPos FD_FAN = new BlockPos(8, 1, 3), ID_FAN = new BlockPos(10, 1, 3);
    private static final BlockPos DEAERATOR = new BlockPos(8, 1, 8);
    private static final UUID PLAYER = new UUID(7, 7);

    private PowerhouseGameTests() {
    }

    private static SimLevelBinding binding(GameTestHelper h) {
        SimLevelBinding b = SimBindings.get(h.getLevel());
        if (b == null) throw new IllegalStateException("no simulation binding for the test level");
        return b;
    }

    private static SteamPlant plant(GameTestHelper h) {
        return binding(h).world().subsystem(SteamPlant.class);
    }

    private static WaterTubeBoilerBlockEntity panel(GameTestHelper h) {
        return h.getBlockEntity(PANEL);
    }

    private static WaterTubeUnit unit(GameTestHelper h) {
        WaterTubeUnit u = panel(h).unit();
        h.assertTrue(u != null, "boiler twin exists");
        return u;
    }

    private static BlockState facing(BlockState s, Direction d) {
        return s.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
    }

    private static void motor(GameTestHelper h, BlockPos rel, Direction toward, int rpm) {
        h.setBlock(rel, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, toward));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(rel)).generatedSpeed.setValue(rpm));
    }

    /**
     * Котёл: топка из экранов, две газовые горелки в боковых стенах, барабан на крыше, по три блока пароперегревателя
     * и экономайзера и два воздухоподогревателя у восточной стены, щит у северной; дутьевой вентилятор и дымосос на
     * моторах 64 об/мин; в щите шахтный метан и вода.
     */
    private static void build(GameTestHelper h) {
        BlockState wall = PcBlocks.BOILER_WALL.get().defaultBlockState();
        for (int x = MIN.getX(); x <= MAX.getX(); x++)
            for (int y = MIN.getY(); y <= MAX.getY(); y++)
                for (int z = MIN.getZ(); z <= MAX.getZ(); z++) {
                    boolean shell = x == MIN.getX() || x == MAX.getX() || y == MIN.getY() || y == MAX.getY()
                            || z == MIN.getZ() || z == MAX.getZ();
                    if (shell) h.setBlock(new BlockPos(x, y, z), wall);
                }
        h.setBlock(new BlockPos(1, 3, 3), facing(PcBlocks.BOILER_BURNER.get().defaultBlockState(), Direction.WEST));
        h.setBlock(new BlockPos(5, 3, 3), facing(PcBlocks.BOILER_BURNER.get().defaultBlockState(), Direction.EAST));
        h.setBlock(new BlockPos(3, 9, 3), facing(PcBlocks.STEAM_DRUM.get().defaultBlockState(), Direction.NORTH));
        for (int z = 2; z <= 4; z++) {
            h.setBlock(new BlockPos(6, 6, z), PcBlocks.BOILER_SUPERHEATER.get().defaultBlockState());
            h.setBlock(new BlockPos(6, 5, z), PcBlocks.BOILER_ECONOMIZER.get().defaultBlockState());
        }
        h.setBlock(new BlockPos(6, 4, 2), PcBlocks.AIR_PREHEATER.get().defaultBlockState());
        h.setBlock(new BlockPos(6, 4, 3), PcBlocks.AIR_PREHEATER.get().defaultBlockState());
        h.setBlock(PANEL, facing(PcBlocks.WATER_TUBE_BOILER.get().defaultBlockState(), Direction.NORTH));
        h.setBlock(FD_FAN, facing(PcBlocks.FORCED_DRAFT_FAN.get().defaultBlockState(), Direction.NORTH));
        h.setBlock(ID_FAN, facing(PcBlocks.INDUCED_DRAFT_FAN.get().defaultBlockState(), Direction.NORTH));
        motor(h, FD_FAN.north(), Direction.SOUTH, 64);
        motor(h, ID_FAN.north(), Direction.SOUTH, 64);
        h.runAfterDelay(2, () -> {
            panel(h).gasBuffer().put(GasBuffer.fluid(ProcessGas.MINE_METHANE.fluidId), WaterTubeBoilerBlockEntity.GAS_TANK_MB);
            panel(h).feedTank().fill(new FluidStack(Fluids.WATER, WaterTubeBoilerBlockEntity.FEED_TANK_MB),
                    IFluidHandler.FluidAction.EXECUTE);
        });
    }

    /** Котёл собран, тягодутьё дало воздух (BMS видит вентиляторы). */
    private static void ready(GameTestHelper h) {
        WaterTubeBoilerShape s = panel(h).shape();
        h.assertTrue(s != null, "boiler formed");
        WaterTubeUnit u = unit(h);
        h.assertTrue(u.outputs().airKgS > 0.25 * u.spec().ratedAirKgS(), "FD fan gives purge air: " + u.outputs().airKgS);
    }

    private static void command(GameTestHelper h, String action) {
        panel(h).bmsCommand(action, PLAYER);
    }

    /** Барабан с водой под давлением — ПАЗ по уровню не мешает розжигу. */
    private static void water(GameTestHelper h) {
        plant(h).presetWaterTube(panel(h).simId(), 0.3e6, WaterTubeUnit.NORMAL_LEVEL_M);
    }

    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void boilerFormsAndRefusesFuelWithoutPurge(GameTestHelper h) {
        build(h);
        h.startSequence()
                .thenWaitUntil(() -> ready(h))
                .thenExecute(() -> {
                    WaterTubeBoilerShape s = panel(h).shape();
                    h.assertTrue(s.volumeM3() == 54 && s.wallAreaM2() == 90, "furnace 3×6×3 inside: " + s.volumeM3() + " m³, "
                            + s.wallAreaM2() + " m²");
                    h.assertTrue(s.burners().size() == 2 && s.drums().size() == 1, "two burners and a drum");
                    h.assertTrue(s.superheaters() == 3 && s.economizers() == 3 && s.airPreheaters() == 2, "heating surfaces counted");
                    WaterTubeUnit u = unit(h);
                    h.assertTrue(u.spec().burners() == 2 && u.fuelKey().equals(ProcessGas.MINE_METHANE.fluidId), "gas burners");
                    command(h, SteamPlant.BMS_MAIN_FUEL);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    h.assertTrue(plant(h).lastBmsResponse(panel(h).simId()) == BmsResponse.INTERLOCK_PURGE,
                            "main fuel without purge: E_INTERLOCK, got " + plant(h).lastBmsResponse(panel(h).simId()));
                    h.assertTrue(unit(h).bms() == BmsState.OFF && unit(h).outputs().fuelBurnKgS == 0, "no fuel admitted");
                    h.assertTrue(panel(h).gasBuffer().amount() == WaterTubeBoilerBlockEntity.GAS_TANK_MB, "gas untouched");
                    command(h, SteamPlant.BMS_PURGE);
                })
                .thenIdle(2)
                .thenExecute(() -> h.assertTrue(unit(h).bms() == BmsState.PURGE, "purge accepted with fans running"))
                .thenSucceed();
    }

    /** T7.24: запальник не загорелся (электроды неисправны) — через 10 с пробы MFT, не позже чем ещё через 1 с. */
    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void igniterTrialWithoutFlameTrips(GameTestHelper h) {
        build(h);
        long[] start = {0};
        h.startSequence()
                .thenWaitUntil(() -> ready(h))
                .thenExecute(() -> {
                    water(h);
                    WaterTubeUnit u = unit(h);
                    for (int b = 0; b < u.spec().burners(); b++) u.boiler().setIgniterFault(b, true);
                    u.boiler().bmsControl().presetPurged();
                    command(h, SteamPlant.BMS_IGNITER);
                    start[0] = h.getTick();
                })
                .thenWaitUntil(() -> h.assertTrue(unit(h).bms() == BmsState.MFT || unit(h).bms() == BmsState.POST_PURGE,
                        "master fuel trip"))
                .thenExecute(() -> {
                    long ticks = h.getTick() - start[0];
                    h.assertTrue(ticks >= 195 && ticks <= 222, "trip after the 10 s trial within 1 s: " + ticks + " ticks");
                    h.assertTrue(unit(h).boiler().bmsControl().lastMftCause() == MftCause.IGNITER_TRIAL_FAILED, "igniter trial failed");
                    h.assertTrue(unit(h).boiler().bmsControl().mainValve(0) == 0, "fuel valves shut");
                })
                .thenWaitUntil(() -> h.assertTrue(h.getBlockState(PANEL).getValue(PcProperties.ALARM), "trip lamp on the panel"))
                .thenSucceed();
    }

    /**
     * T7.24: ключ байпаса датчиков пламени и неисправный запальник первой горелки — BMS «видит» факел, газ копится в
     * холодной топке (ручной режим: газ 100 %, дутьё 30 %); запальник второй горелки поджигает облако — взрыв топки.
     * Отдельный batch: ударная волна не задевает площадки соседних тестов.
     */
    @GameTest(template = LARGE, timeoutTicks = 1600, batch = "powerhouse_blast")
    public static void bypassedFlameDetectorsBlowUpFurnace(GameTestHelper h) {
        build(h);
        h.startSequence()
                .thenWaitUntil(() -> ready(h))
                .thenExecute(() -> {
                    water(h);
                    WaterTubeUnit u = unit(h);
                    u.boiler().setIgniterFault(0, true);
                    command(h, SteamPlant.BMS_TOGGLE_BYPASS);
                    u.boiler().bmsControl().presetPurged();
                    u.setManual(1.0, 0.3, 0.6, 0.3);
                    u.setCombustionAuto(false);
                    command(h, SteamPlant.BMS_IGNITER);
                })
                .thenIdle(5)
                .thenExecute(() -> command(h, SteamPlant.BMS_MAIN_FUEL))
                .thenIdle(800)
                .thenExecute(() -> {
                    WaterTubeUnit u = unit(h);
                    h.assertTrue(u.bms() == BmsState.RUNNING && !u.outputs().flame, "BMS runs blind, no flame");
                    h.assertTrue(u.boiler().accumulatedFuelKg() > 1, "gas in the furnace: " + u.boiler().accumulatedFuelKg() + " kg");
                    command(h, SteamPlant.BMS_IGNITER);
                })
                .thenWaitUntil(() -> {
                    WaterTubeUnit u = unit(h);
                    h.assertTrue(u.rupture() == BoilerRupture.FURNACE_EXPLOSION && u.exploded(), "furnace explosion");
                    h.assertTrue(u.boiler().explosionEnergyJ() > 0, "blast energy");
                })
                .thenSucceed();
    }

    /** Деаэратор: умягчённая вода и пар из барабана под давлением — на выходе питательная вода. */
    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void deaeratorMakesFeedwaterFromDrumSteam(GameTestHelper h) {
        build(h);
        h.setBlock(DEAERATOR, facing(PcBlocks.DEAERATOR.get().defaultBlockState(), Direction.NORTH));
        h.startSequence()
                .thenWaitUntil(() -> ready(h))
                .thenExecute(() -> {
                    plant(h).presetWaterTube(panel(h).simId(), 1.2e6, WaterTubeUnit.NORMAL_LEVEL_M);
                    DeaeratorBlockEntity d = h.getBlockEntity(DEAERATOR);
                    d.input().fill(new FluidStack(WaterTreatmentBlockEntity.fluid(WaterGrade.SOFTENED), DeaeratorBlockEntity.TANK_MB),
                            IFluidHandler.FluidAction.EXECUTE);
                })
                .thenWaitUntil(() -> {
                    DeaeratorBlockEntity d = h.getBlockEntity(DEAERATOR);
                    h.assertTrue(d.output().getFluidAmount() > 50, "feedwater made: " + d.output().getFluidAmount() + " mB");
                    h.assertTrue(WaterTreatmentBlockEntity.grade(d.output().getFluid().getFluid()) == WaterGrade.DEAERATED,
                            "boiler_feedwater");
                    h.assertTrue(d.getBlockState().getValue(PcProperties.RUNNING), "deaerator running");
                })
                .thenSucceed();
    }
}

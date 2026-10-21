package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.grid.BreakerCellBlockEntity;
import dev.denfry.promptcritical.mod.grid.ConsumerBlockEntity;
import dev.denfry.promptcritical.mod.grid.GridComponents;
import dev.denfry.promptcritical.mod.grid.ElectricMotorBlockEntity;
import dev.denfry.promptcritical.mod.grid.GridPanels;
import dev.denfry.promptcritical.mod.grid.InstrumentBlockEntity;
import dev.denfry.promptcritical.mod.grid.RelayCabinetBlockEntity;
import dev.denfry.promptcritical.mod.grid.ShaftGeneratorBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.grid.BreakerResponse;
import dev.denfry.promptcritical.sim.grid.BreakerState;
import dev.denfry.promptcritical.sim.grid.ConsumerUnit;
import dev.denfry.promptcritical.sim.grid.GeneratorUnit;
import dev.denfry.promptcritical.sim.grid.MachineState;
import dev.denfry.promptcritical.sim.grid.MotorState;
import dev.denfry.promptcritical.sim.grid.RelaySetting;
import dev.denfry.promptcritical.sim.grid.SwitchUnit;
import dev.denfry.promptcritical.sim.grid.SyncClosing;
import dev.denfry.promptcritical.sim.grid.SyncResult;
import dev.denfry.promptcritical.sim.grid.SyncScope;
import dev.denfry.promptcritical.sim.grid.VoltageClass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Электросеть I1 в мире (этап 8 M3, 07 §7.3–7.6): T7.25 — включение {@code shaft_generator} на 120° без реле 25
 * (толчок → overstress сети Create) и с реле 25 ({@code E_INTERLOCK}); T7.27 — двигатель 0,4 кВ на шине 6 кВ (пробой
 * и МТЗ); критерии вехи — включение по синхроноскопу и АЧР при дефиците.
 *
 * <p>Раскладка (всё фасадом на север): генератор G1 (4, 1, 5) с возбудителем сверху и валом сзади (4, 1, 6); ячейка
 * КРУ (5, 1, 5) — шина гранями запад/восток, присоединение — югом; G2 на присоединении (5, 1, 6), вал (5, 1, 7).
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GridGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos G1 = new BlockPos(4, 1, 5), CELL = new BlockPos(5, 1, 5), G2 = new BlockPos(5, 1, 6);
    private static final BlockPos CABINET = new BlockPos(6, 1, 5), SCOPE = new BlockPos(5, 1, 4);

    private GridGameTests() {
    }

    private static SimLevelBinding binding(GameTestHelper h) {
        SimLevelBinding b = SimBindings.get(h.getLevel());
        if (b == null) throw new IllegalStateException("no simulation binding for the test level");
        return b;
    }

    private static BlockState facing(BlockState s, Direction d) {
        return s.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
    }

    /** Генератор фасадом на север с возбудителем сверху и творческим мотором Create сзади. */
    private static void placeGenerator(GameTestHelper h, BlockPos pos, int rpm) {
        h.setBlock(pos, facing(PcBlocks.SHAFT_GENERATOR.get().defaultBlockState(), Direction.NORTH));
        h.setBlock(pos.above(), facing(PcBlocks.GENERATOR_EXCITER.get().defaultBlockState(), Direction.NORTH));
        BlockPos motor = pos.south();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(rpm));
    }

    private static GeneratorUnit gen(GameTestHelper h, BlockPos pos) {
        ShaftGeneratorBlockEntity be = h.getBlockEntity(pos);
        GeneratorUnit u = be.unit();
        h.assertTrue(u != null, "generator twin at " + pos);
        return u;
    }

    /** Двойник есть: класс 6,3 кВ, передача {@code rpm}, поле включено, вариатор {@code trim} ‰. */
    private static void configure(GameTestHelper h, BlockPos pos, int rpm, int trim) {
        GeneratorUnit u = gen(h, pos);
        u.setVoltageClass(VoltageClass.MV_6_3);
        while (u.gearRpm() != rpm) u.cycleGear();
        u.setFieldOn(true);
        u.setTrimPermil(trim);
    }

    private static boolean running(GeneratorUnit u) {
        return u.speedPu() > 0.95 && u.terminalVoltageV() > 6000;
    }

    private static SwitchUnit cell(GameTestHelper h) {
        BreakerCellBlockEntity be = h.getBlockEntity(CELL);
        SwitchUnit u = be.unit();
        h.assertTrue(u != null, "cell twin");
        return u;
    }

    private static void placeCell(GameTestHelper h) {
        h.setBlock(CELL, facing(PcBlocks.SWITCHGEAR_CELL.get().defaultBlockState(), Direction.NORTH));
    }

    /** Шкаф РЗА у ячейки с одним модулем. */
    private static void placeCabinet(GameTestHelper h, ItemStack module) {
        h.setBlock(CABINET, facing(PcBlocks.RELAY_CABINET.get().defaultBlockState(), Direction.NORTH));
        h.runAfterDelay(1, () -> {
            RelayCabinetBlockEntity c = h.getBlockEntity(CABINET);
            h.assertTrue(c.panelItems().insertItem(0, module, false).isEmpty(), "module fitted");
        });
    }

    private static boolean hasRelay(SwitchUnit s, RelaySetting.Kind kind) {
        return s.relays().stream().anyMatch(r -> r.kind() == kind);
    }

    /** Модуль угла: |угол в момент касания контактов| в окне [lo, hi] градусов. */
    private static boolean angleIn(SwitchUnit s, double lo, double hi) {
        SyncScope sc = s.syncScope();
        if (sc == null) return false;
        double a = Math.abs(Math.toDegrees(sc.angleAtClosingRad()));
        return a >= lo && a <= hi;
    }

    private static String diag(GameTestHelper h) {
        var g = binding(h).world().subsystem(dev.denfry.promptcritical.sim.grid.GridSubsystem.class);
        SwitchUnit s = cell(h);
        return "[rebuilds " + g.rebuilds() + ", cell " + s.breakerState() + " closed " + s.closed() + " nodes " + s.node(0) + "/" + s.node(1)
                + ", trips " + s.protectionTrips() + ", I " + s.currentA() + ", sync " + gen(h, G1).syncCount()
                + (h.getBlockEntity(G2) instanceof ShaftGeneratorBlockEntity ? "/" + gen(h, G2).syncCount() + " g2node " + gen(h, G2).node(0) : "")
                + " g1node " + gen(h, G1).node(0) + "]";
    }

    // --- T7.25 ---

    @GameTest(template = LARGE, timeoutTicks = 3000)
    public static void closingAt120DegreesWithoutSyncCheckOverstressesTheCreateShaft(GameTestHelper h) {
        placeGenerator(h, G1, 32);
        placeCell(h);
        placeGenerator(h, G2, 32);
        long[] closedAt = {-1};
        // после разгона творческие моторы меняются на ручные приводы: ёмкость 8 su/об·мин держит холостой ход машины,
        // но не толчок несинхронного включения
        h.onEachTick(() -> {
            for (BlockPos g : new BlockPos[] {G1, G2})
                if (h.getLevel().getBlockEntity(h.absolutePos(g.south())) instanceof HandCrankBlockEntity c) c.turn(false);
        });
        h.startSequence()
                .thenWaitUntil(() -> {
                    gen(h, G1);
                    gen(h, G2);
                    cell(h);
                })
                .thenExecute(() -> {
                    configure(h, G1, 32, 0);
                    configure(h, G2, 32, -30); // скольжение ≈ 1,5 Гц
                })
                .thenWaitUntil(() -> h.assertTrue(running(gen(h, G1)) && running(gen(h, G2)),
                        "machines up: " + gen(h, G1).speedPu() + " / " + gen(h, G2).speedPu()))
                .thenExecute(() -> {
                    for (BlockPos g : new BlockPos[] {G1, G2}) {
                        h.setBlock(g.south(), AllBlocks.HAND_CRANK.getDefaultState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
                        ((HandCrankBlockEntity) h.getBlockEntity(g.south())).turn(false);
                    }
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    ShaftGeneratorBlockEntity a = h.getBlockEntity(G1), b = h.getBlockEntity(G2);
                    long since = h.getLevel().getGameTime() - 40;
                    h.assertTrue(!a.overstressedSince(since) && !b.overstressedSince(since), "idle machines fit the cranks");
                    h.assertTrue(running(gen(h, G1)) && running(gen(h, G2)), "machines still at speed on the cranks");
                })
                .thenWaitUntil(() -> {
                    SwitchUnit s = cell(h);
                    h.assertTrue(angleIn(s, 115, 125), "waiting for 120 degrees");
                    BreakerResponse r = s.operate(true);
                    h.assertTrue(r == BreakerResponse.ACCEPTED, "closing accepted without relay 25: " + r);
                    closedAt[0] = h.getLevel().getGameTime();
                })
                .thenWaitUntil(() -> {
                    SyncClosing c = gen(h, G1).lastSync() != null ? gen(h, G1).lastSync() : gen(h, G2).lastSync();
                    h.assertTrue(c != null, "sync event " + diag(h));
                    h.assertTrue(c.result() != SyncResult.OK && c.torquePu() > 1.5, "out-of-phase closing: " + c);
                    ShaftGeneratorBlockEntity a = h.getBlockEntity(G1), b = h.getBlockEntity(G2);
                    h.assertTrue(a.overstressedSince(closedAt[0]) || b.overstressedSince(closedAt[0]), "Create network overstressed");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 2400)
    public static void syncCheckRelayRefusesClosingAt120Degrees(GameTestHelper h) {
        placeGenerator(h, G1, 64);
        placeCell(h);
        placeGenerator(h, G2, 64);
        placeCabinet(h, new ItemStack(PcItems.RELAY_SYNC_CHECK.get()));
        h.startSequence()
                .thenWaitUntil(() -> {
                    gen(h, G1);
                    gen(h, G2);
                    cell(h);
                })
                .thenExecute(() -> {
                    configure(h, G1, 64, 0);
                    configure(h, G2, 64, -30);
                })
                .thenWaitUntil(() -> h.assertTrue(running(gen(h, G1)) && running(gen(h, G2)), "machines up"))
                .thenWaitUntil(() -> h.assertTrue(hasRelay(cell(h), RelaySetting.Kind.SYNC_CHECK), "cabinet module reaches the cell"))
                .thenWaitUntil(() -> {
                    SwitchUnit s = cell(h);
                    h.assertTrue(angleIn(s, 115, 125), "waiting for 120 degrees");
                    BreakerResponse r = s.operate(true);
                    h.assertTrue(r == BreakerResponse.INTERLOCK, "relay 25 blocks the closing: " + r);
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    h.assertTrue(!cell(h).closed(), "breaker stays open");
                    h.assertTrue(gen(h, G1).syncCount() == 0 && gen(h, G2).syncCount() == 0, "no closing happened");
                })
                .thenSucceed();
    }

    // --- критерий M3: включение по синхроноскопу ---

    @GameTest(template = LARGE, timeoutTicks = 4800)
    public static void generatorIsSynchronizedByTheSynchroscope(GameTestHelper h) {
        placeGenerator(h, G1, 64);
        placeCell(h);
        placeGenerator(h, G2, 64);
        // посёлок 200 кВт на шине G1 (западнее генератора)
        h.setBlock(new BlockPos(2, 2, 5), facing(PcBlocks.GRID_CONSUMER.get().defaultBlockState(), Direction.NORTH));
        h.setBlock(SCOPE, facing(PcBlocks.INSTRUMENT_SYNCHROSCOPE.get().defaultBlockState(), Direction.NORTH));
        long[] nextTrim = {0};
        h.startSequence()
                .thenWaitUntil(() -> {
                    gen(h, G1);
                    gen(h, G2);
                    cell(h);
                    ConsumerBlockEntity c = h.getBlockEntity(new BlockPos(2, 2, 5));
                    h.assertTrue(c.unit() != null, "consumer twin");
                })
                .thenExecute(() -> {
                    configure(h, G1, 64, 0);
                    configure(h, G2, 64, -14);
                    ConsumerUnit c = ((ConsumerBlockEntity) h.getBlockEntity(new BlockPos(2, 2, 5))).unit();
                    c.setDemandKw(200);
                    c.setSwitchedOn(true);
                })
                .thenWaitUntil(() -> h.assertTrue(running(gen(h, G1)) && running(gen(h, G2)), "machines up"))
                .thenWaitUntil(() -> {
                    InstrumentBlockEntity scope = h.getBlockEntity(SCOPE);
                    h.assertTrue(scope.target() == cell(h), "synchroscope reads the cell");
                    SyncScope sc = cell(h).syncScope();
                    h.assertTrue(sc != null, "both sides energized");
                    double slip = sc.slipHz();
                    long now = h.getLevel().getGameTime();
                    if (slip <= 0.005 || slip >= 0.06) {
                        // как оператор: стрелка должна медленно идти по часовой — подстройка вариатора с панели прибора
                        if (now >= nextTrim[0]) {
                            scope.press(slip <= 0.005 ? GridPanels.BUTTON_TRIM_UP : GridPanels.BUTTON_TRIM_DOWN);
                            nextTrim[0] = now + 40;
                        }
                        throw new net.minecraft.gametest.framework.GameTestAssertException("slip " + slip + " Hz");
                    }
                    // скольжение держится после последней подстройки — ждём стрелку на «12» и включаем
                    h.assertTrue(now >= nextTrim[0] + 40, "slip settling");
                    h.assertTrue(angleIn(cell(h), 0, 5), "waiting for the needle at twelve");
                    BreakerResponse r = scope.press(GridPanels.BUTTON_CLOSE);
                    h.assertTrue(r == BreakerResponse.ACCEPTED, "closing accepted: " + r);
                })
                .thenIdle(100)
                .thenExecute(() -> {
                    GeneratorUnit g = gen(h, G1).lastSync() != null ? gen(h, G1) : gen(h, G2);
                    SyncClosing c = g.lastSync();
                    h.assertTrue(c != null && c.result() == SyncResult.OK && c.torquePu() < 0.5, "smooth closing: " + c + " " + diag(h));
                    h.assertTrue(cell(h).closed(), "breaker closed");
                    MachineState st = gen(h, G2).state();
                    h.assertTrue(st == MachineState.SYNCHRONIZED || st == MachineState.LOADED, "G2 in parallel: " + st);
                    h.assertTrue(Math.abs(gen(h, G1).frequencyHz() - gen(h, G2).frequencyHz()) < 0.02, "one frequency");
                })
                .thenSucceed();
    }

    // --- критерий M3: АЧР ---

    @GameTest(template = LARGE, timeoutTicks = 2400)
    public static void underfrequencyLoadSheddingDropsTheLoadOnDeficit(GameTestHelper h) {
        BlockPos small = new BlockPos(2, 2, 5), big = new BlockPos(5, 2, 3);
        placeGenerator(h, G1, 64);
        h.setBlock(small, facing(PcBlocks.GRID_CONSUMER.get().defaultBlockState(), Direction.NORTH));
        h.setBlock(big, facing(PcBlocks.GRID_CONSUMER.get().defaultBlockState(), Direction.NORTH));
        double[] fmin = {50};
        h.startSequence()
                .thenWaitUntil(() -> {
                    gen(h, G1);
                    h.assertTrue(((ConsumerBlockEntity) h.getBlockEntity(small)).unit() != null
                            && ((ConsumerBlockEntity) h.getBlockEntity(big)).unit() != null, "consumer twins");
                })
                .thenExecute(() -> {
                    configure(h, G1, 64, 0);
                    ConsumerUnit s = ((ConsumerBlockEntity) h.getBlockEntity(small)).unit();
                    s.setDemandKw(200);
                    s.setSwitchedOn(true);
                    ConsumerBlockEntity b = h.getBlockEntity(big);
                    b.unit().setDemandKw(500);
                    b.unit().setSwitchedOn(false);
                    h.assertTrue(b.panelItems().insertItem(0, new ItemStack(PcItems.RELAY_UFLS.get()), false).isEmpty(), "AChR module fitted");
                })
                .thenWaitUntil(() -> h.assertTrue(running(gen(h, G1)) && gen(h, G1).frequencyHz() > 49, "machine carries the village"))
                .thenWaitUntil(() -> h.assertTrue(((ConsumerBlockEntity) h.getBlockEntity(big)).unit().relays().stream()
                        .anyMatch(r -> r.kind() == RelaySetting.Kind.UFLS), "module reaches the twin"))
                .thenExecute(() -> ((ConsumerBlockEntity) h.getBlockEntity(big)).unit().setSwitchedOn(true)) // 1,4 о.е. на машину
                .thenWaitUntil(() -> {
                    fmin[0] = Math.min(fmin[0], gen(h, G1).frequencyHz());
                    h.assertTrue(((ConsumerBlockEntity) h.getBlockEntity(big)).unit().shed(), "load shed at " + gen(h, G1).frequencyHz());
                })
                .thenIdle(100)
                .thenExecute(() -> {
                    ConsumerUnit b = ((ConsumerBlockEntity) h.getBlockEntity(big)).unit();
                    h.assertTrue(fmin[0] < 48.8, "frequency fell below the stage: " + fmin[0]);
                    h.assertTrue(b.shedCount() == 1 && !b.energized(), "stage dropped once");
                    h.assertTrue(((ConsumerBlockEntity) h.getBlockEntity(small)).unit().energized(), "the rest stays fed");
                    h.assertTrue(gen(h, G1).frequencyHz() > 48.8, "frequency recovered: " + gen(h, G1).frequencyHz());
                    h.assertTrue(!h.getBlockState(big).getValue(BlockStateProperties.LIT), "shed village goes dark");
                })
                .thenSucceed();
    }

    // --- T7.27 ---

    @GameTest(template = LARGE, timeoutTicks = 2400)
    public static void lowVoltageMotorOnSixKilovoltBusBreaksDownAndOvercurrentTrips(GameTestHelper h) {
        placeGenerator(h, G1, 64);
        placeCell(h);
        // двигатель 0,4 кВ фасадом (валом) на юг: выводы — к присоединению ячейки
        h.setBlock(G2, facing(PcBlocks.ELECTRIC_MOTOR.get().defaultBlockState(), Direction.SOUTH));
        // уставка под машину 500 кВА (I_ном 46 А, КЗ ≈ 3 о.е. с затуханием): 55 А, кривая SI, TMS 0,05
        ItemStack mtz = new ItemStack(PcItems.RELAY_OVERCURRENT.get());
        mtz.set(GridComponents.RELAY_SETTINGS.get(), new GridComponents.Settings(55, 0.05));
        placeCabinet(h, mtz);
        long[] closedAt = {-1}, faultedAt = {-1}, trippedAt = {-1};
        h.onEachTick(() -> {
            if (!(h.getBlockEntity(G2) instanceof ElectricMotorBlockEntity m) || m.unit() == null) return;
            long now = h.getLevel().getGameTime();
            if (faultedAt[0] < 0 && m.unit().state() == MotorState.FAULTED) faultedAt[0] = now;
            if (trippedAt[0] < 0 && faultedAt[0] >= 0 && cell(h).breakerState() != BreakerState.CLOSED) trippedAt[0] = now;
        });
        h.startSequence()
                .thenWaitUntil(() -> {
                    gen(h, G1);
                    cell(h);
                    h.assertTrue(((ElectricMotorBlockEntity) h.getBlockEntity(G2)).unit() != null, "motor twin");
                })
                .thenExecute(() -> {
                    configure(h, G1, 64, 0);
                    ((ElectricMotorBlockEntity) h.getBlockEntity(G2)).unit().setContactor(true);
                })
                .thenWaitUntil(() -> h.assertTrue(running(gen(h, G1)), "machine up"))
                .thenWaitUntil(() -> h.assertTrue(hasRelay(cell(h), RelaySetting.Kind.OVERCURRENT), "cabinet module reaches the cell"))
                .thenExecute(() -> h.assertTrue(cell(h).operate(true) == BreakerResponse.ACCEPTED, "feeder closing"))
                .thenWaitUntil(() -> {
                    h.assertTrue(cell(h).closed(), "contacts closed");
                    if (closedAt[0] < 0) closedAt[0] = h.getLevel().getGameTime();
                })
                .thenWaitUntil(() -> h.assertTrue(trippedAt[0] >= 0, "overcurrent trip"))
                .thenWaitUntil(() -> h.assertTrue(cell(h).breakerState() == BreakerState.OPEN, "breaker open " + diag(h)
                        + " faulted " + faultedAt[0] + " tripped " + trippedAt[0]))
                .thenExecute(() -> {
                    h.assertTrue(faultedAt[0] - closedAt[0] <= 2, "breakdown on energizing: " + (faultedAt[0] - closedAt[0]));
                    h.assertTrue(trippedAt[0] - faultedAt[0] <= 40, "overcurrent trip within 2 s: " + (trippedAt[0] - faultedAt[0]));
                    h.assertTrue(cell(h).protectionTrips() == 1, "one protection trip");
                    h.assertTrue(((ElectricMotorBlockEntity) h.getBlockEntity(G2)).unit().state() == MotorState.FAULTED, "motor faulted");
                    h.assertBlockPresent(PcBlocks.ELECTRIC_MOTOR.get(), G2); // защита сработала — разрушений нет
                    h.assertBlockPresent(PcBlocks.SWITCHGEAR_CELL.get(), CELL);
                })
                .thenSucceed();
    }
}

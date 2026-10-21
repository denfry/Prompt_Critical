package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.automation.AutomationBlockEntity;
import dev.denfry.promptcritical.mod.automation.PowerSupplyBlockEntity;
import dev.denfry.promptcritical.mod.automation.WiredDevice;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.grid.BreakerCellBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.automation.AutomationUnit;
import dev.denfry.promptcritical.sim.grid.SwitchUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Автоматика в мире (этап 9 M3, 03d §3d.3–3d.4): провода {@code wiring_tool} между клеммами блоков — лестница
 * пуск/стоп с самоподхватом (§3d.3.2), ПИ-регулятор из блоков A2 на апериодическом объекте (§3d.4.8), выключатель
 * ячейки КРУ от ключа управления через клеммы механизма (оперативный ток, блок-контакт) и переходник реле → редстоун.
 *
 * <p>Блоки питания — со стендовым входом ({@link PowerSupplyBlockEntity#setBenchInput}): сеть 0,4 кВ проверяет
 * {@code GridGameTests}.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AutomationGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";

    private AutomationGameTests() {
    }

    private static SimLevelBinding binding(GameTestHelper h) {
        SimLevelBinding b = SimBindings.get(h.getLevel());
        if (b == null) throw new IllegalStateException("no simulation binding for the test level");
        return b;
    }

    private static void place(GameTestHelper h, BlockPos pos, Block block) {
        h.setBlock(pos, block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    private static WiredDevice dev(GameTestHelper h, BlockPos pos) {
        return (WiredDevice) h.getBlockEntity(pos);
    }

    /** Провод от клеммы {@code la} блока {@code a} к клемме {@code lb} блока {@code b}. */
    private static void wire(GameTestHelper h, BlockPos a, String la, BlockPos b, String lb) {
        WiredDevice da = dev(h, a), db = dev(h, b);
        int ta = da.wiredKind().indexOf(la), tb = db.wiredKind().indexOf(lb);
        h.assertTrue(ta >= 0 && tb >= 0, "terminals " + la + "/" + lb);
        binding(h).automation().connect(da, ta, db, tb);
    }

    private static AutomationUnit unit(GameTestHelper h, BlockPos pos) {
        AutomationBlockEntity be = h.getBlockEntity(pos);
        AutomationUnit u = be.unit();
        h.assertTrue(u != null, "automation twin at " + pos);
        return u;
    }

    private static boolean flag(GameTestHelper h, BlockPos pos, net.minecraft.world.level.block.state.properties.BooleanProperty p) {
        BlockState s = h.getBlockState(pos);
        return s.hasProperty(p) && s.getValue(p);
    }

    // --- лестница с самоподхватом ---

    private static final BlockPos PSU = new BlockPos(2, 2, 5), STOP = new BlockPos(3, 2, 5), START = new BlockPos(4, 2, 5),
            K1 = new BlockPos(5, 2, 5), LAMP = new BlockPos(6, 2, 5);

    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void startStopLadderSealsInAndStops(GameTestHelper h) {
        place(h, PSU, PcBlocks.RELAY_POWER_SUPPLY.get());
        place(h, STOP, PcBlocks.PUSHBUTTON.get());
        place(h, START, PcBlocks.PUSHBUTTON.get());
        place(h, K1, PcBlocks.RELAY.get());
        place(h, LAMP, PcBlocks.INDICATOR_LAMP.get());
        h.runAfterDelay(5, () -> {
            ((PowerSupplyBlockEntity) h.getBlockEntity(PSU)).setBenchInput(1);
            wire(h, PSU, "+24", STOP, "11");        // СТОП — НЗ
            wire(h, STOP, "12", START, "13");       // ПУСК — НО
            wire(h, START, "14", K1, "A1");
            wire(h, K1, "A2", PSU, "0");
            wire(h, STOP, "12", K1, "11");          // самоподхват 11-14
            wire(h, K1, "14", K1, "A1");
            wire(h, PSU, "+24", K1, "21");          // лампа «РАБОТА» через 21-24
            wire(h, K1, "24", LAMP, "X1");
            wire(h, LAMP, "X2", PSU, "0");
        });
        h.runAfterDelay(40, () -> {
            h.assertFalse(flag(h, K1, PcProperties.PICKED), "K1 released before START");
            h.assertTrue(unit(h, PSU).supplyVoltage() > 23, "24 V on the supply: " + unit(h, PSU).supplyVoltage());
            ((AutomationBlockEntity) h.getBlockEntity(START)).press();
        });
        h.runAfterDelay(90, () -> {
            h.assertTrue(flag(h, K1, PcProperties.PICKED), "K1 sealed in after START released");
            h.assertTrue(flag(h, LAMP, BlockStateProperties.LIT), "RUN lamp lit");
            h.assertTrue(unit(h, K1).relayOps() == 1, "one pick-up: " + unit(h, K1).relayOps());
            ((AutomationBlockEntity) h.getBlockEntity(STOP)).press();
        });
        h.runAfterDelay(140, () -> {
            h.assertFalse(flag(h, K1, PcProperties.PICKED), "STOP drops K1 and it stays dropped");
            h.assertFalse(flag(h, LAMP, BlockStateProperties.LIT), "lamp out");
            h.succeed();
        });
    }

    // --- ПИ-регулятор из блоков A2 ---

    private static final BlockPos SP = new BlockPos(2, 1, 4), ERR = new BlockPos(3, 1, 4), INT_C = new BlockPos(4, 1, 4),
            OUT = new BlockPos(5, 1, 4), PLANT = new BlockPos(6, 1, 4);

    /**
     * Объект 1/(2s + 1) на интеграторе с обратной связью, ПИ-регулятор Kp = 1, Tᵢ = 2 с (сумматор ошибки, интегратор,
     * сумматор выхода), задание 6 В от станции: замкнутый контур — апериодическое звено τ = 2 с, PV сходится к SP без
     * статической ошибки (интегральная составляющая), остаток — погрешности ламповых блоков.
     */
    @GameTest(template = LARGE, timeoutTicks = 1200)
    public static void analogPiLoopSettlesOnSetpoint(GameTestHelper h) {
        place(h, SP, PcBlocks.MANUAL_STATION.get());
        place(h, ERR, PcBlocks.ANALOG_SUMMER.get());
        place(h, INT_C, PcBlocks.ANALOG_INTEGRATOR.get());
        place(h, OUT, PcBlocks.ANALOG_SUMMER.get());
        place(h, PLANT, PcBlocks.ANALOG_INTEGRATOR.get());
        h.runAfterDelay(5, () -> {
            wire(h, SP, "SP", ERR, "X1");           // e = SP − PV
            wire(h, PLANT, "Y", ERR, "X2");
            wire(h, ERR, "Y", INT_C, "X1");         // ∫e/Tᵢ
            wire(h, ERR, "Y", OUT, "X1");           // u = Kp·e + ∫
            wire(h, INT_C, "Y", OUT, "X2");
            wire(h, OUT, "Y", PLANT, "X1");         // ẏ = (u − y)/2
            wire(h, PLANT, "Y", PLANT, "X2");
        });
        h.runAfterDelay(20, () -> {
            unit(h, SP).setParam(AutomationUnit.D_SET, 6);
            AutomationUnit err = unit(h, ERR);
            err.setParam(AutomationUnit.D_TABLE, -1);
            err.setParam(AutomationUnit.D_TABLE + 1, 1);
            AutomationUnit ic = unit(h, INT_C);
            ic.setParam(AutomationUnit.D_TABLE, -1);
            ic.setParam(AutomationUnit.D_SPAN, 2);
            AutomationUnit out = unit(h, OUT);
            out.setParam(AutomationUnit.D_TABLE, -1);
            out.setParam(AutomationUnit.D_TABLE + 1, -1);
            AutomationUnit plant = unit(h, PLANT);
            plant.setParam(AutomationUnit.D_TABLE, -1);
            plant.setParam(AutomationUnit.D_TABLE + 1, 1);
            plant.setParam(AutomationUnit.D_SPAN, 2);
        });
        h.runAfterDelay(80, () -> {
            double pv = unit(h, PLANT).output();
            h.assertTrue(pv > 0.5 && pv < 5.9, "PV rising towards SP after 3 s: " + pv);
        });
        h.runAfterDelay(700, () -> {
            double pv = unit(h, PLANT).output(), e = unit(h, ERR).output();
            h.assertTrue(Math.abs(pv - 6) < 0.15, "PV settled on SP 6 V: " + pv);
            h.assertTrue(Math.abs(e) < 0.1, "no steady-state error: " + e);
            h.succeed();
        });
    }

    // --- клеммы механизма: выключатель от ключа ---

    private static final BlockPos CELL = new BlockPos(6, 1, 7), KEY = new BlockPos(3, 2, 7), PSU2 = new BlockPos(2, 2, 7),
            AUX = new BlockPos(4, 2, 7);

    /**
     * Ячейка КРУ без сети: «+/−» привода от блока питания, ключ «ВКЛ» на электромагнит включения, «ОТКЛ» — на
     * отключения, блок-контакт 13–14 зажигает лампу. Без питания на «+/−» привод не работает.
     */
    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void controlKeyClosesAndTripsTheBreakerThroughItsTerminals(GameTestHelper h) {
        place(h, CELL, PcBlocks.SWITCHGEAR_CELL.get());
        place(h, PSU2, PcBlocks.RELAY_POWER_SUPPLY.get());
        place(h, KEY, PcBlocks.KEY_SWITCH.get());
        place(h, AUX, PcBlocks.INDICATOR_LAMP.get());
        h.runAfterDelay(5, () -> {
            ((PowerSupplyBlockEntity) h.getBlockEntity(PSU2)).setBenchInput(0);
            wire(h, PSU2, "+24", CELL, "+");
            wire(h, PSU2, "0", CELL, "-");
            wire(h, PSU2, "+24", KEY, "C");
            wire(h, KEY, "ON", CELL, "CLOSE");
            wire(h, KEY, "OFF", CELL, "TRIP");
            wire(h, PSU2, "+24", CELL, "13");
            wire(h, CELL, "14", AUX, "X1");
            wire(h, AUX, "X2", PSU2, "0");
        });
        h.runAfterDelay(40, () -> {
            SwitchUnit s = ((BreakerCellBlockEntity) h.getBlockEntity(CELL)).unit();
            h.assertTrue(s != null && !s.controlPower(), "no control power without the supply");
            ((PowerSupplyBlockEntity) h.getBlockEntity(PSU2)).setBenchInput(1);
        });
        h.runAfterDelay(60, () -> {
            SwitchUnit s = ((BreakerCellBlockEntity) h.getBlockEntity(CELL)).unit();
            h.assertTrue(s.controlPower(), "control power from the 24 V supply");
            h.assertFalse(s.closed(), "breaker open before the command");
            AutomationBlockEntity key = h.getBlockEntity(KEY);
            key.turnKey(null, true); // выбор
            key.turnKey(null, true); // поворот вправо — ВКЛ
        });
        h.runAfterDelay(110, () -> {
            SwitchUnit s = ((BreakerCellBlockEntity) h.getBlockEntity(CELL)).unit();
            h.assertTrue(s.closed(), "breaker closed by the closing coil");
            h.assertTrue(flag(h, AUX, BlockStateProperties.LIT), "auxiliary contact 13-14 lights the lamp");
            AutomationBlockEntity key = h.getBlockEntity(KEY);
            key.turnKey(null, false);
            key.turnKey(null, false); // влево — ОТКЛ
        });
        h.runAfterDelay(160, () -> {
            SwitchUnit s = ((BreakerCellBlockEntity) h.getBlockEntity(CELL)).unit();
            h.assertFalse(s.closed(), "breaker tripped by the trip coil");
            h.assertFalse(flag(h, AUX, BlockStateProperties.LIT), "lamp out");
            h.succeed();
        });
    }

    // --- переходник реле → редстоун ---

    private static final BlockPos IFACE = new BlockPos(5, 2, 9), WALL = new BlockPos(5, 2, 10), REDLAMP = new BlockPos(5, 2, 11),
            PSU3 = new BlockPos(2, 2, 9), BTN = new BlockPos(3, 2, 9);

    @GameTest(template = LARGE, timeoutTicks = 300)
    public static void redstoneInterfacePowersTheWallBehindIt(GameTestHelper h) {
        h.setBlock(WALL, Blocks.STONE);
        h.setBlock(REDLAMP, Blocks.REDSTONE_LAMP);
        place(h, IFACE, PcBlocks.REDSTONE_INTERFACE.get());
        place(h, PSU3, PcBlocks.RELAY_POWER_SUPPLY.get());
        place(h, BTN, PcBlocks.PUSHBUTTON.get());
        h.runAfterDelay(5, () -> {
            ((PowerSupplyBlockEntity) h.getBlockEntity(PSU3)).setBenchInput(1);
            wire(h, PSU3, "+24", BTN, "13");
            wire(h, BTN, "14", IFACE, "A1");
            wire(h, IFACE, "A2", PSU3, "0");
        });
        h.runAfterDelay(30, () -> {
            h.assertBlockProperty(REDLAMP, BlockStateProperties.LIT, false);
            ((AutomationBlockEntity) h.getBlockEntity(BTN)).press();
        });
        h.runAfterDelay(34, () -> {
            h.assertTrue(flag(h, IFACE, BlockStateProperties.POWERED), "interface coil energized");
            h.assertBlockProperty(REDLAMP, BlockStateProperties.LIT, true);
        });
        h.runAfterDelay(80, () -> {
            h.assertFalse(flag(h, IFACE, BlockStateProperties.POWERED), "released with the button");
            h.succeed();
        });
    }
}

package dev.denfry.promptcritical.mod.gametest;

import com.mojang.authlib.GameProfile;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.automation.AutomationPanelBlockEntity;
import dev.denfry.promptcritical.mod.automation.AutomationPanels;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.ops.HotWorks;
import dev.denfry.promptcritical.mod.ops.PermitBoardBlockEntity;
import dev.denfry.promptcritical.mod.ops.Permits;
import dev.denfry.promptcritical.mod.ops.PortableGasDetectorItem;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.automation.AutomationUnit;
import dev.denfry.promptcritical.sim.ops.PermitEnforcement;
import dev.denfry.promptcritical.sim.ops.PermitRegistry;
import dev.denfry.promptcritical.sim.ops.PermitState;
import dev.denfry.promptcritical.sim.ops.PermitType;
import dev.denfry.promptcritical.sim.ops.Turnaround;
import dev.denfry.promptcritical.sim.ops.TurnaroundPhase;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Наряды-допуски (13 §13.2): огневые работы у резервуара с пропаном без наряда запрещены при {@code ENFORCED} и
 * разрешены по действующему наряду с газоанализом; остановочный ремонт у стенда нарядов проходит фазы §13.6.2 с
 * ремонтом ГЦН по радиационному наряду; периодическая проверка ПАЗ со щита логического блока (03d §3d.6.8).
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PermitGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final long RUN = System.nanoTime();

    private PermitGameTests() {
    }

    private static FakePlayer player(GameTestHelper h, String name) {
        UUID id = UUID.nameUUIDFromBytes(("pc-permit-" + name + "-" + RUN).getBytes());
        return FakePlayerFactory.get(h.getLevel(), new GameProfile(id, name));
    }

    /** Мир GameTest сохраняется между прогонами вместе с журналом нарядов: снять открытые наряды прошлых прогонов здесь. */
    private static void clearPermits(GameTestHelper h, BlockPos center, double r) {
        PermitRegistry reg = Permits.registry(h.getLevel());
        long now = Permits.tick(h.getLevel());
        for (PermitRegistry.Permit p : List.copyOf(reg.all())) {
            if (!p.state().open() || p.target().distanceSq(dev.denfry.promptcritical.mod.ops.Lockouts.key(center)) > (r + 11) * (r + 11)) continue;
            if (p.state() == PermitState.ACTIVE) {
                reg.addWork(p.id(), p.requiredWorkS());
                reg.complete(p.id(), now);
            }
            if (p.state() == PermitState.WORK_COMPLETE) reg.close(p.id(), false, now);
            else reg.cancel(p.id(), now);
        }
    }

    @GameTest(template = LARGE, timeoutTicks = 200, batch = "permit_hot_alone")
    public static void hotWorkNeedsAnActivePermitWithAGasTest(GameTestHelper h) {
        BlockPos tank = new BlockPos(3, 1, 3), spot = new BlockPos(6, 1, 6);
        h.setBlock(tank, com.simibubi.create.AllBlocks.FLUID_TANK.getDefaultState());
        FakePlayer p = player(h, "Welder");
        h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    clearPermits(h, h.absolutePos(spot), 12);
                    IFluidHandler cap = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(tank), null);
                    h.assertTrue(cap != null && cap.fill(new FluidStack(PcFluids.PROPANE.source.get(), 1000), IFluidHandler.FluidAction.EXECUTE) > 0,
                            "propane in the tank");
                    BlockPos at = h.absolutePos(spot);
                    p.moveTo(Vec3.atBottomCenterOf(at));
                    h.assertTrue(Permits.flammableNear(h.getLevel(), at, PermitRegistry.HOT_WORK_RADIUS_M), "propane within 11 m");
                    h.assertTrue(!HotWorks.attempt(p, at, PermitEnforcement.ENFORCED, false), "hot work refused without a permit");
                    h.assertTrue(HotWorks.attempt(p, at, PermitEnforcement.ADVISORY, false), "advisory only warns");

                    PermitRegistry reg = Permits.registry(h.getLevel());
                    PermitRegistry.Permit permit = Permits.issue(p, new ItemStack(PcItems.WORK_PERMIT.get()), h.absolutePos(tank));
                    h.assertTrue(permit != null && permit.type() == PermitType.HOT, "a hot work permit at the propane tank, got " + permit);
                    ItemStack paper = p.getInventory().items.stream().filter(s -> s.has(Permits.REF.get())).findFirst().orElse(ItemStack.EMPTY);
                    h.assertTrue(!paper.isEmpty() && paper.get(Permits.REF.get()).id() == permit.id(), "the permit paper refers to the original");

                    Permits.advance(p, paper, h.absolutePos(tank));
                    h.assertTrue(permit.state() == PermitState.ISOLATED, "nothing to lock at the tank: isolated, got " + permit.state());
                    h.assertTrue(Permits.advance(p, paper, h.absolutePos(tank)) == PermitRegistry.Result.NO_GAS_TEST, "no admission without a gas test");

                    ItemStack detector = new ItemStack(PcItems.PORTABLE_GAS_DETECTOR.get());
                    Permits.measure(p, detector, PermitEnforcement.ENFORCED);
                    h.assertTrue(permit.state() == PermitState.ISOLATED, "an instrument without a bump test is not trusted when enforced");
                    PortableGasDetectorItem.bumpTest(detector, h.getLevel());
                    Permits.measure(p, detector, PermitEnforcement.ENFORCED);
                    h.assertTrue(permit.state() == PermitState.GAS_TESTED, "gas test recorded, got " + permit.state());

                    h.assertTrue(Permits.advance(p, paper, h.absolutePos(tank)) == PermitRegistry.Result.NO_FIRE_WATCH, "no fire watch");
                    p.getInventory().add(new ItemStack(PcItems.FIRE_EXTINGUISHER.get()));
                    h.assertTrue(Permits.advance(p, paper, h.absolutePos(tank)) == PermitRegistry.Result.OK, "admitted with a fire watch");
                    h.assertTrue(permit.state() == PermitState.ACTIVE, "permit active");
                    h.assertTrue(HotWorks.attempt(p, at, PermitEnforcement.ENFORCED, false), "hot work under the permit");

                    p.setShiftKeyDown(true);
                    Permits.advance(p, paper, h.absolutePos(tank));
                    Permits.advance(p, paper, h.absolutePos(tank));
                    p.setShiftKeyDown(false);
                    h.assertTrue(permit.state() == PermitState.CLOSED, "permit closed, got " + permit.state());
                    h.assertTrue(reg.activeCovering(PermitType.HOT, dev.denfry.promptcritical.mod.ops.Lockouts.key(at)) == null,
                            "no active permit after closing");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 200, batch = "permit_turnaround_alone")
    public static void turnaroundRunsFromShutdownToStartupWithAPumpRepairedUnderARadiationPermit(GameTestHelper h) {
        BlockPos board = new BlockPos(2, 1, 2), pump = new BlockPos(4, 1, 4);
        h.setBlock(board, PcBlocks.PERMIT_BOARD.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        h.setBlock(pump, PcBlocks.PRIMARY_PUMP.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        FakePlayer p = player(h, "Fitter");
        h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    clearPermits(h, h.absolutePos(board), PermitBoardBlockEntity.SCOPE_MAX_M);
                    PermitBoardBlockEntity b = (PermitBoardBlockEntity) h.getBlockEntity(board);
                    Turnaround t = b.turnaround();
                    h.assertTrue(t != null, "turnaround at the board");
                    t.reset();
                    b.setScope(PermitBoardBlockEntity.SCOPE_MIN_M); // соседние тесты с работающими машинами — за границей установки
                    h.assertTrue(b.next(null).isEmpty() && t.phase() == TurnaroundPhase.SHUTDOWN, "shutdown");
                    h.assertTrue(b.next(null).isEmpty() && t.phase() == TurnaroundPhase.FREEING, "nothing runs: freeing, got " + t.phase() + " " + b.inputs());
                    h.assertTrue(b.next(null).contains(Turnaround.Missing.GAS_TEST), "freeing needs a gas test");
                    p.moveTo(Vec3.atBottomCenterOf(h.absolutePos(board).east(2)));
                    ItemStack detector = new ItemStack(PcItems.PORTABLE_GAS_DETECTOR.get());
                    PortableGasDetectorItem.bumpTest(detector, h.getLevel());
                    Permits.measure(p, detector, PermitEnforcement.ENFORCED);
                    h.assertTrue(b.next(null).isEmpty() && t.phase() == TurnaroundPhase.OPEN, "open, got " + t.phase());
                    h.assertTrue(b.next(null).contains(Turnaround.Missing.NO_REPAIRS), "no repairs yet");

                    // ремонт ГЦН по радиационному наряду
                    BlockPos pumpAbs = h.absolutePos(pump);
                    PermitRegistry reg = Permits.registry(h.getLevel());
                    PermitRegistry.Permit permit = Permits.issue(p, new ItemStack(PcItems.WORK_PERMIT.get()), pumpAbs);
                    h.assertTrue(permit != null && permit.type() == PermitType.RADIATION, "the main coolant pump takes a radiation permit");
                    ItemStack paper = p.getInventory().items.stream().filter(s -> s.has(Permits.REF.get())
                            && s.get(Permits.REF.get()).id() == permit.id()).findFirst().orElseThrow();
                    p.moveTo(Vec3.atBottomCenterOf(pumpAbs.east()));
                    Permits.advance(p, paper, pumpAbs);
                    Permits.advance(p, paper, pumpAbs);
                    h.assertTrue(permit.state() == PermitState.ACTIVE, "pump permit active, got " + permit.state());
                    p.setShiftKeyDown(true);
                    h.assertTrue(Permits.advance(p, paper, pumpAbs) == PermitRegistry.Result.WORK_NOT_DONE, "a minute of repair is needed");
                    reg.addWork(permit.id(), Permits.REPAIR_WORK_S);
                    h.assertTrue(Permits.advance(p, paper, pumpAbs) == PermitRegistry.Result.OK, "repair complete");
                    h.assertTrue(b.next(null).isEmpty() && t.phase() == TurnaroundPhase.BOX_UP, "box-up, got " + t.phase());
                    h.assertTrue(b.next(null).contains(Turnaround.Missing.PERMITS_OPEN), "the pump permit is still open");
                    h.assertTrue(Permits.advance(p, paper, pumpAbs) == PermitRegistry.Result.OK, "pump permit closed");
                    p.setShiftKeyDown(false);
                    h.assertTrue(permit.collectiveDoseSv() < Permits.ALARA_COLLECTIVE_SV, "a cold pump: collective dose below 10 mSv");
                    h.assertTrue(b.next(null).isEmpty() && t.phase() == TurnaroundPhase.PSSR, "PSSR, got " + t.phase());
                    h.assertTrue(b.next(null).isEmpty() && t.phase() == TurnaroundPhase.STARTUP, "startup, got " + t.phase());
                    h.assertTrue(b.next(null).contains(Turnaround.Missing.NOT_RUNNING), "nothing runs yet");
                    h.setBlock(pump, h.getBlockState(pump).setValue(PcProperties.RUNNING, true));
                    h.assertTrue(b.next(null).isEmpty() && t.complete(), "the pump runs: turnaround complete");
                    h.assertTrue(t.repairs() == 1, "one repair, got " + t.repairs());
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void sisProofTestFromThePanelResetsTheClockAndOverdueLightsTheAlarm(GameTestHelper h) {
        BlockPos sis = new BlockPos(5, 1, 5);
        h.setBlock(sis, PcBlocks.SIS_LOGIC.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        FakePlayer p = player(h, "Instrument");
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(((AutomationPanelBlockEntity) h.getBlockEntity(sis)).unit() != null, "SIS twin"))
                .thenExecute(() -> {
                    AutomationPanelBlockEntity be = (AutomationPanelBlockEntity) h.getBlockEntity(sis);
                    AutomationUnit u = be.unit();
                    u.sis().ageProofTest(100);
                    be.onPanelButton(p, AutomationPanels.BUTTON_SIS_TEST);
                    h.assertTrue(!u.sis().testing(), "no test without a bypass");
                    be.onPanelButton(p, AutomationPanels.BUTTON_SIS_BYPASS);
                    be.onPanelButton(p, AutomationPanels.BUTTON_SIS_TEST);
                    h.assertTrue(u.sis().testing(), "test started in bypass");
                    for (int i = 0; i < 3; i++) be.onPanelButton(p, AutomationPanels.BUTTON_SIS_TEST_CHANNEL);
                    be.onPanelButton(p, AutomationPanels.BUTTON_SIS_SIGN);
                    h.assertTrue(u.sis().testing(), "not signed while bypassed");
                    be.onPanelButton(p, AutomationPanels.BUTTON_SIS_BYPASS);
                    be.onPanelButton(p, AutomationPanels.BUTTON_SIS_SIGN);
                    if (u.sis().failedMask() == 0) {
                        h.assertTrue(!u.sis().testing() && u.sis().sinceTestH() == 0 && u.sis().proofTestsOnTime() == 1,
                                "signed in time: clock reset");
                    } else {
                        h.assertTrue(u.sis().testing() && u.sis().sinceTestH() >= 100, "a failed channel blocks the signature");
                        u.sis().abortProofTest();
                    }
                    u.sis().ageProofTest(u.sisProofIntervalH() + 1);
                })
                .thenWaitUntil(() -> h.assertTrue(h.getBlockState(sis).getValue(PcProperties.ALARM), "overdue proof test lights the alarm"))
                .thenSucceed();
    }
}

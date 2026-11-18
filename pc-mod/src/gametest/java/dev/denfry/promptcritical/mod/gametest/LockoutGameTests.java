package dev.denfry.promptcritical.mod.gametest;

import com.mojang.authlib.GameProfile;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.grid.BreakerCellBlockEntity;
import dev.denfry.promptcritical.mod.ops.Lockouts;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.grid.BreakerResponse;
import dev.denfry.promptcritical.sim.grid.SwitchUnit;
import dev.denfry.promptcritical.sim.ops.LockoutRegistry;
import dev.denfry.promptcritical.sim.ops.LockoutRegistry.IsolationState;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * LOTO в мире (13 §13.1, тест-кейсы T-13-01, T-13-02, T-13-04): замок на отключённой ячейке КРУ держит её
 * отключённой для любой команды; чужой замок не снять; накладка держит замки бригады до последнего; администратор
 * срезает замки; запертую ячейку не сломать.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LockoutGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos CELL = new BlockPos(5, 1, 5);

    private LockoutGameTests() {
    }

    private static FakePlayer player(GameTestHelper h, String name) {
        UUID id = UUID.nameUUIDFromBytes(("pc-lockout-" + name).getBytes());
        return FakePlayerFactory.get(h.getLevel(), new GameProfile(id, name));
    }

    /** ПКМ по ячейке предметом в главной руке (или пустой рукой с Shift) — как это делает клиент. */
    private static void click(GameTestHelper h, FakePlayer p, ItemStack main, boolean sneak) {
        p.setItemInHand(InteractionHand.MAIN_HAND, main);
        p.setShiftKeyDown(sneak);
        BlockPos abs = h.absolutePos(CELL);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false);
        Lockouts.onRightClick(new PlayerInteractEvent.RightClickBlock(p, InteractionHand.MAIN_HAND, abs, hit));
    }

    private static BreakerCellBlockEntity cell(GameTestHelper h) {
        return h.getBlockEntity(CELL);
    }

    private static SwitchUnit unit(GameTestHelper h) {
        SwitchUnit u = cell(h).unit();
        h.assertTrue(u != null, "cell twin");
        return u;
    }

    private static LockoutRegistry registry(GameTestHelper h) {
        return Lockouts.registry(h.getLevel());
    }

    private static IsolationState state(GameTestHelper h) {
        return registry(h).state(Lockouts.key(h.absolutePos(CELL)), cell(h).isolatedPosition());
    }

    private static void place(GameTestHelper h) {
        h.setBlock(CELL, PcBlocks.SWITCHGEAR_CELL.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void lockedCellRefusesClosingAndOnlyItsOwnerUnlocksIt(GameTestHelper h) {
        place(h);
        FakePlayer a = player(h, "Alice"), b = player(h, "Bob");
        h.startSequence()
                .thenWaitUntil(() -> unit(h))
                .thenExecute(() -> {
                    h.assertTrue(cell(h).operate(null, true) == BreakerResponse.ACCEPTED, "cell closes in service");
                    // включённый аппарат замок не принимает: сначала изолировать
                    click(h, a, new ItemStack(PcItems.LOCKOUT_PADLOCK.get()), false);
                    h.assertTrue(state(h) == IsolationState.IN_SERVICE, "no lock on a closed cell: " + state(h));
                    h.assertTrue(cell(h).operate(null, false) == BreakerResponse.ACCEPTED, "opened");
                    a.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(PcItems.LOCKOUT_TAG.get()));
                    click(h, a, new ItemStack(PcItems.LOCKOUT_PADLOCK.get()), false);
                    h.assertTrue(state(h) == IsolationState.LOCKED, "locked: " + state(h));
                    h.assertTrue(!registry(h).locks(Lockouts.key(h.absolutePos(CELL))).get(0).tag().isEmpty(), "tag hung with the lock");
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    h.assertTrue(unit(h).lockedOut(), "the drive is locked out");
                    h.assertTrue(cell(h).operate(null, true) == BreakerResponse.LOCKED_OUT, "closing refused");
                    // T-13-01: Боб снять замок Алисы не может, и сломать ячейку тоже
                    click(h, b, ItemStack.EMPTY, true);
                    h.assertTrue(state(h) == IsolationState.LOCKED, "Bob cannot take Alice's lock off");
                    BlockEvent.BreakEvent br = new BlockEvent.BreakEvent(h.getLevel(), h.absolutePos(CELL),
                            h.getLevel().getBlockState(h.absolutePos(CELL)), b);
                    Lockouts.onBreak(br);
                    h.assertTrue(br.isCanceled(), "a locked point cannot be broken");
                    // Алиса снимает свой замок — замок и бирка возвращаются
                    a.getInventory().clearContent();
                    click(h, a, ItemStack.EMPTY, true);
                    h.assertTrue(state(h) == IsolationState.ISOLATED, "back to isolated: " + state(h));
                    h.assertTrue(a.getInventory().countItem(PcItems.LOCKOUT_PADLOCK.get()) == 1, "padlock returned");
                    h.assertTrue(a.getInventory().countItem(PcItems.LOCKOUT_TAG.get()) == 1, "tag returned");
                })
                .thenIdle(2)
                .thenExecute(() -> h.assertTrue(cell(h).operate(null, true) == BreakerResponse.ACCEPTED, "closes again"))
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void haspHoldsTheCrewAndAnAdministratorCutsTheLocks(GameTestHelper h) {
        place(h);
        FakePlayer a = player(h, "Alice"), b = player(h, "Bob"), c = player(h, "Carol");
        h.startSequence()
                .thenWaitUntil(() -> unit(h))
                .thenExecute(() -> {
                    cell(h).operate(null, false);
                    click(h, a, new ItemStack(PcItems.LOCKOUT_PADLOCK.get()), false);
                    click(h, b, new ItemStack(PcItems.LOCKOUT_PADLOCK.get()), false);
                    h.assertTrue(registry(h).locks(Lockouts.key(h.absolutePos(CELL))).size() == 1, "one lock without a hasp");
                    click(h, a, ItemStack.EMPTY, true);
                    click(h, a, new ItemStack(PcItems.LOCKOUT_HASP.get()), false);
                    for (FakePlayer p : new FakePlayer[] {a, b, c}) click(h, p, new ItemStack(PcItems.LOCKOUT_PADLOCK.get()), false);
                    h.assertTrue(registry(h).locks(Lockouts.key(h.absolutePos(CELL))).size() == 3, "three crew locks on the hasp");
                    // T-13-04: сняты два — точка заперта
                    click(h, a, ItemStack.EMPTY, true);
                    click(h, b, ItemStack.EMPTY, true);
                    h.assertTrue(state(h) == IsolationState.LOCKED, "still locked by Carol");
                    // T-13-02: администратор срезает
                    h.assertTrue(Lockouts.adminCut(h.getLevel(), h.absolutePos(CELL), "admin", "test").size() == 1, "Carol's lock cut");
                    h.assertTrue(state(h) == IsolationState.ISOLATED, "isolated after the cut");
                })
                .thenSucceed();
    }

    /** Заглушка между трубой и баком: открытая пропускает воду в бак, глухая — нет; запертую не повернуть. */
    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void lineBlindPassesFluidOnlyWhenOpenAndHoldsWhenLocked(GameTestHelper h) {
        BlockPos blind = new BlockPos(3, 1, 3), tank = blind.north();
        h.setBlock(tank, com.simibubi.create.AllBlocks.FLUID_TANK.getDefaultState());
        h.setBlock(blind, PcBlocks.LINE_BLIND.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(BlockStateProperties.OPEN, true));
        FakePlayer a = player(h, "Alice");
        h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    var be = (dev.denfry.promptcritical.mod.ops.LineBlindBlockEntity) h.getBlockEntity(blind);
                    var water = new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 500);
                    var in = be.fluids(Direction.SOUTH);
                    h.assertTrue(in != null && in.fill(water, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE) == 500,
                            "open blind passes water to the tank");
                    be.use(a);
                    h.assertTrue(!be.open(), "turned to blind");
                    h.assertTrue(be.fluids(Direction.SOUTH).fill(water.copy(), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE) == 0,
                            "blind side passes nothing");
                    var r = Lockouts.registry(h.getLevel());
                    r.apply(Lockouts.key(h.absolutePos(blind)), true, a.getUUID(), "Alice", "", 0, 0, 8);
                    be.use(a);
                    h.assertTrue(!be.open(), "a locked blind is not turned");
                })
                .thenSucceed();
    }

    /** Ящик блокировки: ключи ответственного в ящике, бригада на ящике — свои замки с точек он не снимет. */
    @GameTest(template = LARGE, timeoutTicks = 300)
    public static void lockoutBoxHoldsTheResponsiblesLocksWhileTheCrewIsOnIt(GameTestHelper h) {
        place(h);
        BlockPos box = new BlockPos(8, 1, 8);
        h.setBlock(box, PcBlocks.LOCKOUT_BOX.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        FakePlayer lead = player(h, "Lead"), crew = player(h, "Crew");
        h.startSequence()
                .thenWaitUntil(() -> unit(h))
                .thenExecute(() -> {
                    cell(h).operate(null, false);
                    click(h, lead, new ItemStack(PcItems.LOCKOUT_PADLOCK.get()), false);
                    var b = (dev.denfry.promptcritical.mod.ops.LockoutBoxBlockEntity) h.getBlockEntity(box);
                    b.use(lead); // ключи — в ящик
                    var r = Lockouts.registry(h.getLevel());
                    var boxKey = Lockouts.key(h.absolutePos(box));
                    r.fitHasp(boxKey);
                    h.assertTrue(r.apply(boxKey, true, crew.getUUID(), "Crew", "", 0, 0, 8) == LockoutRegistry.LockResult.OK, "crew lock on the box");
                    click(h, lead, ItemStack.EMPTY, true);
                    h.assertTrue(state(h) == IsolationState.LOCKED, "the lead cannot take the lock off while the crew holds the box");
                    r.remove(boxKey, crew.getUUID());
                    b.use(lead); // ключи — обратно
                    click(h, lead, ItemStack.EMPTY, true);
                    h.assertTrue(state(h) == IsolationState.ISOLATED, "lead's lock off after the crew left");
                })
                .thenSucceed();
    }

}

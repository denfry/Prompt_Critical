package dev.denfry.promptcritical.mod.gametest;

import com.mojang.authlib.GameProfile;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.grid.DisconnectorBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.paperwork.Paperwork;
import dev.denfry.promptcritical.mod.paperwork.PermitRecord;
import dev.denfry.promptcritical.mod.paperwork.ShiftLog;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.grid.SwitchUnit;
import dev.denfry.promptcritical.sim.grid.WorkPermit;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Документы завода в мире: наряд-допуск запирает разъединитель, оперативный журнал пишет показания щита. */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PaperworkGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";

    private PaperworkGameTests() {
    }

    private static ItemStack find(FakePlayer p, net.minecraft.world.item.Item item) {
        for (ItemStack s : p.getInventory().items) if (s.is(item) && s.has(Paperwork.WORK_PERMIT.get())) return s;
        return ItemStack.EMPTY;
    }

    /**
     * Под открытым нарядом разъединитель не включается (плакат висит, нож разомкнут); наряд закрыт — включается.
     * Допуск ждёт заземления, а заземление снимают только после закрытия наряда.
     */
    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void disconnectorStaysOpenUnderAPermitAndClosesAfterIt(GameTestHelper h) {
        BlockPos at = new BlockPos(5, 1, 5);
        h.setBlock(at, PcBlocks.DISCONNECTOR.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        FakePlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "pc_electrician"));
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(((DisconnectorBlockEntity) h.getBlockEntity(at)).unit() != null, "disconnector twin"))
                .thenExecute(() -> {
                    DisconnectorBlockEntity be = h.getBlockEntity(at);
                    SwitchUnit u = be.unit();
                    if (u.closed()) be.use(p);
                    h.assertFalse(u.closed(), "disconnector open before the permit");
                    ItemStack blank = new ItemStack(PcItems.WORK_PERMIT.get(), 3);
                    Paperwork.usePermit(p, blank, be);
                    h.assertTrue(blank.getCount() == 2, "one form used");
                    ItemStack permit = find(p, PcItems.WORK_PERMIT.get());
                    PermitRecord rec = permit.get(Paperwork.WORK_PERMIT.get());
                    h.assertTrue(rec != null && rec.stage() == WorkPermit.Stage.PREPARING && rec.number() > 0, "permit written, got " + rec);
                    h.assertTrue(u.openPermits() == 1, "apparatus held");

                    be.use(p);                                                          // попытка включить ножи
                    h.assertFalse(u.closed(), "a disconnector under a permit does not close");

                    Paperwork.usePermit(p, permit, be);                                 // допуск без заземления — нельзя
                    h.assertTrue(permit.get(Paperwork.WORK_PERMIT.get()).stage() == WorkPermit.Stage.PREPARING, "no admission unearthed");
                    p.setShiftKeyDown(true);
                    be.use(p);                                                          // заземляющие ножи
                    p.setShiftKeyDown(false);
                    h.assertTrue(u.earthed(), "earthed");
                    Paperwork.usePermit(p, permit, be);
                    h.assertTrue(permit.get(Paperwork.WORK_PERMIT.get()).stage() == WorkPermit.Stage.ADMITTED, "brigade admitted");
                    p.setShiftKeyDown(true);
                    be.use(p);                                                          // снять заземление под нарядом
                    h.assertTrue(u.earthed(), "earthing stays on while the permit is open");

                    Paperwork.usePermit(p, permit, be);                                 // закрыть наряд
                    h.assertTrue(permit.get(Paperwork.WORK_PERMIT.get()).stage() == WorkPermit.Stage.CLOSED, "permit closed");
                    h.assertTrue(u.openPermits() == 0, "apparatus released");
                    be.use(p);                                                          // теперь заземление снимается
                    p.setShiftKeyDown(false);
                    h.assertFalse(u.earthed(), "earthing removed");
                    be.use(p);
                    h.assertTrue(u.closed(), "disconnector closes once the permit is closed");
                })
                .thenSucceed();
    }

    /** Журнал по щиту токарного станка: строка с показаниями; сдача смены — подпись, новая вахта с нуля. */
    @GameTest(template = LARGE, timeoutTicks = 100)
    public static void shiftLogWritesPanelReadingsAndSignsTheHandover(GameTestHelper h) {
        BlockPos lathe = new BlockPos(5, 1, 5);
        h.setBlock(lathe, PcBlocks.MACHINE_LATHE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        FakePlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "pc_operator"));
        ItemStack log = new ItemStack(PcItems.SHIFT_LOG.get());
        h.runAfterDelay(5, () -> {
            var be = h.getBlockEntity(lathe);
            h.assertTrue(be instanceof PanelHost, "lathe has a local panel");
            Paperwork.writeReadings(p, log, (PanelHost) be, be);
            Paperwork.writeReadings(p, log, (PanelHost) be, be);
            ShiftLog j = log.get(Paperwork.SHIFT_LOG.get());
            h.assertTrue(j != null && j.entries().size() == 2 && !j.entries().get(0).values().isEmpty(), "two readings, got " + j);
            h.assertTrue(j.entries().get(0).author().equals("pc_operator"), "author");
            h.assertTrue(j.currentShiftEntries() == 2, "two entries this shift");
            Paperwork.handOver(p, log);
            j = log.get(Paperwork.SHIFT_LOG.get());
            h.assertTrue(j.entries().size() == 3 && j.entries().get(2).handover() && j.currentShiftEntries() == 0, "signed handover");
            Paperwork.handOver(p, log);
            h.assertTrue(log.get(Paperwork.SHIFT_LOG.get()).entries().size() == 3, "nothing to sign twice");
            h.succeed();
        });
    }
}

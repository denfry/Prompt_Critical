package dev.denfry.promptcritical.mod.ops;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.sim.ops.Turnaround;
import dev.denfry.promptcritical.sim.ops.TurnaroundPhase;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Щит стенда нарядов-допусков {@code permit_board} (13 §13.2.2) с остановочным ремонтом установки (13 §13.6.2). */
public final class PermitBoardPanels {
    public static final int PHASE = 0, RUNNING = 1, OPEN = 2, DONE = 3, LOCKS = 4, SIS_BYPASSED = 5, SIS_OVERDUE = 6, FLAGS = 7,
            MISSING = 8, ACTIVE = 9, SCOPE = 10, CHANNELS = 11;
    public static final int F_GAS = 0, F_COMPLETE = 1;
    public static final int B_NEXT = 0, B_RESET = 1, B_SCOPE_DOWN = 2, B_SCOPE_UP = 3;

    private static final Tr TITLE = PanelText.tr("permit_board.phase", "Turnaround: %s", "Остановочный ремонт: %s"),
            PLANT = PanelText.tr("permit_board.plant", "Machines running %s · permits open %s · active %s · done under permits %s",
                    "Работают машин %s · нарядов открыто %s · действует %s · окончено по нарядам %s"),
            SAFETY = PanelText.tr("permit_board.safety", "Locks on %s · SIS bypassed %s · SIS proof tests overdue %s",
                    "Замков %s · ПАЗ в байпасе %s · просрочено проверок ПАЗ %s"),
            NEEDS = PanelText.tr("permit_board.needs", "To go on: %s", "Чтобы продолжить: %s"),
            READY = PanelText.tr("permit_board.ready", "Ready for the next phase", "Можно переходить к следующей фазе"),
            GAS = PanelText.tr("permit_board.gas", "Gas test", "Газоанализ"),
            DONE_L = PanelText.tr("permit_board.done", "Complete", "Завершён"),
            NEXT = PanelText.tr("permit_board.next", "Next phase", "Следующая фаза"),
            RESET = PanelText.tr("permit_board.reset", "New", "Новый"),
            SCOPE_L = PanelText.tr("permit_board.scope", "Plant radius %s m", "Радиус установки %s м"),
            NEXT_TIP = PanelText.tr("permit_board.next_tip", "Move the turnaround on when the phase conditions are met (13 §13.6.2)",
                    "Перевести ремонт в следующую фазу, когда выполнены её условия (13 §13.6.2)");

    public static final PanelLayout BOARD = PanelLayout.builder("permit_board", 248, 120, CHANNELS)
            .readout(8, 22, 232, v -> TITLE.get(PermitText.phase(TurnaroundPhase.values()[Math.floorMod(v.raw(PHASE), TurnaroundPhase.values().length)])), null)
            .readout(8, 38, 232, v -> PLANT.get(v.raw(RUNNING), v.raw(OPEN), v.raw(ACTIVE), v.raw(DONE)), null)
            .readout(8, 52, 232, v -> SAFETY.get(v.raw(LOCKS), v.raw(SIS_BYPASSED), v.raw(SIS_OVERDUE)), null)
            .readout(8, 66, 232, PermitBoardPanels::needs, null)
            .lamp(8, 84, FLAGS, F_GAS, LampColor.GREEN, GAS.get())
            .lamp(80, 84, FLAGS, F_COMPLETE, LampColor.WHITE, DONE_L.get())
            .button(8, 98, 120, B_NEXT, NEXT.get(), NEXT_TIP.get())
            .button(132, 98, 60, B_RESET, RESET.get(), null)
            .readout(150, 84, 90, v -> SCOPE_L.get(v.raw(SCOPE)), null)
            .button(196, 98, 20, B_SCOPE_DOWN, Component.literal("−"), null)
            .button(220, 98, 20, B_SCOPE_UP, Component.literal("+"), null)
            .noInventory().build();

    private PermitBoardPanels() {
    }

    private static Component needs(PanelValues v) {
        int mask = v.raw(MISSING);
        if (mask == 0) return READY.get();
        EnumSet<Turnaround.Missing> set = EnumSet.noneOf(Turnaround.Missing.class);
        for (Turnaround.Missing m : Turnaround.Missing.values()) if ((mask & 1 << m.ordinal()) != 0) set.add(m);
        return NEEDS.get(PermitText.missing(set));
    }

    public static List<PanelLayout> all() {
        return List.of(BOARD);
    }
}

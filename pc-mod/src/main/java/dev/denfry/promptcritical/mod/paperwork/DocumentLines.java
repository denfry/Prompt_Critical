package dev.denfry.promptcritical.mod.paperwork;

import dev.denfry.promptcritical.mod.registry.PcItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Текст документа построчно — для окна документа и подсказки. Строка с {@code indent} пишется с отступом (показания под
 * заголовком записи), с {@code signature} — подчёркнутая подпись.
 */
public final class DocumentLines {
    public record Line(Component text, boolean indent, boolean signature) {
    }

    public record Doc(Component title, List<Line> lines) {
    }

    private DocumentLines() {
    }

    public static Doc of(ItemStack stack) {
        if (stack.is(PcItems.WORK_PERMIT.get())) return permit(stack.get(Paperwork.WORK_PERMIT.get()));
        return log(stack.getOrDefault(Paperwork.SHIFT_LOG.get(), ShiftLog.EMPTY));
    }

    static Doc log(ShiftLog journal) {
        List<Line> out = new ArrayList<>();
        if (journal.entries().isEmpty()) out.add(new Line(Component.translatable(PaperworkText.LOG_EMPTY), false, false));
        for (ShiftLog.Entry e : journal.entries()) {
            if (e.handover()) {
                out.add(new Line(Component.translatable(PaperworkText.LOG_HANDOVER, e.author()).append(" — ")
                        .append(PlantClock.stamp(e.day(), e.minute())), false, true));
                continue;
            }
            out.add(new Line(PlantClock.stamp(e.day(), e.minute()).append(" · ").append(e.machine()).append(" · " + e.author()), false, false));
            for (Component v : e.values()) out.add(new Line(v, true, false));
        }
        return new Doc(Component.translatable(PaperworkText.LOG_TITLE), out);
    }

    static Doc permit(PermitRecord r) {
        List<Line> out = new ArrayList<>();
        if (r == null) {
            out.add(new Line(Component.translatable(PaperworkText.PERMIT_BLANK), false, false));
            return new Doc(Component.translatable(PaperworkText.PERMIT_TITLE, "____"), out);
        }
        Component dash = Component.translatable(PaperworkText.PERMIT_NOT_YET);
        out.add(new Line(Component.translatable(PaperworkText.PERMIT_FORM), false, false));
        out.add(new Line(Component.translatable(PaperworkText.PERMIT_APPARATUS, r.apparatus(),
                r.pos().getX() + " " + r.pos().getY() + " " + r.pos().getZ()), false, false));
        out.add(new Line(Component.translatable(PaperworkText.PERMIT_ISSUED_LINE, r.issuer(), PlantClock.stamp(r.day(), r.minute())), false, false));
        out.add(new Line(Component.translatable(PaperworkText.PERMIT_MEASURES), true, false));
        out.add(new Line(Component.translatable(PaperworkText.PERMIT_ADMITTED_LINE,
                r.admittedBy().isEmpty() ? dash : Component.literal(r.admittedBy())), false, !r.admittedBy().isEmpty()));
        out.add(new Line(Component.translatable(PaperworkText.PERMIT_CLOSED_LINE,
                r.closedBy().isEmpty() ? dash : Component.literal(r.closedBy())), false, !r.closedBy().isEmpty()));
        if (r.duplicate()) out.add(new Line(Component.translatable(PaperworkText.PERMIT_DUPLICATE), true, false));
        out.add(new Line(Paperwork.stage(r.stage()), false, false));
        return new Doc(Component.translatable(PaperworkText.PERMIT_TITLE, r.number()), out);
    }
}

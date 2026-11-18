package dev.denfry.promptcritical.mod.safety;

import dev.denfry.promptcritical.mod.acoustics.MachineAcoustics;
import dev.denfry.promptcritical.mod.ops.IsolationPoint;
import dev.denfry.promptcritical.mod.ops.Lockouts;
import dev.denfry.promptcritical.mod.ops.Permits;
import dev.denfry.promptcritical.sim.ops.LockoutRegistry;
import dev.denfry.promptcritical.sim.ops.PermitRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * Карточка пункта обхода (13 §13.3.1): что видит и слышит обходчик в {@value #CARD_RADIUS} блоках от метки. Даёт то,
 * чего нет у телеметрии: раннюю акустическую диагностику и осмотр.
 *
 * <ul>
 *   <li>послушать — вращающиеся машины Create: износ подшипника D_b от {@value #AUDIBLE_BEARING} — «шум подшипника»
 *   [BALANCE: порог, при котором дефект слышен на слух до вибродиагностики], заметный дисбаланс или расцентровка —
 *   «гул, биение»;</li>
 *   <li>осмотреть — запертые точки изоляции рядом, открытые наряды-допуски, замкнутые пространства с непригодной
 *   атмосферой за люком.</li>
 * </ul>
 * Сверка показаний местных приборов с телеметрией (§13.3.1, «прибор неисправен?») не сделана: у датчиков мода пока
 * нет скрытых отказов.
 */
public final class Patrols {
    public static final int CARD_RADIUS = 4;
    public static final double AUDIBLE_BEARING = 0.3, AUDIBLE_IMBALANCE = 0.5, AUDIBLE_MISALIGNMENT_MM = 0.2;

    public enum Finding { BEARING_NOISE, HUM, LOCKED_POINT, OPEN_PERMIT, BAD_AIR }

    private Patrols() {
    }

    public record Card(List<Finding> findings, int machines) {
    }

    public static Card card(ServerLevel level, BlockPos at) {
        List<Finding> out = new ArrayList<>();
        int machines = 0;
        LockoutRegistry locks = Lockouts.registry(level);
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-CARD_RADIUS, -CARD_RADIUS, -CARD_RADIUS), at.offset(CARD_RADIUS, CARD_RADIUS, CARD_RADIUS))) {
            if (!level.isLoaded(p)) continue;
            MachineAcoustics.Probe probe = MachineAcoustics.probe(level, p);
            if (probe != null && probe.running()) {
                machines++;
                if (MachineAcoustics.bearingDamage(level, p) >= AUDIBLE_BEARING) out.add(Finding.BEARING_NOISE);
                else if (MachineAcoustics.imbalance(level, p) >= AUDIBLE_IMBALANCE
                        || MachineAcoustics.misalignmentMm(level, p) >= AUDIBLE_MISALIGNMENT_MM) out.add(Finding.HUM);
            }
            var be = level.getBlockEntity(p);
            if (be instanceof IsolationPoint && locks != null && locks.isLocked(Lockouts.key(p))) out.add(Finding.LOCKED_POINT);
            if (be instanceof ManwayBlockEntity m && m.confined() && !m.fitToEnter()) out.add(Finding.BAD_AIR);
        }
        PermitRegistry r = Permits.registry(level);
        if (r != null && r.openNear(Lockouts.key(at), CARD_RADIUS + 1) > 0) out.add(Finding.OPEN_PERMIT);
        return new Card(out, machines);
    }

    /** Карточка в чат обходчику. */
    static List<Component> lines(Card c) {
        List<Component> out = new ArrayList<>();
        out.add(SafetyText.msg("patrol_card", c.machines()).withStyle(ChatFormatting.AQUA));
        if (c.findings().isEmpty()) out.add(SafetyText.msg("patrol_ok").withStyle(ChatFormatting.GREEN));
        java.util.EnumSet<Finding> seen = java.util.EnumSet.noneOf(Finding.class);
        for (Finding f : c.findings()) {
            if (!seen.add(f)) continue;
            long n = c.findings().stream().filter(x -> x == f).count();
            out.add(SafetyText.msg("patrol." + f.name().toLowerCase(java.util.Locale.ROOT), n)
                    .withStyle(f == Finding.BEARING_NOISE || f == Finding.BAD_AIR ? ChatFormatting.RED : ChatFormatting.GOLD));
        }
        return out;
    }
}

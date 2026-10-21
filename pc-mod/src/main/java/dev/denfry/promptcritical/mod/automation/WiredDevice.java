package dev.denfry.promptcritical.mod.automation;

import dev.denfry.promptcritical.sim.automation.AutomationKind;
import dev.denfry.promptcritical.sim.automation.AutomationSubsystem;
import dev.denfry.promptcritical.sim.automation.AutomationUnit;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * BE с клеммами автоматики (03d §3d.2.1): блоки A1/A2, элементы пульта и механизмы этапа 8. Провода хранят оба конца;
 * {@link AutomationWorld} собирает из них цепи и отдаёт двойнику ({@link AutomationUnit#setNets}).
 */
public interface WiredDevice {
    BlockPos wiredPos();

    AutomationKind wiredKind();

    /** Фасад модели (клеммы поворачиваются вместе с ним). */
    Direction wiredFacing();

    /** Провода от клемм этого блока. */
    List<Wire> wires();

    /** Провода изменились: сохранить и отослать клиенту. */
    void wiresChanged();

    /** Двойник в домене (создаётся при первом обращении); {@code null} — блок ещё не в топологии. */
    @Nullable
    AutomationUnit automationUnit(AutomationSubsystem automation);

    default Vec3 terminalPoint(int terminal) {
        return TerminalLayout.world(wiredPos(), wiredFacing(), TerminalLayout.of(wiredKind()).get(terminal));
    }

    default Direction terminalNormal(int terminal) {
        return TerminalLayout.normal(wiredFacing(), TerminalLayout.of(wiredKind()).get(terminal));
    }

    /** Ближайшая к точке клик клемма в пределах {@code maxDist} блока; -1 — нет. */
    default int terminalAt(Vec3 hit, double maxDist) {
        int best = -1;
        double bestD = maxDist * maxDist;
        for (int t = 0; t < wiredKind().terminalCount(); t++) {
            double d = terminalPoint(t).distanceToSqr(hit);
            if (d < bestD) {
                bestD = d;
                best = t;
            }
        }
        return best;
    }

    /** Провод от клеммы {@code from} к клемме {@code to} блока {@code other}. */
    record Wire(int from, BlockPos other, int to) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("from", from);
            t.putLong("other", other.asLong());
            t.putInt("to", to);
            return t;
        }

        static Wire load(CompoundTag t) {
            return new Wire(t.getInt("from"), BlockPos.of(t.getLong("other")), t.getInt("to"));
        }
    }

    static ListTag saveWires(List<Wire> wires) {
        ListTag list = new ListTag();
        for (Wire w : wires) list.add(w.save());
        return list;
    }

    static void loadWires(CompoundTag tag, List<Wire> into) {
        into.clear();
        for (Tag e : tag.getList("wires", Tag.TAG_COMPOUND)) into.add(Wire.load((CompoundTag) e));
    }

    static List<Wire> copy(List<Wire> wires) {
        return new ArrayList<>(wires);
    }
}

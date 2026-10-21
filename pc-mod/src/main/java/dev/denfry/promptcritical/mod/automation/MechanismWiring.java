package dev.denfry.promptcritical.mod.automation;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.sim.automation.AutomationKind;
import dev.denfry.promptcritical.sim.automation.AutomationSubsystem;
import dev.denfry.promptcritical.sim.automation.AutomationUnit;
import dev.denfry.promptcritical.sim.core.SimId;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Клеммы механизма мира (03d §3d.3.1: «дискретные сигналы механизмов выводятся на клеммы самого механизма»): провода
 * и регистрация BE ячейки КРУ, щита 0,4 кВ, двигателя и потребителя в {@link AutomationWorld}. BE делегирует сюда
 * методы {@link WiredDevice} и жизненный цикл.
 */
public final class MechanismWiring {
    private final List<WiredDevice.Wire> wires = new ArrayList<>();
    private boolean registered, unloading;

    public List<WiredDevice.Wire> wires() {
        return wires;
    }

    public void onLoad(BlockEntity be, WiredDevice self) {
        unloading = false;
        Level level = be.getLevel();
        if (level instanceof ServerLevel s) {
            SimLevelBinding b = SimBindings.get(s);
            if (b != null) {
                b.automation().register(be.getBlockPos());
                registered = true;
            }
        } else if (level != null) {
            ClientWires.add(self);
        }
    }

    public void onChunkUnloaded() {
        unloading = true;
    }

    public void setRemoved(BlockEntity be, WiredDevice self) {
        if (be.getLevel() instanceof ServerLevel s) {
            SimLevelBinding b = registered ? SimBindings.get(s) : null;
            if (b != null) {
                if (!unloading) b.automation().deviceRemoved(self);
                b.automation().unregister(be.getBlockPos());
            }
            registered = false;
        } else {
            ClientWires.remove(self);
        }
    }

    /** Двойник клемм механизма; {@code null} — биндинга нет или механизм ещё не в топологии. */
    @Nullable
    public static AutomationUnit unit(@Nullable Level level, SimId id, AutomationKind kind) {
        SimLevelBinding b = level instanceof ServerLevel s ? SimBindings.get(s) : null;
        if (b == null || id.isNone() || !b.world().topology().exists(id)) return null;
        AutomationSubsystem a = b.world().subsystem(AutomationSubsystem.class);
        return a == null ? null : a.unit(id, kind);
    }

    /** Провода изменились: сохранить и отослать клиенту. */
    public static void changed(BlockEntity be) {
        be.setChanged();
        Level level = be.getLevel();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), Block.UPDATE_CLIENTS);
    }

    public void save(CompoundTag tag) {
        tag.put("wires", WiredDevice.saveWires(wires));
    }

    public void load(CompoundTag tag) {
        WiredDevice.loadWires(tag, wires);
    }
}

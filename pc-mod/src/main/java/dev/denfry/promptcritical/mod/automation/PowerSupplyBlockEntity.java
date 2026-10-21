package dev.denfry.promptcritical.mod.automation;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.grid.GridDevice;
import dev.denfry.promptcritical.sim.automation.AutomationKind;
import dev.denfry.promptcritical.sim.automation.AutomationUnit;
import dev.denfry.promptcritical.sim.grid.AuxSupplyUnit;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.GridUnit;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * {@code relay_power_supply} (03d §3d.3.1, §3d.13.4): выпрямитель 24 В DC от шины 0,4 кВ — кабель к любой грани, кроме
 * стены и фасада. Без шины под напряжением реле отпадают. {@link #setBenchInput} — лабораторный источник для стенда
 * (GameTest): вход в о.е. вместо шины.
 */
public class PowerSupplyBlockEntity extends AutomationPanelBlockEntity implements GridDevice {
    private boolean gridRegistered;
    private double benchInput = Double.NaN;

    public PowerSupplyBlockEntity(BlockPos pos, BlockState state) {
        super(AutomationBlockEntities.RELAY_POWER_SUPPLY.get(), pos, state, AutomationKind.POWER_SUPPLY);
    }

    /** Питание от стенда, о.е. (NaN — от шины). */
    public void setBenchInput(double pu) {
        benchInput = pu;
    }

    @Override
    protected void serverTick(AutomationUnit u) {
        AuxSupplyUnit aux = aux();
        double rated = u.supplyRatedA();
        if (aux != null) aux.setRatedPowerW(rated * 24 / AuxSupplyUnit.EFFICIENCY);
        double in = !Double.isNaN(benchInput) ? benchInput : aux == null ? 0 : aux.inputPu();
        u.setInput(AutomationUnit.IN_MAIN, in);
        super.serverTick(u);
        setFlag(BlockStateProperties.POWERED, u.supplyVoltage() > 1);
        setFlag(PcProperties.ALARM, in > 0.5 && u.supplyVoltage() < 0.7 * 24 * in);
    }

    @Nullable
    private AuxSupplyUnit aux() {
        SimLevelBinding b = level instanceof ServerLevel s ? SimBindings.get(s) : null;
        if (b == null || simId().isNone() || !b.world().topology().exists(simId())) return null;
        GridSubsystem g = b.world().subsystem(GridSubsystem.class);
        return g == null ? null : g.auxSupply(simId());
    }

    // --- вход от сети ---

    @Override
    public List<List<Face>> terminals() {
        Direction f = wiredFacing();
        List<Face> faces = new ArrayList<>();
        for (Direction d : Direction.values()) if (d != f && d != f.getOpposite()) faces.add(new Face(worldPosition, d));
        return List.of(faces);
    }

    @Override
    @Nullable
    public GridUnit gridUnit(GridSubsystem grid) {
        return simId().isNone() ? null : grid.auxSupply(simId());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel s) {
            SimLevelBinding b = SimBindings.get(s);
            if (b != null) {
                b.grids().register(worldPosition);
                gridRegistered = true;
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (gridRegistered && level instanceof ServerLevel s) {
            SimLevelBinding b = SimBindings.get(s);
            if (b != null) b.grids().unregister(worldPosition);
            gridRegistered = false;
        }
    }
}

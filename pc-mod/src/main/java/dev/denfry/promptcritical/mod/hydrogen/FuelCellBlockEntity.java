package dev.denfry.promptcritical.mod.hydrogen;

import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import dev.denfry.promptcritical.mod.grid.GridDevice;
import dev.denfry.promptcritical.mod.grid.GridDeviceBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.InverterUnit;
import dev.denfry.promptcritical.sim.hydrogen.HydrogenGas;
import dev.denfry.promptcritical.sim.hydrogen.HydrogenStorage;
import dev.denfry.promptcritical.sim.hydrogen.PemFuelCell;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Модуль топливных элементов PEM 200 кВт с сетевым инвертором ({@link PemFuelCell}): водород — по газовой линии от
 * порта 0 к рампе, переменный ток 0,4 кВ — кабелем к восточной грани инвертора (порт 1). Инвертор отдаёт в сеть
 * задание (до 200 кВт), сколько модуль может при нынешней температуре и подаче водорода. Вода продукта — в бак или
 * трубу под портом 2, иначе в дренаж. ПКМ — щит, Shift + ПКМ — пуск или стоп.
 */
public class FuelCellBlockEntity extends GridDeviceBlockEntity<InverterUnit> implements H2Machine, PanelHost, WholeModelHost {
    static final double AMBIENT_K = 293.15;
    private final PemFuelCell cell = new PemFuelCell();
    private GasLines.End supply;
    private boolean hasLine, gridBound;
    private double waterDebt;

    public FuelCellBlockEntity(BlockPos pos, BlockState state) {
        super(HydrogenBlockEntities.FUEL_CELL.get(), pos, state);
    }

    public PemFuelCell cell() {
        return cell;
    }

    @Override
    protected InverterUnit unitOf(GridSubsystem grid) {
        InverterUnit u = grid.inverter(simId());
        u.configure(PemFuelCell.RATED_AC_W, 1.0); // КПД инвертора — в модели модуля
        return u;
    }

    @Override
    public List<List<Face>> terminals() {
        List<Face> out = new ArrayList<>();
        GridDevice.faces(out, worldPosition, wholeModelFacing(), 1, 0, 0, Direction.EAST);
        return List.of(out);
    }

    @Override
    public void tickDevice() {
        super.tickDevice();
        if (!(level instanceof ServerLevel sl)) return;
        if (ticks % 20 == 1) {
            supply = GasLines.firstStore(GasLines.trace(sl, this, 0));
            hasLine = supply != null;
        }
        double dt = 0.05;
        HydrogenStorage store = hasLine ? supply.machine().storage() : null;
        double supplyPa = store != null ? store.pressurePa() : 0;
        double avail = store != null ? store.availableMol(PemFuelCell.MIN_SUPPLY_PA) : 0;
        InverterUnit u = unit();
        gridBound = u != null && u.bound();
        double acNow = 0;
        if (u != null) {
            u.configure(PemFuelCell.RATED_AC_W, 1.0);
            double maxOut = cell.on() && cell.trip() == PemFuelCell.Trip.NONE ? cell.availableW() : 0;
            u.setDispatch(cell.setpointW(), maxOut, 0);
            acNow = Math.max(0, u.powerW());
        }
        double used = cell.step(dt, supplyPa, avail, acNow, AMBIENT_K, true);
        if (store != null && used > 0) store.take(used, PemFuelCell.MIN_SUPPLY_PA);
        // вода продукта: 1 мБ = 1 кг
        waterDebt += cell.waterKgS() * dt;
        int mb = (int) waterDebt;
        if (mb > 0) {
            waterDebt -= mb;
            BlockPos below = portCell(2).below();
            IFluidHandler h = sl.getCapability(Capabilities.FluidHandler.BLOCK, below, Direction.UP);
            if (h != null) h.fill(new FluidStack(Fluids.WATER, mb), IFluidHandler.FluidAction.EXECUTE);
        }
        if (cell.acW() > 1e3 && ticks % 6 == 0) {
            BlockPos ex = portCell(3).above();
            sl.sendParticles(ParticleTypes.CLOUD, ex.getX() + 0.5, ex.getY() + 0.1, ex.getZ() + 0.5, 1, 0.1, 0.1, 0.1, 0.01);
        }
        if (ticks % 10 == 0) setFlag(PcProperties.RUNNING, cell.acW() > 1e3);
        if (ticks % 100 == 0) setChanged();
    }

    // --- щит и руки ---

    private void switchTo(boolean on, @Nullable Player player) {
        cell.setOn(on);
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, 0.8f);
        if (player != null) player.displayClientMessage(HydrogenText.msg(on ? "fuel_cell.on" : "fuel_cell.off"), true);
        setChanged();
    }

    @Override
    public InteractionResult use(Player player) {
        switchTo(!cell.on(), player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == HydrogenPanels.BUTTON_START) switchTo(true, player);
        else if (id == HydrogenPanels.BUTTON_STOP) switchTo(false, player);
    }

    static int tripCode(PemFuelCell.Trip t) {
        return switch (t) {
            case NONE -> 0;
            case OVERTEMP -> 2;
            case LOW_H2 -> 5;
        };
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        HydrogenStorage store = hasLine ? supply.machine().storage() : null;
        out[HydrogenPanels.FC_H2_BAR10] = store != null ? (int) Math.round(store.pressurePa() / 1e4) : 0;
        out[HydrogenPanels.FC_VOLTS] = (int) Math.round(cell.stackVoltage());
        out[HydrogenPanels.FC_KW] = (int) Math.round(cell.acW() / 1e3);
        out[HydrogenPanels.FC_WATER_LH] = (int) Math.round(cell.waterKgS() * 3600);
        out[HydrogenPanels.FC_TEMP_C] = (int) Math.round(cell.tempK() - 273.15);
        out[HydrogenPanels.FC_EFF_PCT] = (int) Math.round(cell.efficiencyLhv() * 100);
        out[HydrogenPanels.FC_TRIP] = tripCode(cell.trip());
        out[HydrogenPanels.FC_FLAGS] = (cell.acW() > 1e3 ? 1 << HydrogenPanels.F_FC_RUN : 0)
                | (cell.trip() != PemFuelCell.Trip.NONE ? 1 << HydrogenPanels.F_FC_FAULT : 0)
                | (gridBound ? 1 << HydrogenPanels.F_FC_GRID : 0) | (hasLine ? 1 << HydrogenPanels.F_FC_LINE : 0);
    }

    /** Водород, который модуль сейчас берёт, Нм³/ч. */
    public double h2Nm3h() {
        return HydrogenGas.nm3(cell.h2MolS()) * 3600;
    }

    @Override
    public PanelLayout panelLayout() {
        return HydrogenPanels.FUEL_CELL;
    }

    @Override
    public Component panelTitle() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public BlockPos panelPos() {
        return worldPosition;
    }

    @Override
    public boolean panelValid(Player player) {
        return PanelHost.near(this, player);
    }

    // --- порты и модель ---

    @Override
    public boolean gasPort(int port) {
        return port == 0 || port == 3;
    }

    @Override
    public String h2ModelId() {
        return "h2_fuel_cell_real";
    }

    @Override
    public Direction h2Facing() {
        return wholeModelFacing();
    }

    @Override
    public BlockPos h2Controller() {
        return worldPosition;
    }

    @Override
    @Nullable
    public String wholeModelId() {
        return WholeModelHost.idOf(getBlockState());
    }

    @Override
    public Direction wholeModelFacing() {
        BlockState s = getBlockState();
        return s.getBlock() instanceof PcBlock b ? b.facing(s) : Direction.NORTH;
    }

    @Override
    public BlockPos wholeModelLightPos() {
        return worldPosition.relative(wholeModelFacing());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("cell", H2Nbt.save(cell::save));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("cell")) cell.load(H2Nbt.reader(tag.getCompound("cell")));
    }
}

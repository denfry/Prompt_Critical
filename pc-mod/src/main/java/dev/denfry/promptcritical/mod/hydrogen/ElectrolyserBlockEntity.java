package dev.denfry.promptcritical.mod.hydrogen;

import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.grid.GridDevice;
import dev.denfry.promptcritical.mod.grid.GridLoad;
import dev.denfry.promptcritical.mod.grid.MachinePower;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.GridUnit;
import dev.denfry.promptcritical.sim.hydrogen.AlkalineElectrolyser;
import dev.denfry.promptcritical.sim.hydrogen.HydrogenStorage;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Щелочной электролизёр 2,2 МВт ({@link AlkalineElectrolyser}). Выпрямитель питается от своего ввода 6,3 кВ: кабель 6 кВ
 * — к западной грани шин (порт 0), выключатель ввода с нулевой защитой ({@link MachinePower.Mv}). Обессоленная вода —
 * в порт 1, вода охлаждения щёлочи — в порт 4 (трубы Create с насосом). Водород идёт по газовой линии от порта 2 в
 * газгольдер; линии нет — в свечу (и в воздух). Кислород — в атмосферу через порт 3. ПКМ — щит, Shift + ПКМ — пуск
 * или стоп.
 */
public class ElectrolyserBlockEntity extends DeviceBlockEntity implements H2Machine, GridDevice, FoundryDevice, PanelHost, WholeModelHost {
    /** КПД выпрямителя и наибольшая мощность постоянного тока, Вт. */
    static final double RECTIFIER_EFF = 0.97, RECTIFIER_W = 2.5e6;
    static final double AMBIENT_K = 293.15, H2_OUT_K = 308.15;
    private final AlkalineElectrolyser cells = new AlkalineElectrolyser();
    private final MachinePower.Mv mv = new MachinePower.Mv();
    private final FluidTank water = tank(), cooling = tank();
    private int ticks;
    private double coolingDebt, waterDebt;
    private boolean noLine = true, noPower;
    private GasLines.End outlet;
    /** Водород, ушедший в воздух (нет линии), моль — для облака. */
    private double releasedMol;

    public ElectrolyserBlockEntity(BlockPos pos, BlockState state) {
        super(HydrogenBlockEntities.ELECTROLYSER.get(), pos, state);
    }

    private FluidTank tank() {
        return new FluidTank(4000, f -> f.getFluid().isSame(Fluids.WATER)) {
            @Override
            protected void onContentsChanged() {
                setChanged();
            }
        };
    }

    public AlkalineElectrolyser cells() {
        return cells;
    }

    public double releasedMol() {
        return releasedMol;
    }

    /** Забрать учтённый выброс водорода в воздух, моль. */
    public double takeReleased() {
        double r = releasedMol;
        releasedMol = 0;
        return r;
    }

    @Override
    public void tickDevice() {
        ticks++;
        if (!(level instanceof ServerLevel sl)) return;
        if (ticks % 20 == 1) {
            outlet = GasLines.firstStore(GasLines.trace(sl, this, 2));
            noLine = outlet == null;
        }
        double dt = 0.05;
        HydrogenStorage store = noLine ? null : outlet.machine().storage();
        // ввод 6,3 кВ: выпрямитель просит мощность батареи с запасом на КПД
        double want = cells.on() ? cells.ratedDcW() / RECTIFIER_EFF : 0;
        double f = mv.supply(level, worldPosition, simId(), want);
        if (cells.on() && mv.tripped()) cells.setOn(false);
        noPower = cells.on() && f <= 0;
        double dcAvail = f > 0 ? RECTIFIER_W * Math.min(1, f) : 0;
        // вода подпитки: 1 мБ = 1 кг
        double makeup = water.getFluidAmount();
        boolean coolingOk = cooling.getFluidAmount() > 0;
        double outletPa = store != null ? store.pressurePa() : HydrogenStorage.ATM_PA;
        double used = cells.step(dt, dcAvail, makeup, coolingOk, outletPa, AMBIENT_K);
        waterDebt += used;
        int mb = (int) waterDebt;
        if (mb > 0) {
            water.drain(mb, IFluidHandler.FluidAction.EXECUTE);
            waterDebt -= mb;
        }
        coolingDebt += cells.coolerW() / (4186 * 10) * dt;
        mb = (int) coolingDebt;
        if (mb > 0) {
            cooling.drain(mb, IFluidHandler.FluidAction.EXECUTE);
            coolingDebt -= mb;
        }
        double mol = cells.h2MolS() * dt;
        if (store != null) store.add(mol, H2_OUT_K);
        else releasedMol += mol;
        if (cells.h2MolS() > 0 && ticks % 5 == 0) {
            // кислород из своего патрубка и водород из свечи, если линии нет
            BlockPos o2 = portCell(3).above();
            sl.sendParticles(ParticleTypes.CLOUD, o2.getX() + 0.5, o2.getY() + 0.1, o2.getZ() + 0.5, 1, 0.05, 0.1, 0.05, 0.02);
            if (store == null) {
                BlockPos h2 = portCell(2).above();
                sl.sendParticles(ParticleTypes.CLOUD, h2.getX() + 0.5, h2.getY() + 0.1, h2.getZ() + 0.5, 2, 0.05, 0.2, 0.05, 0.04);
            }
        }
        if (ticks % 40 == 0 && cells.amps() > 0) sl.playSound(null, worldPosition, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.25f, 0.6f);
        if (ticks % 10 == 0) setFlag(cells.amps() > 0);
        if (ticks % 100 == 0) setChanged();
    }

    private void setFlag(boolean running) {
        BlockState s = getBlockState();
        if (s.hasProperty(PcProperties.RUNNING) && s.getValue(PcProperties.RUNNING) != running)
            level.setBlock(worldPosition, s.setValue(PcProperties.RUNNING, running), Block.UPDATE_CLIENTS);
    }

    @Override
    @Nullable
    public IFluidHandler portFluids(int port) {
        return port == 1 ? water : port == 4 ? cooling : null;
    }

    // --- сеть 6,3 кВ ---

    @Override
    public List<List<Face>> terminals() {
        List<Face> out = new ArrayList<>();
        GridDevice.faces(out, worldPosition, wholeModelFacing(), -4, 1, 0, Direction.WEST);
        return List.of(out);
    }

    @Override
    @Nullable
    public GridUnit gridUnit(GridSubsystem grid) {
        return simId().isNone() ? null : grid.consumer(simId());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        GridLoad.register(level, worldPosition);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        GridLoad.unregister(level, worldPosition);
    }

    // --- щит и руки ---

    private void switchTo(boolean on, @Nullable Player player) {
        if (on) mv.start();
        else mv.stop();
        cells.setOn(on);
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, 0.8f);
        if (player != null) {
            String key = on && !cells.on() ? "electrolyser.purge_first" : on ? "electrolyser.on" : "electrolyser.off";
            player.displayClientMessage(HydrogenText.msg(key), true);
        }
        setChanged();
    }

    @Override
    public InteractionResult use(Player player) {
        switchTo(!cells.on(), player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case HydrogenPanels.BUTTON_START -> switchTo(true, player);
            case HydrogenPanels.BUTTON_STOP -> switchTo(false, player);
            case HydrogenPanels.BUTTON_PURGE -> {
                if (cells.on()) switchTo(false, player);
                cells.purge();
                player.displayClientMessage(HydrogenText.msg("electrolyser.purge"), true);
            }
            default -> {
            }
        }
    }

    static int tripCode(AlkalineElectrolyser.Trip t) {
        return switch (t) {
            case NONE -> 0;
            case HTO -> 1;
            case OVERTEMP -> 2;
            case OUTLET_PRESSURE -> 3;
            case LYE -> 4;
        };
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        double koh = cells.kohFraction();
        int level0 = (int) Math.round(Math.min(1, 0.30 / koh * 0.6) * 1000);
        out[HydrogenPanels.E_AMPS] = (int) Math.round(cells.amps());
        out[HydrogenPanels.E_VOLTS] = (int) Math.round(cells.stackVoltage());
        // газ в щёлочи поднимает уровень в сепараторе водорода чуть выше, чем кислорода
        out[HydrogenPanels.E_H2_GLASS] = level0 + (int) Math.round(40 * cells.amps() / AlkalineElectrolyser.NOMINAL_A);
        out[HydrogenPanels.E_O2_GLASS] = level0 + (int) Math.round(20 * cells.amps() / AlkalineElectrolyser.NOMINAL_A);
        out[HydrogenPanels.E_TEMP_C10] = (int) Math.round((cells.tempK() - 273.15) * 10);
        out[HydrogenPanels.E_KOH_PERMIL] = (int) Math.round(koh * 1000);
        out[HydrogenPanels.E_HTO_PPM] = (int) Math.round(cells.hto() * 1e6);
        out[HydrogenPanels.E_NM3H] = (int) Math.round(cells.h2Nm3h());
        out[HydrogenPanels.E_KWH100] = (int) Math.round(cells.specificKwhPerNm3() * 100);
        out[HydrogenPanels.E_TRIP] = tripCode(cells.trip());
        out[HydrogenPanels.E_FLAGS] = (cells.amps() > 0 ? 1 << HydrogenPanels.F_RUN : 0)
                | (cells.hto() >= AlkalineElectrolyser.HTO_ALARM ? 1 << HydrogenPanels.F_HTO_ALARM : 0)
                | (cells.trip() != AlkalineElectrolyser.Trip.NONE ? 1 << HydrogenPanels.F_TRIP : 0)
                | (cells.purging() ? 1 << HydrogenPanels.F_PURGE : 0)
                | (cooling.getFluidAmount() <= 0 ? 1 << HydrogenPanels.F_NO_COOLING : 0)
                | (water.getFluidAmount() <= 0 ? 1 << HydrogenPanels.F_NO_WATER : 0)
                | (noLine ? 1 << HydrogenPanels.F_NO_LINE : 0) | (noPower ? 1 << HydrogenPanels.F_NO_POWER : 0);
    }

    @Override
    public PanelLayout panelLayout() {
        return HydrogenPanels.ELECTROLYSER;
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

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return false;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    // --- порты и модель ---

    @Override
    public String h2ModelId() {
        return "h2_electrolyser_real";
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
        tag.put("cells", H2Nbt.save(cells::save));
        mv.save(tag);
        tag.put("water", water.writeToNBT(registries, new CompoundTag()));
        tag.put("cooling", cooling.writeToNBT(registries, new CompoundTag()));
        tag.putDouble("released", releasedMol);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("cells")) cells.load(H2Nbt.reader(tag.getCompound("cells")));
        mv.load(tag);
        if (tag.contains("water")) water.readFromNBT(registries, tag.getCompound("water"));
        if (tag.contains("cooling")) cooling.readFromNBT(registries, tag.getCompound("cooling"));
        releasedMol = tag.getDouble("released");
    }
}

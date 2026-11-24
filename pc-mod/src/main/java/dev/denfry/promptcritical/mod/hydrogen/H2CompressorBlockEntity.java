package dev.denfry.promptcritical.mod.hydrogen;

import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.grid.MachinePower;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.hydrogen.DiaphragmCompressor;
import dev.denfry.promptcritical.sim.hydrogen.HydrogenStorage;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
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
 * Мембранный компрессор водорода ({@link DiaphragmCompressor}): всас — линия к газгольдеру (порт 0), нагнетание —
 * линия к рампе (порт 1). Двигатель 132 кВт питается от розетки 3ф или силового щита 0,4 кВ рядом
 * ({@link MachinePower}), при недостатке медленнее. Вода охлаждения головок и промежуточного холодильника — в порт 3
 * (труба Create с насосом), без неё компрессор сожмёт без промежуточного охлаждения и отключится по 230 °C. Реле
 * давления пускает и останавливает его само. ПКМ — щит, Shift + ПКМ — пуск или стоп.
 */
public class H2CompressorBlockEntity extends DeviceBlockEntity implements H2Machine, FoundryDevice, PanelHost, WholeModelHost {
    static final double COOLING_K = 288.15, AFTERCOOLER_K = COOLING_K + 15, MOTOR_EFF = 0.93;
    private final DiaphragmCompressor compressor = new DiaphragmCompressor();
    private final FluidTank cooling = new FluidTank(2000, f -> f.getFluid().isSame(Fluids.WATER)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private int ticks;
    private double demandW, coolingDebt;
    private boolean hasSuction, hasDischarge, cooled;
    private GasLines.End suction, discharge;

    public H2CompressorBlockEntity(BlockPos pos, BlockState state) {
        super(HydrogenBlockEntities.H2_COMPRESSOR.get(), pos, state);
    }

    public DiaphragmCompressor compressor() {
        return compressor;
    }

    @Override
    public void tickDevice() {
        ticks++;
        if (!(level instanceof ServerLevel sl)) return;
        if (ticks % 20 == 1) {
            suction = GasLines.firstStore(GasLines.trace(sl, this, 0));
            discharge = GasLines.firstStore(GasLines.trace(sl, this, 1));
            hasSuction = suction != null;
            hasDischarge = discharge != null;
        }
        double dt = 0.05;
        HydrogenStorage in = hasSuction ? suction.machine().storage() : null, out = hasDischarge ? discharge.machine().storage() : null;
        // вода охлаждения: ≈ 80 % мощности уходит в воду, нагрев на 10 K
        cooled = cooling.getFluidAmount() > 0;
        boolean wants = compressor.on() && in != null && out != null;
        double speed = wants ? MachinePower.supply(level, worldPosition, Math.max(demandW, compressor.loaded() ? DiaphragmCompressor.MOTOR_W : 0)) : 0;
        double suctionPa = in != null ? in.pressurePa() : 0, suctionK = in != null ? in.tempK() : COOLING_K;
        double dischargePa = out != null ? out.pressurePa() : Double.MAX_VALUE;
        double mol = compressor.step(dt, suctionPa, suctionK, dischargePa, speed, cooled ? COOLING_K : Double.NaN) * dt;
        if (mol > 0 && in != null && out != null) {
            double got = in.take(mol, DiaphragmCompressor.STOP_SUCTION_PA * 0.98);
            out.add(got, cooled ? Math.min(compressor.stage2OutK(), AFTERCOOLER_K) : compressor.stage2OutK());
        }
        demandW = compressor.powerW() / MOTOR_EFF;
        if (cooled && compressor.powerW() > 0) {
            coolingDebt += compressor.powerW() * 0.8 / (4186 * 10) * dt; // кг = мБ
            int mb = (int) coolingDebt;
            if (mb > 0) {
                cooling.drain(mb, IFluidHandler.FluidAction.EXECUTE);
                coolingDebt -= mb;
            }
        }
        if (ticks % 10 == 0) setFlag(compressor.powerW() > 0);
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
        return port == 3 ? cooling : null;
    }

    // --- щит и руки ---

    private void switchTo(boolean on, @Nullable Player player) {
        if (on) compressor.reset();
        compressor.setOn(on);
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, 0.8f);
        if (player != null) player.displayClientMessage(HydrogenText.msg(on ? "compressor.on" : "compressor.off"), true);
        setChanged();
    }

    @Override
    public InteractionResult use(Player player) {
        switchTo(!compressor.on(), player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (id == HydrogenPanels.BUTTON_START) switchTo(true, player);
        else if (id == HydrogenPanels.BUTTON_STOP) switchTo(false, player);
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        HydrogenStorage in = hasSuction ? suction.machine().storage() : null, dis = hasDischarge ? discharge.machine().storage() : null;
        out[HydrogenPanels.C_SUCTION_KPA] = in != null ? (int) Math.round(in.pressurePa() / 1e3) : 0;
        out[HydrogenPanels.C_DISCHARGE_BAR10] = dis != null ? (int) Math.round(dis.pressurePa() / 1e4) : 0;
        out[HydrogenPanels.C_STAGE_C] = (int) Math.round(Math.max(compressor.stage1OutK(), compressor.stage2OutK()) - 273.15);
        out[HydrogenPanels.C_NM3H] = (int) Math.round(compressor.nm3h());
        out[HydrogenPanels.C_KW] = (int) Math.round(compressor.powerW() / 1e3);
        out[HydrogenPanels.C_FLAGS] = (compressor.powerW() > 0 ? 1 << HydrogenPanels.F_LOADED : 0)
                | (compressor.trip() != DiaphragmCompressor.Trip.NONE ? 1 << HydrogenPanels.F_TRIP : 0)
                | (!hasSuction ? 1 << HydrogenPanels.F_NO_SUCTION : 0) | (!hasDischarge ? 1 << HydrogenPanels.F_NO_DISCHARGE : 0)
                | (!cooled ? 1 << HydrogenPanels.F_NO_COOLING : 0);
    }

    @Override
    public PanelLayout panelLayout() {
        return HydrogenPanels.COMPRESSOR;
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
        return "h2_compressor_real";
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
        tag.put("compressor", H2Nbt.save(compressor::save));
        tag.put("cooling", cooling.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("compressor")) compressor.load(H2Nbt.reader(tag.getCompound("compressor")));
        if (tag.contains("cooling")) cooling.readFromNBT(registries, tag.getCompound("cooling"));
    }
}

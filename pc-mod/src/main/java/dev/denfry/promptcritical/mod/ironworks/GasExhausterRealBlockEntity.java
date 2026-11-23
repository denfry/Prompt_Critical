package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.boiler.ShaftConsumer;
import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.sim.metallurgy.CyclicMachine;
import dev.denfry.promptcritical.sim.metallurgy.FoundrySubsystem;
import dev.denfry.promptcritical.sim.metallurgy.GasMain;
import dev.denfry.promptcritical.sim.metallurgy.ProcessGas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Центробежный эксгаустер реального масштаба {@code gas_exhauster_real} (08 §8.4.4, art/blockbench/coke/gas_exhauster):
 * улитка 1,6 м, осевой всас с запада, нагнетание вверх, 3 × 3 × 3 блока. Тот же юнит, что у старого эксгаустера
 * ({@link FoundrySubsystem#GAS_EXHAUSTER}): касается газосборника батареи любой клеткой, отсасывает Q₀·s; газ — в
 * свой бак, его забирают трубы с нагнетательного патрубка (клетка (0, 2, −1) сверху). Вал Create — в клетку
 * {@link #SHAFT_PORT} с востока ({@link RealShaftBlock}).
 */
public final class GasExhausterRealBlockEntity extends DeviceBlockEntity
        implements FoundryDevice, ShaftConsumer, WholeModelHost, GasExhauster {
    /** Порт вала в модели. */
    public static final BlockPos SHAFT_PORT = new BlockPos(2, 1, 0);

    private final GasBuffer outlet = new GasBuffer(16_000, s -> s.is(GasBuffer.fluid(ProcessGas.COKE_OVEN.fluidId)), this::setChanged);

    public GasExhausterRealBlockEntity(BlockPos pos, BlockState state) {
        super(IronworksBlockEntities.GAS_EXHAUSTER_REAL.get(), pos, state);
    }

    /** В симуляции — тот же эксгаустер, что и старый. */
    @Override
    public String deviceType() {
        return FoundrySubsystem.GAS_EXHAUSTER;
    }

    @Override
    @Nullable
    public CyclicMachine machine() {
        SimLevelBinding b = level instanceof ServerLevel s ? SimBindings.get(s) : null;
        return Ironworks.shaftMachine(b, simId());
    }

    @Override
    public GasBuffer outlet() {
        return outlet;
    }

    private Direction facing() {
        return getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel)) {
            clientTick();
            return;
        }
        CyclicMachine u = machine();
        if (u == null) return;
        u.setLoaded(true);
        BlockState st = getBlockState();
        if (st.getValue(PcProperties.RUNNING) != u.running())
            level.setBlock(worldPosition, st.setValue(PcProperties.RUNNING, u.running()), Block.UPDATE_ALL);
    }

    private void clientTick() {
        if (!getBlockState().getValue(PcProperties.RUNNING)) return;
        MachineFx fx = MachineFx.of(level, worldPosition, facing());
        if (fx != null && fx.chance(1)) fx.sound(fx.at(8, 24, 8), SoundEvents.MINECART_INSIDE, 0.15f, 0.6f);
    }

    // --- вал через клетку kinetic_port ---

    @Override
    public void sampleShaft(double rpm, boolean healthy, long tick) {
        CyclicMachine u = machine();
        if (u != null) u.shaft.sample(rpm, healthy, tick);
    }

    @Override
    public double shaftPowerW() {
        CyclicMachine u = machine();
        return u == null ? 0 : u.powerW();
    }

    @Override
    public double ratedShaftW() {
        return GasMain.EXHAUSTER_RATED_W;
    }

    @Override
    public double nominalShaftRpm() {
        return CyclicMachine.NOMINAL_RPM;
    }

    // --- бак и руки ---

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return outlet.output();
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return false;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult use(Player player) {
        CyclicMachine m = machine();
        double s = m == null ? 0 : m.speedRatio();
        player.displayClientMessage(IronworksText.msg("exhauster", IronworksText.num(GasMain.EXHAUSTER_NM3S * s * 3600),
                outlet.amount()), true);
        return InteractionResult.SUCCESS;
    }

    // --- цельная модель ---

    @Override
    @Nullable
    public String wholeModelId() {
        return WholeModelHost.idOf(getBlockState());
    }

    @Override
    public Direction wholeModelFacing() {
        return facing();
    }

    /** Свет — с клетки перед фасадом вне корпуса. */
    @Override
    public BlockPos wholeModelLightPos() {
        return worldPosition.relative(facing(), 2);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("outlet", outlet.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("outlet")) outlet.load(registries, tag.getCompound("outlet"));
    }
}

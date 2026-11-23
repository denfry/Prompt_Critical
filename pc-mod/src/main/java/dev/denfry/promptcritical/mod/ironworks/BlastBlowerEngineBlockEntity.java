package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.boiler.ShaftConsumer;
import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.sim.metallurgy.BlastFurnaceUnit;
import dev.denfry.promptcritical.sim.metallurgy.CyclicMachine;
import dev.denfry.promptcritical.sim.metallurgy.FoundrySubsystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Горизонтальная воздуходувная машина реального масштаба {@code blast_blower_engine} (08 §8.5.4,
 * docs/research/blast_blower-dimensions.md): воздушный цилиндр двойного действия на кривошипе с маховиком, 7 × 3 × 3
 * блока. Тот же юнит симуляции, что у старой воздуходувки ({@link FoundrySubsystem#BLAST_BLOWER}, {@link CyclicMachine}):
 * холодное дутьё Q₀·s, s — скорость вала к номинальной. Вал Create входит в клетку (0, 1, 0) с запада
 * ({@link BlastBlowerEngineBlock}); дутьё забирает домна в пределах {@value BlastFurnaceControllerBlockEntity#LINK_RANGE}
 * блоков, как у старой воздуходувки.
 */
public final class BlastBlowerEngineBlockEntity extends DeviceBlockEntity implements FoundryDevice, ShaftConsumer, WholeModelHost, ColdBlastSource {
    /** Клапанная коробка над цилиндром, px модели: оттуда слышно дутьё. */
    private static final double[] VALVES = {98, 40, 16};

    public BlastBlowerEngineBlockEntity(BlockPos pos, BlockState state) {
        super(IronworksBlockEntities.BLAST_BLOWER_ENGINE.get(), pos, state);
    }

    /** В симуляции — та же воздуходувка, что и старая. */
    @Override
    public String deviceType() {
        return FoundrySubsystem.BLAST_BLOWER;
    }

    /** Юнит симуляции; {@code null} — на клиенте или пока устройство не привязано. */
    @Nullable
    public CyclicMachine unit() {
        SimLevelBinding b = level instanceof ServerLevel s ? SimBindings.get(s) : null;
        return Ironworks.shaftMachine(b, simId());
    }

    @Override
    public double coldNm3S() {
        CyclicMachine m = unit();
        return m == null ? 0 : BlastFurnaceUnit.BLOWER_NM3S * m.speedRatio();
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
        CyclicMachine u = unit();
        if (u == null) return;
        u.setLoaded(true);
        BlockState st = getBlockState();
        if (st.getValue(PcProperties.RUNNING) != u.running())
            level.setBlock(worldPosition, st.setValue(PcProperties.RUNNING, u.running()), Block.UPDATE_ALL);
    }

    private void clientTick() {
        if (!getBlockState().getValue(PcProperties.RUNNING)) return;
        MachineFx fx = MachineFx.of(level, worldPosition, facing());
        if (fx != null && fx.chance(1)) fx.sound(fx.at(VALVES[0], VALVES[1], VALVES[2]), SoundEvents.ELYTRA_FLYING, 0.1f, 0.5f);
    }

    // --- вал через клетку kinetic_port ---

    @Override
    public void sampleShaft(double rpm, boolean healthy, long tick) {
        CyclicMachine u = unit();
        if (u != null) u.shaft.sample(rpm, healthy, tick);
    }

    @Override
    public double shaftPowerW() {
        CyclicMachine u = unit();
        return u == null ? 0 : u.powerW();
    }

    @Override
    public double ratedShaftW() {
        return BlastFurnaceUnit.BLOWER_RATED_W;
    }

    @Override
    public double nominalShaftRpm() {
        return CyclicMachine.NOMINAL_RPM;
    }

    // --- руки ---

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

    @Override
    public InteractionResult use(Player player) {
        player.displayClientMessage(IronworksText.msg("blower", IronworksText.num(coldNm3S() * 3600)), true);
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

    /** Свет — с клетки над станиной у кривошипа: контроллер внутри фундамента. */
    @Override
    public BlockPos wholeModelLightPos() {
        return worldPosition.above(3);
    }
}

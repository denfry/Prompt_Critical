package dev.denfry.promptcritical.mod.safety;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.denfry.promptcritical.mod.acoustics.MachineAcoustics;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.powerhouse.SteamTurbineBlockEntity;
import dev.denfry.promptcritical.sim.mechanics.RotorBow;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Валоповоротное устройство {@code turbine_turning_gear} (03a §3a.7.6) вплотную к паровой турбине. Берёт вал Create с
 * соседней грани (не от турбины; от {@value RotorBow#MIN_INPUT_RPM} об/мин) и через редуктор проворачивает стоящий
 * ротор на {@value RotorBow#ROTOR_RPM} об/мин. Ведёт тепловой прогиб ротора ({@link RotorBow}, физическое время):
 * если остановленную горячую турбину не проворачивать, ротор выгибается, и после пуска дисбаланс турбины в модели
 * вибрации 04a растёт в 1 + 10·bow раз, пока ротор не выпрямится на ходу — это видно на виброметре и вибродатчике. Без валоповорота у турбины прогиба нет [допущение: устройство и ведёт учёт].
 */
public final class TurningGearBlockEntity extends DeviceBlockEntity implements FoundryDevice {
    private RotorBow state = new RotorBow();
    private double appliedFactor = 1;
    private boolean turning, running;
    @Nullable
    private BlockPos turbine;

    public TurningGearBlockEntity(BlockPos pos, BlockState state) {
        super(SafetyBlockEntities.TURBINE_TURNING_GEAR.get(), pos, state);
    }

    public RotorBow rotor() {
        return state;
    }

    public boolean turning() {
        return turning;
    }

    /** Для испытаний и сценариев: задать прогиб и время с останова. */
    public void setRotor(double bowValue, double sinceStopS) {
        state = new RotorBow(bowValue, sinceStopS);
        setChanged();
    }

    @Nullable
    private SteamTurbineBlockEntity turbine(ServerLevel level) {
        for (Direction d : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(d)) instanceof SteamTurbineBlockEntity t) {
                turbine = t.getBlockPos();
                return t;
            }
        }
        turbine = null;
        return null;
    }

    /** Вал Create с другой грани: от {@value RotorBow#MIN_INPUT_RPM} об/мин. */
    private boolean input(ServerLevel level) {
        for (Direction d : Direction.values()) {
            BlockPos p = worldPosition.relative(d);
            if (level.getBlockEntity(p) instanceof KineticBlockEntity k && !(k instanceof SteamTurbineBlockEntity)
                    && Math.abs(k.getSpeed()) >= RotorBow.MIN_INPUT_RPM) return true;
        }
        return false;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl) || (level.getGameTime() + worldPosition.hashCode()) % 20 != 0) return;
        SimLevelBinding b = SimBindings.get(sl);
        double dt = b == null ? 1 : b.world().clock().kSlow();
        SteamTurbineBlockEntity t = turbine(sl);
        running = t != null && t.outputRpm() > 0;
        turning = !running && t != null && input(sl);
        state.step(dt, running, turning);
        if (t != null) applyVibration(sl, t.getBlockPos());
        BlockState s = getBlockState();
        if (s.hasProperty(PcProperties.RUNNING) && s.getValue(PcProperties.RUNNING) != turning)
            level.setBlock(worldPosition, s.setValue(PcProperties.RUNNING, turning), Block.UPDATE_CLIENTS);
        if (level.getGameTime() % 200 == 0) setChanged();
    }

    /** Дисбаланс турбины в модели вибрации: множитель прогиба поверх собственного. */
    private void applyVibration(ServerLevel sl, BlockPos pos) {
        double f = state.vibrationFactor();
        if (Math.abs(f - appliedFactor) < 1e-3) return;
        double u = MachineAcoustics.imbalance(sl, pos);
        MachineAcoustics.setImbalance(sl, pos, u / appliedFactor * f);
        appliedFactor = f;
    }

    @Override
    public InteractionResult use(Player player) {
        if (!level.isClientSide) {
            String key = turbine == null ? "turning_no_turbine" : running ? "turning_running" : turning ? "turning_on" : state.hot() ? "turning_hot_idle" : "turning_cold";
            player.displayClientMessage(SafetyText.msg(key, Math.round(state.bow() * 100), String.format(java.util.Locale.ROOT, "%.1f", state.vibrationFactor()))
                    .withStyle(state.bow() > 0.1 ? ChatFormatting.GOLD : ChatFormatting.GREEN), false);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("bow", state.bow());
        tag.putDouble("since_stop", Math.min(state.sinceStopS(), 1e12));
        tag.putDouble("applied", appliedFactor);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("bow")) state = new RotorBow(tag.getDouble("bow"), tag.getDouble("since_stop"));
        if (tag.contains("applied")) appliedFactor = tag.getDouble("applied");
    }
}

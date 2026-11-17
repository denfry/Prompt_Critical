package dev.denfry.promptcritical.mod.powerhouse;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Редуктор газовой турбины: задней гранью — к агрегату {@link GasTurbineBlockEntity}, спереди — вал Create
 * (64/128/256 об/мин ключом). Скорость и ёмкость берёт у турбины, обратно отдаёт нагрузку сети Create.
 */
public class TurbineGearboxBlockEntity extends GeneratingKineticBlockEntity implements FoundryDevice {
    public static final int[] GEARS = {64, 128, 256};
    private static final double HYSTERESIS = 0.02;

    private int gear = 2, level4, ticks;
    private float capacityPerRpm, lastRpm;

    public TurbineGearboxBlockEntity(BlockPos pos, BlockState state) {
        super(PowerhouseBlockEntities.TURBINE_GEARBOX.get(), pos, state);
    }

    @Nullable
    private GasTurbineBlockEntity turbine() {
        if (level == null) return null;
        Direction back = getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
        return GasTurbineBlockEntity.at(level, worldPosition.relative(back));
    }

    public float outputRpm() {
        return GEARS[gear] * level4 / 4f;
    }

    @Override
    public float getGeneratedSpeed() {
        return level != null && level.isClientSide ? lastRpm : outputRpm();
    }

    @Override
    public float calculateAddedStressCapacity() {
        lastCapacityProvided = capacityPerRpm;
        return capacityPerRpm;
    }

    @Override
    public void tickDevice() {
        tick();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel)) return;
        ticks++;
        GasTurbineBlockEntity t = turbine();
        double r = t == null ? 0 : t.turbine().speedPu();
        int next = level4(r);
        if (next > level4) next = Math.max(level4, level4(r / (1 + HYSTERESIS)));
        else if (next < level4) next = Math.min(level4, level4(r * (1 + HYSTERESIS)));
        level4 = next;
        float rpm = outputRpm();
        double wPerSu = ServerConfig.W_PER_SU.get();
        float cap = (float) ((t == null ? 0 : t.capacityW()) / wPerSu / (rpm > 0 ? rpm : GEARS[gear]));
        if (rpm != lastRpm) {
            lastRpm = rpm;
            capacityPerRpm = cap;
            updateGeneratedRotation();
        } else if (Math.abs(cap - capacityPerRpm) > 0.02 * Math.max(capacityPerRpm, 0.01f) && ticks % 20 == 0) {
            capacityPerRpm = cap;
            if (hasNetwork() && getOrCreateNetwork() != null) notifyStressCapacityChange(calculateAddedStressCapacity());
        }
        double load = 0;
        float speed = Math.abs(getSpeed());
        if (speed > 0 && !isOverStressed() && capacity > 0) {
            double own = lastCapacityProvided * speed;
            load = wPerSu * Math.min(stress, capacity) * own / capacity;
        }
        if (t != null) t.setLoadW(load);
        if (ticks % 5 == 0) {
            BlockState s = getBlockState();
            boolean running = level4 > 0;
            if (s.getValue(PcProperties.RUNNING) != running) level.setBlock(worldPosition, s.setValue(PcProperties.RUNNING, running), Block.UPDATE_CLIENTS);
        }
    }

    private static int level4(double r) {
        if (r >= 0.95) return 4;
        if (r >= 0.2) return Math.max(1, Math.min(3, (int) Math.floor(4 * r)));
        return 0;
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
        if (stack.is(com.simibubi.create.AllItems.WRENCH.get())) {
            gear = (gear + 1) % GEARS.length;
            setChanged();
            player.displayClientMessage(PowerhouseText.msg("turbine.gear", GEARS[gear]), true);
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult use(Player player) {
        GasTurbineBlockEntity t = turbine();
        player.displayClientMessage(PowerhouseText.msg(t == null ? "gearbox.no_turbine" : "gearbox.linked", GEARS[gear]), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!clientPacket) tag.putInt("gear", gear);
        tag.putFloat("rpm", lastRpm);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (!clientPacket && tag.contains("gear")) gear = Math.floorMod(tag.getInt("gear"), GEARS.length);
        lastRpm = tag.getFloat("rpm");
    }
}

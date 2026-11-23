package dev.denfry.promptcritical.mod.grid;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * Привод одноосевого трекера {@code solar_tracker_drive} (15 §15.5): червячный редуктор на валу Create поворачивает
 * торсионную трубу ряда панелей восток–запад. Вращается вал — панели {@code solar_panel_tracker} в
 * {@value SolarInverterBlockEntity#RANGE} блоках следят за солнцем, до {@value #PANELS_PER_DRIVE} на привод. Мотор
 * поворота ряда — сотни ватт: {@value #RATED_W} Вт при {@value #NOMINAL_RPM} об/мин.
 */
public final class SolarTrackerDriveBlockEntity extends KineticBlockEntity implements FoundryDevice {
    public static final double RATED_W = 750, NOMINAL_RPM = 16;
    public static final int PANELS_PER_DRIVE = 100;

    public SolarTrackerDriveBlockEntity(BlockPos pos, BlockState state) {
        super(GridBlockEntities.SOLAR_TRACKER_DRIVE.get(), pos, state);
    }

    public float impact() {
        return (float) (RATED_W / (ServerConfig.W_PER_SU.get() * NOMINAL_RPM));
    }

    @Override
    public float calculateStressApplied() {
        lastStressApplied = impact();
        return lastStressApplied;
    }

    /** Вал крутится и сеть Create не перегружена. */
    public boolean tracking() {
        return getSpeed() != 0 && !isOverStressed();
    }

    @Override
    public void tickDevice() {
        tick();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel) || level.getGameTime() % 20 != 0) return;
        BlockState s = getBlockState();
        boolean on = tracking();
        if (s.hasProperty(PcProperties.RUNNING) && s.getValue(PcProperties.RUNNING) != on)
            level.setBlock(worldPosition, s.setValue(PcProperties.RUNNING, on), Block.UPDATE_ALL);
    }

    @Override
    public @Nullable IItemHandler items(@Nullable Direction side) {
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
        return InteractionResult.PASS;
    }
}

package dev.denfry.promptcritical.mod.npp;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * Компенсатор давления ВВЭР в {@value PwrReactorBlockEntity#LINK_RADIUS} блоках от реактора. Сам компенсатор считает
 * реактор ({@code Pressurizer} в {@code PressurizedWaterReactor}); блок показывает его: горит, пока включены ТЭНы,
 * парит и шипит, когда ИПУ или ПК сбрасывают пар в барботёр. Без него первый контур не держит давление — реактор не
 * взводится.
 */
public final class PressurizerBlockEntity extends DeviceBlockEntity implements FoundryDevice, dev.denfry.promptcritical.mod.client.whole.WholeModelHost {
    private boolean heaters, relief;
    private long lastReport = -100;

    public PressurizerBlockEntity(BlockPos pos, BlockState state) {
        super(NppBlockEntities.PRESSURIZER.get(), pos, state);
    }

    /** Состояние от реактора, раз в тик. */
    void report(boolean heatersOn, boolean reliefOpen) {
        heaters = heatersOn;
        relief = reliefOpen;
        lastReport = level == null ? 0 : level.getGameTime();
    }

    /** Реактор видел этот КД в последние 2 секунды. */
    public boolean connected() {
        return level != null && level.getGameTime() - lastReport < 40;
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl)) return;
        long t = level.getGameTime();
        if (!connected()) heaters = relief = false;
        if (relief && t % 4 == 0) {
            sl.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + 0.5, worldPosition.getY() + 1.2, worldPosition.getZ() + 0.5,
                    3, 0.2, 0.1, 0.2, 0.08);
            if (t % 20 == 0) sl.playSound(null, worldPosition, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1.2f, 0.5f);
        }
        if (t % 10 == 0) {
            BlockState st = getBlockState();
            if (st.hasProperty(BlockStateProperties.LIT) && st.getValue(BlockStateProperties.LIT) != heaters)
                level.setBlock(worldPosition, st.setValue(BlockStateProperties.LIT, heaters), Block.UPDATE_CLIENTS);
        }
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

    @Override
    public InteractionResult use(Player player) {
        return InteractionResult.PASS;
    }


    // --- цельная модель реального масштаба (спецификация real-scale machines) ---

    @Override
    public String wholeModelId() {
        return dev.denfry.promptcritical.mod.client.whole.WholeModelHost.idOf(getBlockState());
    }

    @Override
    public net.minecraft.core.Direction wholeModelFacing() {
        return getBlockState().getBlock() instanceof dev.denfry.promptcritical.mod.block.PcBlock pc ? pc.facing(getBlockState()) : net.minecraft.core.Direction.NORTH;
    }

    @Override
    public net.minecraft.core.BlockPos wholeModelLightPos() {
        return worldPosition.relative(wholeModelFacing(), 4).above();
    }
}

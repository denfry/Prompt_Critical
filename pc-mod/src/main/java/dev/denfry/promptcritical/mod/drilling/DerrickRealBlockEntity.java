package dev.denfry.promptcritical.mod.drilling;

import dev.denfry.promptcritical.mod.client.whole.RealScaleBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Секция буровой вышки реального масштаба {@code derrick_segment_real} (3 × 3 × 3,
 * art/blockbench/drilling/derrick_segment_real.author.js): только контроллер цельной модели. Секции ставятся одна на
 * другую над ротором; нижняя рисуется с башмаками ног и V-образными воротами, верхняя — с подкронблочной площадкой,
 * красной полосой и заградительным огнём. Пульт бурильщика считает её частью буровой, как старую секцию.
 */
public final class DerrickRealBlockEntity extends BlockEntity implements FoundryDevice, RealScaleBlockEntity {
    public DerrickRealBlockEntity(BlockPos pos, BlockState state) {
        super(DrillingBlockEntities.DERRICK_REAL.get(), pos, state);
    }

    /** Вариант цельной модели по соседям: {@code _bottom} без секции ниже, {@code _top} без секции выше. */
    public static String variant(BlockGetter level, BlockPos controller) {
        boolean below = level.getBlockState(controller.below()).is(PcBlocks.DERRICK_SEGMENT_REAL.get());
        boolean above = level.getBlockState(controller.above(3)).is(PcBlocks.DERRICK_SEGMENT_REAL.get());
        if (!below) return "derrick_segment_real_bottom";
        return above ? "derrick_segment_real" : "derrick_segment_real_top";
    }

    @Override
    public BlockPos wholeModelLightPos() {
        return worldPosition.relative(wholeModelFacing(), 2);
    }

    @Override
    public void tickDevice() {
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
}

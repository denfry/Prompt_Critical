package dev.denfry.promptcritical.mod.pa;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.block.PcProperties.PaMount;
import dev.denfry.promptcritical.mod.foundry.FoundryBlock;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * Пульт и громкоговоритель ГГС (13 §13.3.4): монтаж по грани, на которую ставят. На стену — спиной к стене, лицом
 * наружу; на пол — пульт настольный, громкоговоритель на мачте; на потолок — потолочный громкоговоритель.
 */
public class PaDeviceBlock extends FoundryBlock {
    protected PaDeviceBlock(Properties props, Spec spec, BiFunction<BlockPos, BlockState, ? extends BlockEntity> factory) {
        super(props, spec, factory);
    }

    public static BiFunction<Properties, Spec, dev.denfry.promptcritical.mod.block.PcDeviceBlock> with(
            BiFunction<BlockPos, BlockState, ? extends BlockEntity> be) {
        return (p, s) -> construct(s, () -> new PaDeviceBlock(p, s, be));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState s = super.getStateForPlacement(ctx);
        if (s == null) return null;
        Direction face = ctx.getClickedFace();
        if (face.getAxis().isHorizontal()) s = s.setValue(BlockStateProperties.HORIZONTAL_FACING, face);
        if (s.hasProperty(PcProperties.INTERCOM_MOUNT))
            s = s.setValue(PcProperties.INTERCOM_MOUNT, face == Direction.UP ? PaMount.DESK : PaMount.WALL);
        if (s.hasProperty(PcProperties.SPEAKER_MOUNT))
            s = s.setValue(PcProperties.SPEAKER_MOUNT, face == Direction.UP ? PaMount.POLE : face == Direction.DOWN ? PaMount.CEILING : PaMount.WALL);
        return s;
    }
}

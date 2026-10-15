package dev.denfry.promptcritical.mod.mine;

import dev.denfry.promptcritical.mod.block.PcDeviceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Секция механизированной крепи {@code powered_roof_support} 1×3×2 (08 §8.2.2–8.2.3): контроллер — средняя клетка,
 * перекрытие над лавой со стороны фасада, ограждение — к выработанному пространству. Распёртая ({@code extended})
 * держит кровлю ({@link dev.denfry.promptcritical.sim.mining.MineSupport#POWERED}) и не даёт себя двигать; разгружают
 * её сигналом редстоуна на любую клетку или пустой рукой, тогда поршень Create передвигает секцию за комбайном.
 */
public final class PoweredRoofSupportBlock extends PcDeviceBlock {
    private PoweredRoofSupportBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static PoweredRoofSupportBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new PoweredRoofSupportBlock(props, spec));
    }

    /** Секция распёрта: держит кровлю, двигать нельзя. */
    public static boolean set(BlockState controller) {
        return controller.getValue(BlockStateProperties.EXTENDED);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isController(state) ? new PoweredRoofSupportBlockEntity(pos, state) : null;
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return isController(state) ? (l, p, s, be) -> {
            if (be instanceof PoweredRoofSupportBlockEntity r) r.tick();
        } : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(controllerPos(state, pos)) instanceof PoweredRoofSupportBlockEntity be)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        be.toggleManual(player);
        return InteractionResult.CONSUME;
    }
}

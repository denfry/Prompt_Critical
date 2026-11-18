package dev.denfry.promptcritical.mod.mine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Свет фары у головы игрока: невидимый блок без формы и столкновений с уровнем света от потока фары. Свой блок, а не
 * ванильный {@code minecraft:light}: ставит и снимает его только {@link CapLampService}, игроцкие источники света
 * не трогаются. Блок сам проверяет раз в секунду, что он ещё чей-то, иначе исчезает — поэтому не остаётся ни после
 * выгрузки чанка, ни после падения сервера (плановый тик хранится в чанке и срабатывает после загрузки).
 */
public class CapLampLightBlock extends Block {
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 1, 15);
    static final int CHECK_TICKS = 20;

    public CapLampLightBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(LEVEL, 12));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(LEVEL);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected void onPlace(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!level.isClientSide) level.scheduleTick(pos, this, CHECK_TICKS);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (CapLampService.owns(level, pos)) level.scheduleTick(pos, this, CHECK_TICKS);
        else level.removeBlock(pos, false);
    }
}

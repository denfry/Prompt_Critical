package dev.denfry.promptcritical.mod.multiblock;

import com.mojang.serialization.MapCodec;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Клетка-часть машины реального масштаба, где у цельной модели нарисован патрубок ({@link WholePorts}): та же
 * невидимая {@link MultiblockPartBlock}, но с пустым {@link PortCellBlockEntity}, чтобы труба Create к ней
 * пристыковалась (как {@code kinetic_port} — для вала). Ставит её {@link PcBlock} вместо обычной части.
 */
public final class PortCellBlock extends MultiblockPartBlock implements EntityBlock {
    public static final MapCodec<PortCellBlock> PORT_CODEC = simpleCodec(PortCellBlock::new);

    public PortCellBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return PORT_CODEC;
    }

    public static BlockState forOffset(BlockPos offsetFromAnchor) {
        return PcBlocks.PORT_CELL.get().defaultBlockState().setValue(CELL, MultiblockPartBlock.forOffset(offsetFromAnchor).getValue(CELL));
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PortCellBlockEntity(pos, state);
    }

    /**
     * Стоит ли патрубок в клетке модели (mx, my, mz) машины {@code block} — клетке с фасадом на север, от контроллера.
     * Только машины реального масштаба с объявленными портами.
     */
    public static boolean isPortCell(Block block, int mx, int my, int mz) {
        if (!PcBlocks.isWholeModel(block)) return false;
        return WholePorts.isPortCell(BuiltInRegistries.BLOCK.getKey(block).getPath(), mx, my, mz);
    }

    /** Якорь не-контроллерного сегмента {@code state} машины — клетка патрубка (ему нужен {@link PortCellBlockEntity}). */
    public static boolean isPortAnchor(PcBlock block, BlockState state) {
        if (block.isController(state) || !PcBlocks.isWholeModel(block)) return false;
        Footprint.Segment s = block.spec().footprint().segments().get(block.segmentIndex(state));
        return isPortCell(block, s.ax(), s.ay(), s.az());
    }
}

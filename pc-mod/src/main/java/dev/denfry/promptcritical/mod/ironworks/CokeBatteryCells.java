package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.block.PcProperties.CellDoor;
import dev.denfry.promptcritical.mod.block.PcProperties.CellLevel;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Вид клетки коксовой батареи реального масштаба (08 §8.4.1, art/blockbench/coke/coke_oven/coke_oven_real.author.js):
 * ярус — по таким же клеткам снизу и сверху, ось камер — по соседям, торец с дверью камеры или анкерной колонной
 * простенка — где ряд таких же клеток кончается. Батарею собирают свободно, поэтому вид считается по соседям, а не по
 * найденной {@link CokeBatteryShape}: клетка выглядит верно и в недостроенной батарее.
 */
final class CokeBatteryCells {
    private CokeBatteryCells() {}

    /** Состояние {@code s} клетки в {@code pos} с ярусом, осью и торцом по соседям. */
    static BlockState look(BlockState s, LevelAccessor level, BlockPos pos) {
        Block self = s.getBlock();
        boolean below = level.getBlockState(pos.below()).is(self), above = level.getBlockState(pos.above()).is(self);
        CellLevel lv = !below ? CellLevel.BOTTOM : !above ? CellLevel.TOP : CellLevel.MIDDLE;
        Direction.Axis axis = axis(s, level, pos);
        Direction neg = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
        CellDoor door = !level.getBlockState(pos.relative(neg)).is(self) ? CellDoor.NEG
                : !level.getBlockState(pos.relative(neg.getOpposite())).is(self) ? CellDoor.POS : CellDoor.NONE;
        return s.setValue(PcProperties.CELL_LEVEL, lv).setValue(BlockStateProperties.HORIZONTAL_AXIS, axis)
                .setValue(PcProperties.CELL_DOOR, door);
    }

    /**
     * Ось камер: вдоль неё клетки одного рода идут рядом (камера — из камер, простенок — из простенков), поперёк
     * камеры и простенки чередуются. Одиночную клетку без соседей не трогаем.
     */
    private static Direction.Axis axis(BlockState s, LevelAccessor level, BlockPos pos) {
        Block self = s.getBlock();
        Block other = self == PcBlocks.COKE_OVEN.get() ? PcBlocks.COKE_BATTERY_WALL.get() : PcBlocks.COKE_OVEN.get();
        if (level.getBlockState(pos.east()).is(self) || level.getBlockState(pos.west()).is(self)) return Direction.Axis.X;
        if (level.getBlockState(pos.north()).is(self) || level.getBlockState(pos.south()).is(self)) return Direction.Axis.Z;
        if (level.getBlockState(pos.east()).is(other) || level.getBlockState(pos.west()).is(other)) return Direction.Axis.Z;
        if (level.getBlockState(pos.north()).is(other) || level.getBlockState(pos.south()).is(other)) return Direction.Axis.X;
        return s.getValue(BlockStateProperties.HORIZONTAL_AXIS);
    }
}

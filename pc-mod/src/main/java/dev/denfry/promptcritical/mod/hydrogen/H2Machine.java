package dev.denfry.promptcritical.mod.hydrogen;

import dev.denfry.promptcritical.mod.multiblock.WholePorts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Машина водородной станции реального масштаба: порты — из {@link WholePorts} по id цельной модели, номер порта —
 * его роль у машины (см. комментарии там).
 */
public interface H2Machine {
    String h2ModelId();

    Direction h2Facing();

    BlockPos h2Controller();

    /** Сосуд с газом этой машины; {@code null} — машина не хранит газ. */
    @Nullable
    default dev.denfry.promptcritical.sim.hydrogen.HydrogenStorage storage() {
        return null;
    }

    /** Бак за портом {@code port} для труб Create (вода, продукт); {@code null} — порт не жидкостный. */
    @Nullable
    default net.neoforged.neoforge.fluids.capability.IFluidHandler portFluids(int port) {
        return null;
    }

    /**
     * Порт газовый: трубы Create к нему подключаются (пустой бак, без него последняя труба смотрела бы раструбом в
     * воздух и линия считалась бы разорванной), но насос газ не возьмёт — он ходит по {@link GasLines}.
     */
    default boolean gasPort(int port) {
        return false;
    }

    /** Номер порта в клетке {@code cell} с мировой грани {@code side}; −1 — не порт. */
    default int portAt(BlockPos cell, Direction side) {
        BlockPos c = h2Controller();
        return WholePorts.portIndex(h2ModelId(), h2Facing(), cell.getX() - c.getX(), cell.getY() - c.getY(), cell.getZ() - c.getZ(), side);
    }

    default BlockPos portCell(int i) {
        return WholePorts.cell(h2Controller(), h2Facing(), WholePorts.of(h2ModelId()).get(i));
    }

    default Direction portFace(int i) {
        return WholePorts.worldFace(h2Facing(), WholePorts.of(h2ModelId()).get(i));
    }
}

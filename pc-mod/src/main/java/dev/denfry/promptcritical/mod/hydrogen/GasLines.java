package dev.denfry.promptcritical.mod.hydrogen;

import com.simibubi.create.content.fluids.pipes.EncasedPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.SmartFluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.valve.FluidValveBlock;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * Газовые линии водородной станции: трубы Create от порта машины до портов других машин. Газ по линии идёт сам — по
 * перепаду давления, насос не нужен (насос Create на водородной линии не пропускает газ, как и закрытый клапан).
 * Линия — до {@value #MAX_PIPES} труб.
 */
public final class GasLines {
    public static final int MAX_PIPES = 512;

    /** Конец линии: машина и её порт. */
    public record End(H2Machine machine, int port) {}

    private GasLines() {
    }

    /** Блок пропускает газ: трубы Create и открытый клапан. */
    static boolean pipe(BlockState s) {
        var b = s.getBlock();
        if (b instanceof FluidValveBlock) return !s.hasProperty(BlockStateProperties.ENABLED) || s.getValue(BlockStateProperties.ENABLED);
        return b instanceof FluidPipeBlock || b instanceof EncasedPipeBlock || b instanceof GlassFluidPipeBlock || b instanceof SmartFluidPipeBlock;
    }

    /** Машина водородной станции, частью которой является клетка {@code pos}; {@code null} — нет. */
    @Nullable
    public static H2Machine machineAt(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return null;
        if (level.getBlockEntity(pos) instanceof H2Machine m) return m;
        BlockState st = level.getBlockState(pos);
        BlockPos anchor;
        if (st.getBlock() instanceof MultiblockPartBlock) anchor = MultiblockPartBlock.anchor(st, pos);
        else if (PcBlocks.isWholeModel(st.getBlock())) anchor = pos;
        else return null;
        BlockPos c = PcBlock.structureController(level, anchor);
        return c != null && level.getBlockEntity(c) instanceof H2Machine m ? m : null;
    }

    /** Линия: концы у машин и открытые концы труб (клетки воздуха, куда труба смотрит раструбом). */
    public record Line(List<End> ends, List<BlockPos> open) {}

    /** Концы линии от порта {@code port} машины {@code from}, ближние первыми; пусто — к порту не подведена труба. */
    public static List<End> trace(Level level, H2Machine from, int port) {
        return line(level, from, port).ends();
    }

    /** Труба Create смотрит открытым концом в сторону {@code d}. */
    static boolean openTowards(BlockState s, Direction d) {
        if (!(s.getBlock() instanceof FluidPipeBlock)) return false;
        var prop = net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(d);
        return s.hasProperty(prop) && s.getValue(prop);
    }

    /** Вся линия от порта: машины на концах и открытые концы труб. */
    public static Line line(Level level, H2Machine from, int port) {
        List<End> out = new ArrayList<>();
        List<BlockPos> open = new ArrayList<>();
        BlockPos start = from.portCell(port).relative(from.portFace(port));
        if (!level.isLoaded(start) || !pipe(level.getBlockState(start))) return new Line(out, open);
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(start);
        seen.add(start);
        while (!q.isEmpty() && seen.size() <= MAX_PIPES) {
            BlockPos p = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (seen.contains(n) || !level.isLoaded(n)) continue;
                BlockState s = level.getBlockState(n);
                if (pipe(s)) {
                    seen.add(n);
                    q.add(n);
                    continue;
                }
                if (s.isAir()) {
                    if (openTowards(level.getBlockState(p), d) && !open.contains(n)) open.add(n);
                    continue;
                }
                H2Machine m = machineAt(level, n);
                if (m == null) continue;
                int i = m.portAt(n, d.getOpposite());
                if (i < 0 || m == from && i == port) continue;
                End e = new End(m, i);
                if (!out.contains(e)) out.add(e);
            }
        }
        return new Line(out, open);
    }

    /** Первый сосуд с газом на линии; {@code null} — нет. */
    @Nullable
    public static End firstStore(List<End> ends) {
        for (End e : ends) if (e.machine().storage() != null) return e;
        return null;
    }
}

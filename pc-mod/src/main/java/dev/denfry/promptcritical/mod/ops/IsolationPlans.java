package dev.denfry.promptcritical.mod.ops;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.IsolationPlanner;
import dev.denfry.promptcritical.sim.ops.EnergyKind;
import dev.denfry.promptcritical.sim.ops.LockoutRegistry;
import dev.denfry.promptcritical.sim.ops.LockoutRegistry.IsolationState;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * План изоляции объекта работ (13 §13.1.4) по графам сетей: электрическая часть — аппараты, питающие устройство сети
 * ({@link IsolationPlanner}); механическая — расцепные муфты {@code mechanical_disconnect} на валу Create объекта.
 * Пункт плана — точка и её состояние по реестру замков: в работе, изолирована, заперта (кем), проверена. Источник
 * без аппарата и накопленная энергия (конденсаторы) — отдельными предупреждениями.
 *
 * <p>Вал обходится по соседним кинетическим блокам до {@value #SHAFT_LIMIT} клеток и не проходит сквозь муфту.
 * Ремни и цепные передачи соединяют несмежные клетки — их часть вала в план не попадает, такие муфты игрок добавляет
 * сам. Гидравлической части нет: среды машин идут по трубам Create, графа {@code FluidNetwork} у них нет — заглушки
 * {@code line_blind} ставят по месту.
 */
public final class IsolationPlans {
    public static final int SHAFT_LIMIT = 256;

    public record Point(BlockPos pos, EnergyKind energy, IsolationState state, String owners) {
    }

    public record Plan(BlockPos target, List<Point> points, List<BlockPos> unswitchedSources, List<BlockPos> storedEnergy) {
        public boolean complete() {
            return !points.isEmpty() && unswitchedSources.isEmpty() && points.stream().allMatch(p -> p.state == IsolationState.VERIFIED);
        }

        public boolean empty() {
            return points.isEmpty() && unswitchedSources.isEmpty() && storedEnergy.isEmpty();
        }
    }

    private IsolationPlans() {
    }

    /** Контроллер многоблочного объекта. */
    static BlockPos controller(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof PcBlock pc) {
            BlockPos c = pc.controllerPos(s, pos);
            if (c != null) return c;
        }
        return pos;
    }

    public static Plan build(ServerLevel level, BlockPos clicked) {
        BlockPos target = controller(level, clicked);
        List<BlockPos> pointPos = new ArrayList<>(), sources = new ArrayList<>(), stored = new ArrayList<>();
        electrical(level, target, pointPos, sources, stored);
        mechanical(level, target, pointPos);
        LockoutRegistry r = Lockouts.registry(level);
        List<Point> points = new ArrayList<>();
        for (BlockPos p : pointPos) {
            Lockouts.Found f = Lockouts.find(level, p);
            if (f == null) continue;
            GridPos k = Lockouts.key(f.pos());
            boolean isolated = f.point().isolatedPosition();
            IsolationState st = r == null ? (isolated ? IsolationState.ISOLATED : IsolationState.IN_SERVICE) : r.state(k, isolated);
            points.add(new Point(f.pos(), f.point().energy(), st, Lockouts.owners(level, f.pos())));
        }
        return new Plan(target, points, sources, stored);
    }

    private static void electrical(ServerLevel level, BlockPos target, List<BlockPos> points, List<BlockPos> sources, List<BlockPos> stored) {
        SimLevelBinding b = SimBindings.get(level);
        if (b == null || !(level.getBlockEntity(target) instanceof DeviceBlockEntity d) || d.simId().isNone()) return;
        GridSubsystem grid;
        try {
            grid = b.world().subsystem(GridSubsystem.class);
        } catch (IllegalArgumentException e) {
            return;
        }
        IsolationPlanner.Plan p = IsolationPlanner.electrical(grid, d.simId());
        var topo = b.world().topology();
        for (IsolationPlanner.Feed f : p.feeds()) add(points, topo, f.device());
        for (SimId s : p.unswitchedSources()) add(sources, topo, s);
        for (SimId s : p.storedEnergy()) add(stored, topo, s);
    }

    private static void add(List<BlockPos> out, dev.denfry.promptcritical.sim.core.TopologyEditor topo, SimId id) {
        if (!topo.exists(id)) return;
        GridPos g = topo.devicePos(id);
        BlockPos p = new BlockPos(g.x(), g.y(), g.z());
        if (!out.contains(p)) out.add(p);
    }

    /** Муфты на валу объекта: обход соседних кинетических блоков, сквозь муфту не идёт. */
    private static void mechanical(ServerLevel level, BlockPos target, List<BlockPos> points) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        seen.add(target);
        queue.add(target);
        while (!queue.isEmpty() && seen.size() < SHAFT_LIMIT) {
            BlockPos p = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos n = p.relative(dir);
                if (!seen.add(n) || !level.isLoaded(n)) continue;
                var be = level.getBlockEntity(n);
                if (be instanceof MechanicalDisconnectBlockEntity) {
                    if (!points.contains(n)) points.add(n);
                } else if (be instanceof KineticBlockEntity) {
                    queue.add(n);
                }
            }
        }
    }

    // ------------------------------------------------------------------ вывод

    public static List<Component> describe(ServerLevel level, Plan plan) {
        List<Component> out = new ArrayList<>();
        out.add(LockoutText.msg("plan.header", level.getBlockState(plan.target()).getBlock().getName()).withStyle(ChatFormatting.GOLD));
        if (plan.empty()) {
            out.add(LockoutText.msg("plan.none").withStyle(ChatFormatting.GRAY));
            return out;
        }
        int todo = 0;
        for (Point p : plan.points()) {
            MutableComponent state = switch (p.state()) {
                case IN_SERVICE -> LockoutText.msg("plan.state.in_service").withStyle(ChatFormatting.RED);
                case ISOLATED -> LockoutText.msg("plan.state.isolated").withStyle(ChatFormatting.YELLOW);
                case LOCKED -> LockoutText.msg("plan.state.locked", p.owners()).withStyle(ChatFormatting.YELLOW);
                case VERIFIED -> LockoutText.msg("plan.state.verified", p.owners()).withStyle(ChatFormatting.GREEN);
            };
            if (p.state() != IsolationState.VERIFIED) todo++;
            out.add(LockoutText.msg("plan.point", level.getBlockState(p.pos()).getBlock().getName(), p.pos().toShortString(), state));
        }
        for (BlockPos s : plan.unswitchedSources())
            out.add(LockoutText.msg("plan.source", level.getBlockState(s).getBlock().getName(), s.toShortString()).withStyle(ChatFormatting.RED));
        for (BlockPos s : plan.storedEnergy())
            out.add(LockoutText.msg("plan.stored", level.getBlockState(s).getBlock().getName(), s.toShortString()).withStyle(ChatFormatting.YELLOW));
        out.add(plan.complete() ? LockoutText.msg("plan.done").withStyle(ChatFormatting.GREEN)
                : LockoutText.msg("plan.todo", todo + plan.unswitchedSources().size()).withStyle(ChatFormatting.YELLOW));
        return out;
    }

    @Nullable
    static Plan nonEmpty(ServerLevel level, BlockPos pos) {
        Plan p = build(level, pos);
        return p.empty() ? null : p;
    }
}

package dev.denfry.promptcritical.mod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.sim.consequence.BudgetStats;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.SimulationWorld;
import dev.denfry.promptcritical.sim.core.TopologyEditor;
import dev.denfry.promptcritical.sim.core.profile.SimProfiler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Служебные команды {@code /pc} (05 §5.4.7): хеш состояния, устройства, профиль тика. */
public final class PcCommands {
    private static final int LIST_LIMIT = 20;

    private PcCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pc").requires(s -> s.hasPermission(2))
                .then(Commands.literal("debug")
                        .then(Commands.literal("hash").executes(PcCommands::hash))
                        .then(Commands.literal("devices").executes(PcCommands::devices)))
                .then(Commands.literal("profile")
                        .then(Commands.literal("report").executes(PcCommands::profileReport))
                        .then(Commands.literal("reset").executes(PcCommands::profileReset))));
    }

    private static SimLevelBinding binding(CommandContext<CommandSourceStack> ctx) {
        SimLevelBinding b = SimBindings.get(ctx.getSource().getLevel());
        if (b == null) throw new IllegalStateException("no simulation in " + ctx.getSource().getLevel().dimension().location());
        return b;
    }

    private static int hash(CommandContext<CommandSourceStack> ctx) {
        SimulationWorld w = binding(ctx).world();
        long h = w.stateHash();
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.promptcritical.debug.hash",
                String.format("%016x", h), w.clock().tick()), false);
        return 1;
    }

    private static int devices(CommandContext<CommandSourceStack> ctx) {
        TopologyEditor t = binding(ctx).world().topology();
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.promptcritical.debug.devices", t.deviceCount()), false);
        for (int i = 0; i < Math.min(LIST_LIMIT, t.deviceCount()); i++) {
            SimId id = t.deviceAt(i);
            GridPos p = t.devicePos(id);
            String line = id.hex() + "  " + t.deviceType(id) + "  " + p.x() + " " + p.y() + " " + p.z() + "  " + t.deviceMode(id);
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return t.deviceCount();
    }

    private static int profileReport(CommandContext<CommandSourceStack> ctx) {
        SimLevelBinding b = binding(ctx);
        SimProfiler p = b.world().profiler();
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.promptcritical.profile.header",
                b.level().dimension().location().toString(), b.world().degradation().level().name(),
                String.format("%.3f", b.world().degradation().p95Ms())), false);
        for (int s = 0; s < p.stageCount(); s++) {
            String line = String.format("%-11s n=%-7d mean %7.1f us  p50 %7.1f  p95 %7.1f  p99 %7.1f", p.stageName(s),
                    p.samples(s), p.meanNanos(s) / 1e3, p.percentileNanos(s, 0.50) / 1e3,
                    p.percentileNanos(s, 0.95) / 1e3, p.percentileNanos(s, 0.99) / 1e3);
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        BudgetStats d = b.damage().stats();
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.promptcritical.profile.damage",
                d.blocksAppliedTotal(), d.queueLength(), d.deniedTotal()), false);
        return 1;
    }

    private static int profileReset(CommandContext<CommandSourceStack> ctx) {
        binding(ctx).world().profiler().reset();
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.promptcritical.profile.reset"), true);
        return 1;
    }
}

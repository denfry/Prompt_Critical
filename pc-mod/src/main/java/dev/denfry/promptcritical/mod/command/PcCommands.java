package dev.denfry.promptcritical.mod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.boiler.BoilerGeometry;
import dev.denfry.promptcritical.mod.boiler.FireTubeBoilerBlockEntity;
import dev.denfry.promptcritical.mod.content.HandbookManager;
import dev.denfry.promptcritical.mod.content.HandbookService;
import dev.denfry.promptcritical.sim.consequence.BudgetStats;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.SimulationWorld;
import dev.denfry.promptcritical.sim.core.TopologyEditor;
import dev.denfry.promptcritical.sim.core.profile.SimProfiler;
import dev.denfry.promptcritical.sim.mining.IgnitionSource;
import dev.denfry.promptcritical.sim.mining.MethaneField;
import dev.denfry.promptcritical.sim.mining.MineGas;
import dev.denfry.promptcritical.sim.mining.MineNetwork;
import dev.denfry.promptcritical.sim.mining.MineSubsystem;
import dev.denfry.promptcritical.sim.plant.SteamPlant;
import dev.denfry.promptcritical.mod.mine.HoistMenu;
import dev.denfry.promptcritical.mod.mine.MineHoistBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Служебные команды {@code /pc} (05 §5.4.7): хеш состояния, устройства, профиль тика, шахты, налив котла. */
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
                        .then(Commands.literal("reset").executes(PcCommands::profileReset)))
                .then(Commands.literal("mine")
                        .then(Commands.literal("info").executes(PcCommands::mineInfo))
                        .then(Commands.literal("gas").then(Commands.argument("ch4_percent", DoubleArgumentType.doubleArg(0, 100))
                                .executes(PcCommands::mineGas)))
                        .then(Commands.literal("ignite").executes(PcCommands::mineIgnite))
                        .then(Commands.literal("water").then(Commands.argument("m3", DoubleArgumentType.doubleArg(-1e6, 1e6))
                                .executes(PcCommands::mineWater))))
                .then(Commands.literal("boiler")
                        .then(Commands.literal("preset").then(Commands.argument("pressure_mpa", DoubleArgumentType.doubleArg(0.1, 6))
                                .then(Commands.argument("level_m", DoubleArgumentType.doubleArg(0, 1.6)).executes(PcCommands::boilerPreset)))))
                .then(Commands.literal("use").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> use(ctx, ItemStack.EMPTY))
                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                .executes(ctx -> use(ctx, stack(ctx, 1)))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> use(ctx, stack(ctx, IntegerArgumentType.getInteger(ctx, "count"))))))))
                .then(Commands.literal("hoist").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("button", StringArgumentType.word()).executes(PcCommands::hoist))))
                .then(Commands.literal("handbook").then(Commands.argument("entry", ResourceLocationArgument.id())
                        .executes(ctx -> handbook(ctx, 0))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> handbook(ctx, IntegerArgumentType.getInteger(ctx, "page") - 1))))));
    }

    /** Открыть справочнику игрока главу (и страницу): наладка, сервер с правилами, проверка текстов. */
    private static int handbook(CommandContext<CommandSourceStack> ctx, int page) throws CommandSyntaxException {
        var id = ResourceLocationArgument.getId(ctx, "entry");
        if (HandbookManager.data().entry(id) == null) {
            ctx.getSource().sendFailure(Component.literal("no handbook entry " + id));
            return 0;
        }
        HandbookService.open(ctx.getSource().getPlayerOrException(), id, false, page);
        return 1;
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

    /** Шахта и узел у глаз исполнителя: газы, скорость воздуха (08 §8.2). */
    private static int mineInfo(CommandContext<CommandSourceStack> ctx) {
        SimLevelBinding b = binding(ctx);
        MineSubsystem sim = b.mines().sim();
        int count = sim == null ? 0 : sim.networks().size();
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.promptcritical.mine.count", count), false);
        BlockPos eye = eye(ctx);
        MineNetwork n = b.mines().mineAt(eye);
        if (n == null) return count;
        int node = n.nodeOf(eye.asLong());
        MethaneField f = n.field();
        String line = String.format("mine %d node %d/%d: CH4 %.2f %% (roof %.2f %%), CO %.0f ppm, O2 %.1f %%, v %.2f m/s, intake %.2f m3/s",
                n.id(), node, n.topology().nodeCount() - 1, 100 * f.concentration(node, MineGas.CH4), 100 * f.roofMethane(node),
                1e6 * f.concentration(node, MineGas.CO), 100 * f.concentration(node, MineGas.O2), f.nodeVelocity(node),
                n.surfaceAirflowM3s());
        ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        ctx.getSource().sendSuccess(() -> dev.denfry.promptcritical.mod.mine.MineWorld.drainageLine(n), false);
        return count;
    }

    /** Прорыв воды в шахту у глаз исполнителя, м³ (отладка сценариев водоотлива). */
    private static int mineWater(CommandContext<CommandSourceStack> ctx) {
        MineNetwork n = binding(ctx).mines().mineAt(eye(ctx));
        if (n == null) return 0;
        n.drainage().injectM3(DoubleArgumentType.getDouble(ctx, "m3"));
        ctx.getSource().sendSuccess(() -> dev.denfry.promptcritical.mod.mine.MineWorld.drainageLine(n), false);
        return 1;
    }

    /** Задать метан в узле у глаз исполнителя (отладка сценариев). */
    private static int mineGas(CommandContext<CommandSourceStack> ctx) {
        BlockPos eye = eye(ctx);
        MineNetwork n = executorMine(ctx, eye);
        if (n == null) return 0;
        n.field().setConcentration(n.nodeOf(eye.asLong()), MineGas.CH4, DoubleArgumentType.getDouble(ctx, "ch4_percent") / 100);
        return 1;
    }

    /** Открытое пламя в узле у глаз исполнителя. */
    private static int mineIgnite(CommandContext<CommandSourceStack> ctx) {
        BlockPos eye = eye(ctx);
        MineNetwork n = executorMine(ctx, eye);
        if (n == null) return 0;
        boolean ok = n.ignite(n.nodeOf(eye.asLong()), IgnitionSource.OPEN_FLAME);
        ctx.getSource().sendSuccess(() -> Component.translatable(ok ? "commands.promptcritical.mine.ignited"
                : "commands.promptcritical.mine.not_ignited"), true);
        return ok ? 1 : 0;
    }

    /** Котёл под взглядом исполнителя: вода и пар на линии насыщения при заданном давлении (отладка, как в GameTest). */
    private static int boilerPreset(CommandContext<CommandSourceStack> ctx) {
        var e = ctx.getSource().getEntity();
        var level = ctx.getSource().getLevel();
        BlockPos c = e != null && e.pick(8, 1, false) instanceof BlockHitResult hit ? BoilerGeometry.controllerOf(level, hit.getBlockPos()) : null;
        if (c == null || !(level.getBlockEntity(c) instanceof FireTubeBoilerBlockEntity be) || be.simId().isNone()) {
            ctx.getSource().sendFailure(Component.translatable("commands.promptcritical.boiler.no_boiler"));
            return 0;
        }
        double mpa = DoubleArgumentType.getDouble(ctx, "pressure_mpa"), lv = DoubleArgumentType.getDouble(ctx, "level_m");
        binding(ctx).world().subsystem(SteamPlant.class).presetWater(be.simId(), mpa * 1e6, lv);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.promptcritical.boiler.preset", mpa, lv), true);
        return 1;
    }

    private static ItemStack stack(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        var id = ResourceLocationArgument.getId(ctx, "item");
        if (!BuiltInRegistries.ITEM.containsKey(id)) throw ResourceLocationArgument.ERROR_UNKNOWN_ID.create(id);
        return new ItemStack(BuiltInRegistries.ITEM.get(id), count);
    }

    /**
     * Правый клик по блоку от имени фейк-игрока с предметом в руке (сценарии, съёмка, проверка без GUI): розжиг,
     * вентиль, канат, форма, ведро. Фейк-игрок стоит над верхней гранью блока и после клика остаётся с пустыми руками.
     */
    private static int use(CommandContext<CommandSourceStack> ctx, ItemStack stack) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        FakePlayer fp = FakePlayerFactory.getMinecraft(level);
        fp.moveTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 0, 90);
        fp.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
        InteractionResult r = fp.gameMode.useItemOn(fp, level, stack, InteractionHand.MAIN_HAND, hit);
        fp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ctx.getSource().sendSuccess(() -> Component.literal("use " + pos.toShortString() + ": " + r), false);
        return r.consumesAction() ? 1 : 0;
    }

    /** Кнопка панели подъёмной машины без GUI: номер посадки (с 0), {@code stop}, {@code reset}, {@code slower}, {@code faster}. */
    private static int hoist(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        if (!(level.getBlockEntity(BlockPosArgument.getLoadedBlockPos(ctx, "pos")) instanceof MineHoistBlockEntity be)) {
            ctx.getSource().sendFailure(Component.literal("no mine hoist at this position"));
            return 0;
        }
        String b = StringArgumentType.getString(ctx, "button");
        int id = switch (b) {
            case "stop" -> HoistMenu.BUTTON_STOP;
            case "reset" -> HoistMenu.BUTTON_RESET;
            case "slower" -> HoistMenu.BUTTON_SLOWER;
            case "faster" -> HoistMenu.BUTTON_FASTER;
            default -> b.chars().allMatch(Character::isDigit) && !b.isEmpty() ? Integer.parseInt(b) : -1;
        };
        if (id < 0) {
            ctx.getSource().sendFailure(Component.literal("button: landing number, stop, reset, slower or faster"));
            return 0;
        }
        Player p = ctx.getSource().getEntity() instanceof Player pl ? pl : FakePlayerFactory.getMinecraft(level);
        be.onButton(p, id);
        return 1;
    }

    private static BlockPos eye(CommandContext<CommandSourceStack> ctx) {
        var e = ctx.getSource().getEntity();
        return BlockPos.containing(e != null ? e.getEyePosition() : ctx.getSource().getPosition());
    }

    private static MineNetwork executorMine(CommandContext<CommandSourceStack> ctx, BlockPos eye) {
        MineNetwork n = binding(ctx).mines().mineAt(eye);
        if (n == null) ctx.getSource().sendFailure(Component.translatable("promptcritical.mine.not_in_mine"));
        return n;
    }

    private static int profileReset(CommandContext<CommandSourceStack> ctx) {
        binding(ctx).world().profiler().reset();
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.promptcritical.profile.reset"), true);
        return 1;
    }
}

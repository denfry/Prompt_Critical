package dev.denfry.promptcritical.mod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.body.BodyService;
import dev.denfry.promptcritical.mod.body.PlayerBody;
import dev.denfry.promptcritical.mod.boiler.BoilerGeometry;
import dev.denfry.promptcritical.sim.body.BodyProfile;
import dev.denfry.promptcritical.mod.boiler.FireTubeBoilerBlockEntity;
import dev.denfry.promptcritical.mod.content.HandbookManager;
import dev.denfry.promptcritical.mod.content.HandbookService;
import dev.denfry.promptcritical.mod.panel.PanelHost;
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
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Служебные команды {@code /pc} (05 §5.4.7): хеш состояния, устройства, профиль тика, шахты, налив котла. */
public final class PcCommands {
    private static final int LIST_LIMIT = 20;
    private static final DynamicCommandExceptionType UNKNOWN_ITEM =
            new DynamicCommandExceptionType(id -> Component.literal("Unknown item: " + id));

    private PcCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // корень открыт всем, у кого есть хоть одна ветка; остальное — администратор (16 §16.11.4)
        dispatcher.register(Commands.literal("pc").requires(s -> PcPermissions.admin(s) || PcPermissions.allows(s, PcPermissions.COMMAND_ENV)
                        || PcPermissions.allows(s, PcPermissions.COMMAND_STATS) || PcPermissions.allows(s, PcPermissions.LOTO_OVERRIDE))
                .then(Commands.literal("debug").requires(PcPermissions::admin)
                        .then(Commands.literal("hash").executes(PcCommands::hash))
                        .then(Commands.literal("devices").executes(PcCommands::devices)))
                .then(Commands.literal("profile").requires(PcPermissions::admin)
                        .then(Commands.literal("report").executes(PcCommands::profileReport))
                        .then(Commands.literal("reset").executes(PcCommands::profileReset)))
                .then(Commands.literal("env").requires(s -> PcPermissions.allows(s, PcPermissions.COMMAND_ENV)).executes(PcCommands::env))
                .then(Commands.literal("mine").requires(PcPermissions::admin)
                        .then(Commands.literal("info").executes(PcCommands::mineInfo))
                        .then(Commands.literal("gas").then(Commands.argument("ch4_percent", DoubleArgumentType.doubleArg(0, 100))
                                .executes(PcCommands::mineGas)))
                        .then(Commands.literal("ignite").executes(PcCommands::mineIgnite))
                        .then(Commands.literal("water").then(Commands.argument("m3", DoubleArgumentType.doubleArg(-1e6, 1e6))
                                .executes(PcCommands::mineWater))))
                .then(Commands.literal("boiler").requires(PcPermissions::admin)
                        .then(Commands.literal("preset").then(Commands.argument("pressure_mpa", DoubleArgumentType.doubleArg(0.1, 6))
                                .then(Commands.argument("level_m", DoubleArgumentType.doubleArg(0, 1.6)).executes(PcCommands::boilerPreset)))))
                .then(Commands.literal("use").requires(PcPermissions::admin).then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> use(ctx, ItemStack.EMPTY))
                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                .executes(ctx -> use(ctx, stack(ctx, 1)))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> use(ctx, stack(ctx, IntegerArgumentType.getInteger(ctx, "count"))))))))
                .then(Commands.literal("panel").requires(PcPermissions::admin).then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("button", IntegerArgumentType.integer(0)).executes(PcCommands::panel))))
                .then(Commands.literal("hoist").requires(PcPermissions::admin).then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("button", StringArgumentType.word()).executes(PcCommands::hoist))))
                .then(Commands.literal("handbook").requires(PcPermissions::admin).then(Commands.argument("entry", ResourceLocationArgument.id())
                        .executes(ctx -> handbook(ctx, 0))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> handbook(ctx, IntegerArgumentType.getInteger(ctx, "page") - 1)))))
                .then(Commands.literal("body").requires(PcPermissions::admin).then(Commands.argument("player", EntityArgument.player())
                        .executes(PcCommands::bodyShow)
                        .then(Commands.literal("reset").executes(PcCommands::bodyReset))))
                // LOTO 13 §13.1.7: чужой замок снимает узел ops.loto_override (по умолчанию операторы), с причиной в журнал
                .then(Commands.literal("lockout")
                        .then(Commands.literal("list").requires(PcPermissions::admin).executes(PcCommands::lockoutList))
                        .then(Commands.literal("plan").requires(PcPermissions::admin).then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(PcCommands::lockoutPlan)))
                        .then(Commands.literal("cut").requires(s -> PcPermissions.allows(s, PcPermissions.LOTO_OVERRIDE)).then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("reason", StringArgumentType.greedyString()).executes(PcCommands::lockoutCut)))))
                // задачи наладчика 03d §3d.11.4: таблица лидеров по узлу command.stats; сброс — администратор
                .then(Commands.literal("challenge")
                        .then(Commands.literal("list").requires(s -> PcPermissions.allows(s, PcPermissions.COMMAND_STATS)).executes(PcCommands::challengeList))
                        .then(Commands.literal("top").requires(s -> PcPermissions.allows(s, PcPermissions.COMMAND_STATS)).then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("metric", StringArgumentType.word()).executes(PcCommands::challengeTop))))
                        .then(Commands.literal("reset").requires(PcPermissions::admin).then(Commands.argument("id", StringArgumentType.word())
                                .executes(PcCommands::challengeReset)))));
    }

    private static int challengeList(CommandContext<CommandSourceStack> ctx) {
        for (var c : dev.denfry.promptcritical.sim.automation.twin.ChallengeCatalog.ALL.values()) {
            String line = c.id() + " (" + c.industry() + ", " + c.level() + ", " + c.scenario().plant() + "): "
                    + c.metrics().stream().map(m -> m.id()).collect(java.util.stream.Collectors.joining(", "));
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return dev.denfry.promptcritical.sim.automation.twin.ChallengeCatalog.ALL.size();
    }

    private static int challengeTop(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id"), metric = StringArgumentType.getString(ctx, "metric");
        var lines = dev.denfry.promptcritical.mod.scada.TunerChallenges.top(ctx.getSource().getServer(), id, metric);
        if (lines.isEmpty()) ctx.getSource().sendSuccess(() -> Component.literal("no results for " + id + " " + metric), false);
        for (String l : lines) ctx.getSource().sendSuccess(() -> Component.literal(l), false);
        return lines.size();
    }

    private static int challengeReset(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        dev.denfry.promptcritical.mod.scada.TunerChallenges.reset(ctx.getSource().getServer(), id);
        ctx.getSource().sendSuccess(() -> Component.literal("leaderboard reset: " + id), true);
        return 1;
    }

    private static int lockoutList(CommandContext<CommandSourceStack> ctx) {
        var level = ctx.getSource().getLevel();
        var r = dev.denfry.promptcritical.mod.ops.Lockouts.registry(level);
        if (r == null || r.lockedPoints().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("no locked points"), false);
            return 0;
        }
        for (var p : r.lockedPoints()) {
            String locks = r.locks(p).stream().map(l -> l.ownerName() + (l.tag().isEmpty() ? "" : " (tag)"))
                    .collect(java.util.stream.Collectors.joining(", "));
            String line = p.x() + " " + p.y() + " " + p.z() + ": " + locks + (r.hasHasp(p) ? " [hasp]" : "")
                    + (r.verified(p) ? " [verified]" : "");
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return r.lockedPoints().size();
    }

    /** План изоляции объекта работ (13 §13.1.4). */
    private static int lockoutPlan(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        var level = ctx.getSource().getLevel();
        var plan = dev.denfry.promptcritical.mod.ops.IsolationPlans.build(level, pos);
        for (Component line : dev.denfry.promptcritical.mod.ops.IsolationPlans.describe(level, plan))
            ctx.getSource().sendSuccess(() -> line, false);
        return plan.points().size();
    }

    private static int lockoutCut(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        String reason = StringArgumentType.getString(ctx, "reason");
        var cut = dev.denfry.promptcritical.mod.ops.Lockouts.adminCut(ctx.getSource().getLevel(), pos, ctx.getSource().getTextName(), reason);
        if (cut.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("no locks at " + pos.toShortString()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("cut " + cut.size() + " lock(s) at " + pos.toShortString()), true);
        return cut.size();
    }

    /** Тело игрока: профиль и подтверждён ли он. */
    private static int bodyShow(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        PlayerBody b = BodyService.body(p);
        BodyProfile f = b.profile();
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                "%s: %s, %d cm, %d kg, %d y, confirmed=%s", p.getGameProfile().getName(), f.sex(), f.heightCm(),
                f.massKg(), f.ageYears(), b.confirmed())), false);
        return 1;
    }

    /** Сбросить выбор тела: игрок снова видит экран выбора (тело выбирается один раз на мир). */
    private static int bodyReset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        BodyService.reset(p);
        ctx.getSource().sendSuccess(() -> Component.literal("body reset: " + p.getGameProfile().getName()), true);
        return 1;
    }

    /** Погода у источника команды: воздух, ветер на 10 м, солнце, сезон. */
    private static int env(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        var s = dev.denfry.promptcritical.mod.environment.EnvironmentService.sample(level, pos, 10);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                "%s (%.2f of year): air %.1f C, %.3f kg/m3, %.0f Pa; wind 10 m %.1f m/s (gust now %.1f) from %.0f deg; sun %.0f W/m2",
                s.season(), s.yearFraction(), s.airTempC(), s.densityKgM3(), s.pressurePa(), s.meanWindMs(), s.windMs(),
                (Math.toDegrees(s.windDirRad()) % 360 + 360) % 360, s.solarWm2())), false);
        return 1;
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
        if (!BuiltInRegistries.ITEM.containsKey(id)) throw UNKNOWN_ITEM.create(id);
        return new ItemStack(BuiltInRegistries.ITEM.get(id), count);
    }

    /**
     * Правый клик по блоку от имени фейк-игрока с предметом в руке (сценарии, съёмка, проверка без GUI): розжиг,
     * канат, форма, ведро. Кнопки местных щитов (вентиль машины и т. п.) — {@code /pc panel}. Фейк-игрок стоит над верхней гранью блока и после клика остаётся с пустыми руками.
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

    /** Кнопка местного щита машины без GUI: номер кнопки раскладки (вентиль машины — 0, проворот — 1, передача — 2). */
    private static int panel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        BlockState st = level.getBlockState(pos);
        BlockPos c = st.getBlock() instanceof PcBlock b ? b.controllerPos(st, pos) : pos;
        if (!(level.getBlockEntity(c) instanceof PanelHost host)) {
            ctx.getSource().sendFailure(Component.literal("no machine panel at this position"));
            return 0;
        }
        int button = IntegerArgumentType.getInteger(ctx, "button");
        host.onPanelButton(FakePlayerFactory.getMinecraft(level), button);
        ctx.getSource().sendSuccess(() -> Component.literal("panel " + c.toShortString() + ": button " + button), false);
        return 1;
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

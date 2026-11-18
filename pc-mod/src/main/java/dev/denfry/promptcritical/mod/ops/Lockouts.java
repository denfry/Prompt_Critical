package dev.denfry.promptcritical.mod.ops;

import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.consequence.ServerMode;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.ops.LockoutRegistry;
import dev.denfry.promptcritical.sim.ops.LockoutRegistry.LockResult;
import dev.denfry.promptcritical.sim.ops.LockoutRegistry.Lockout;
import dev.denfry.promptcritical.sim.ops.OpsLogEntry;
import dev.denfry.promptcritical.sim.ops.OpsSubsystem;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import org.jetbrains.annotations.Nullable;

/**
 * LOTO в мире (13 §13.1): навеска и снятие замков, накладка, указатель напряжения, запрет ломать запертую точку,
 * срез администратором. Реестр замков — {@link LockoutRegistry} в {@link OpsSubsystem}; блок-точка ({@link
 * IsolationPoint}) сам держит своё положение и спрашивает {@link #locked} каждый тик.
 *
 * <p>Руки игрока: ПКМ замком — повесить (бирка в другой руке вешается вместе с ним); ПКМ накладкой — поставить
 * многозамковую накладку; ПКМ указателем — проверить отсутствие напряжения (на запертой точке — отметка «проверено»);
 * Shift + ПКМ пустой рукой — снять свой замок, а с пустой точки — накладку.
 */
public final class Lockouts {
    /** Без наряда замок вешается с номером 0. */
    static final long NO_PERMIT = 0;

    private Lockouts() {
    }

    /** Точка изоляции и клетка её контроллера (по ней — ключ реестра). */
    public record Found(BlockPos pos, IsolationPoint point) {
    }

    @Nullable
    public static LockoutRegistry registry(@Nullable Level level) {
        OpsSubsystem ops = OpsWorld.ops(level);
        return ops == null ? null : ops.lockouts();
    }

    public static GridPos key(BlockPos p) {
        return new GridPos(p.getX(), p.getY(), p.getZ());
    }

    /** Точка изоляции, которой принадлежит клетка (контроллер многоблочного аппарата). */
    @Nullable
    public static Found find(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof IsolationPoint p) return new Found(pos, p);
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof PcBlock pc) {
            BlockPos c = pc.controllerPos(s, pos);
            if (c != null && level.getBlockEntity(c) instanceof IsolationPoint p) return new Found(c, p);
        }
        return null;
    }

    /** На точке висит замок: блок не двигается ни по чьей команде (§13.1.3). */
    public static boolean locked(@Nullable Level level, BlockPos controller) {
        LockoutRegistry r = registry(level);
        return r != null && r.isLocked(key(controller));
    }

    /** Ники владельцев замков точки через запятую. */
    public static String owners(@Nullable Level level, BlockPos controller) {
        LockoutRegistry r = registry(level);
        return r == null ? "" : r.locks(key(controller)).stream().map(Lockout::ownerName).collect(Collectors.joining(", "));
    }

    /** «ЗАБЛОКИРОВАНО: ник» — ответ на любую попытку двинуть запертый блок. */
    public static Component lockedMessage(Level level, BlockPos controller) {
        return LockoutText.msg("locked", owners(level, controller)).withStyle(ChatFormatting.RED);
    }

    // ------------------------------------------------------------------ события

    public static void onRightClick(PlayerInteractEvent.RightClickBlock e) {
        Player p = e.getEntity();
        ItemStack stack = e.getItemStack();
        boolean tool = stack.getItem() instanceof LockoutItem;
        boolean bare = stack.isEmpty() && p.isShiftKeyDown() && e.getHand() == InteractionHand.MAIN_HAND;
        if (!tool && !bare) return;
        Level level = e.getLevel();
        Found f = find(level, e.getPos());
        if (f == null) {
            // замок или бирка по объекту работ, а не по точке изоляции — план изоляции (13 §13.1.4)
            if (!tool || ((LockoutItem) stack.getItem()).kind() == LockoutItem.Kind.VOLTAGE_TESTER) return;
            if (level instanceof net.minecraft.server.level.ServerLevel sl && p instanceof ServerPlayer sp) {
                IsolationPlans.Plan plan = IsolationPlans.nonEmpty(sl, e.getPos());
                if (plan == null) return;
                for (Component line : IsolationPlans.describe(sl, plan)) sp.displayClientMessage(line, false);
            }
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        InteractionResult r = InteractionResult.SUCCESS;
        if (!level.isClientSide && p instanceof ServerPlayer sp) {
            r = tool ? use(sp, ((LockoutItem) stack.getItem()).kind(), stack, f) : takeOff(sp, f);
            if (r == InteractionResult.PASS) return;
        } else if (bare && !tool) {
            return; // на клиенте реестра нет: решает сервер, а обычное взаимодействие не перехватываем
        }
        e.setCanceled(true);
        e.setCancellationResult(r);
    }

    private static InteractionResult use(ServerPlayer p, LockoutItem.Kind kind, ItemStack stack, Found f) {
        LockoutRegistry r = registry(p.level());
        if (r == null) return InteractionResult.PASS;
        GridPos k = key(f.pos());
        switch (kind) {
            case PADLOCK -> {
                if (f.point() instanceof LockoutBoxBlockEntity) r.fitHasp(k); // у ящика накладка своя, на шесть замков
                ItemStack off = p.getItemInHand(InteractionHand.OFF_HAND);
                boolean tagged = off.is(PcItems.LOCKOUT_TAG.get());
                String tag = tagged ? LockoutText.TAG_TEXT : "";
                LockResult res = r.apply(k, f.point().isolatedPosition(), p.getUUID(), p.getGameProfile().getName(), tag, NO_PERMIT,
                        p.level().getGameTime(), ServerConfig.OPS_MAX_LOCKS_PER_PLAYER.get());
                if (res == LockResult.OK) {
                    dev.denfry.promptcritical.mod.content.SimAchievements.direct(p, dev.denfry.promptcritical.mod.content.SimAchievements.LOCKOUT, "", 1);
                    if (!p.getAbilities().instabuild) {
                        stack.shrink(1);
                        if (tagged) off.shrink(1);
                    }
                    journal(p.level(), f, OpsLogEntry.Kind.LOCKOUT_ON, p.getGameProfile().getName() + (tagged ? " tagged" : ""));
                    p.level().playSound(null, f.pos(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 1.6f);
                    p.displayClientMessage(LockoutText.msg(tagged ? "hung_tagged" : "hung"), true);
                } else {
                    p.displayClientMessage(result(res).withStyle(ChatFormatting.RED), true);
                }
            }
            case HASP -> {
                LockResult res = r.fitHasp(k);
                if (res == LockResult.OK && !p.getAbilities().instabuild) stack.shrink(1);
                p.displayClientMessage(res == LockResult.OK ? LockoutText.msg("hasp_fitted") : result(res).withStyle(ChatFormatting.RED), true);
            }
            case VOLTAGE_TESTER -> {
                boolean none = f.point().noEnergy();
                Component reading = LockoutText.msg(none ? "tester_dead" : "tester_live", LockoutText.energy(f.point().energy()));
                if (r.isLocked(k)) {
                    LockResult res = r.verify(k, none);
                    if (res == LockResult.OK) reading = LockoutText.msg("verified", reading);
                }
                p.displayClientMessage(reading.copy().withStyle(none ? ChatFormatting.GREEN : ChatFormatting.RED), true);
            }
            case TAG -> p.displayClientMessage(LockoutText.msg("tag_only").withStyle(ChatFormatting.GOLD), true);
        }
        return InteractionResult.SUCCESS;
    }

    /** Shift + ПКМ пустой рукой: снять свой замок (с биркой) или пустую накладку. */
    private static InteractionResult takeOff(ServerPlayer p, Found f) {
        LockoutRegistry r = registry(p.level());
        if (r == null) return InteractionResult.PASS;
        GridPos k = key(f.pos());
        Lockout mine = null;
        for (Lockout l : r.locks(k)) if (l.owner().equals(p.getUUID())) mine = l;
        if (mine != null) {
            r.remove(k, p.getUUID());
            give(p, new ItemStack(PcItems.LOCKOUT_PADLOCK.get()));
            if (!mine.tag().isEmpty()) give(p, new ItemStack(PcItems.LOCKOUT_TAG.get()));
            journal(p.level(), f, OpsLogEntry.Kind.LOCKOUT_OFF, p.getGameProfile().getName());
            p.level().playSound(null, f.pos(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6f, 1.6f);
            p.displayClientMessage(LockoutText.msg(r.isLocked(k) ? "removed_others" : "removed", owners(p.level(), f.pos())), true);
            return InteractionResult.SUCCESS;
        }
        if (r.isLocked(k)) {
            p.displayClientMessage(result(LockResult.NOT_YOURS).withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        if (r.hasHasp(k) && r.removeHasp(k) == LockResult.OK) {
            give(p, new ItemStack(PcItems.LOCKOUT_HASP.get()));
            p.displayClientMessage(LockoutText.msg("hasp_removed"), true);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private static void give(Player p, ItemStack s) {
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    /**
     * Сломать запертую точку нельзя (§13.1.7); в режиме {@code REALISTIC} — можно, с сигналом «ВМЕШАТЕЛЬСТВО В
     * ИЗОЛЯЦИЮ» и записью в журнал. Сломанная точка уносит свои замки и накладку.
     */
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel sl)) return;
        Found f = find(sl, e.getPos());
        if (f == null) return;
        LockoutRegistry r = registry(sl);
        if (r == null) return;
        GridPos k = key(f.pos());
        if (r.isLocked(k)) {
            if (ServerConfig.SERVER_MODE.get() != ServerMode.REALISTIC) {
                e.setCanceled(true);
                e.getPlayer().displayClientMessage(lockedMessage(sl, f.pos()), true);
                return;
            }
            String who = e.getPlayer().getGameProfile().getName();
            journal(sl, f, OpsLogEntry.Kind.UNAUTHORIZED_ACCESS, who + " broke a locked point of " + owners(sl, f.pos()));
            Component alarm = LockoutText.msg("tamper", who, f.pos().toShortString()).withStyle(ChatFormatting.DARK_RED);
            for (ServerPlayer p : sl.players()) p.displayClientMessage(alarm, false);
        }
        r.forget(k);
    }

    /** Взрыв уносит точки вместе с замками. */
    public static void onExplosion(ExplosionEvent.Detonate e) {
        LockoutRegistry r = registry(e.getLevel());
        if (r == null) return;
        for (BlockPos p : e.getAffectedBlocks()) {
            Found f = find(e.getLevel(), p);
            if (f != null) r.forget(key(f.pos()));
        }
    }

    /**
     * Срез замков администратором (§13.1.7, T-13-02): журнал CRITICAL, объявление на сервере.
     *
     * @return срезанные замки
     */
    public static List<Lockout> adminCut(ServerLevel sl, BlockPos pos, String admin, String reason) {
        Found f = find(sl, pos);
        LockoutRegistry r = registry(sl);
        if (f == null || r == null) return List.of();
        List<Lockout> cut = r.adminCut(key(f.pos()));
        if (cut.isEmpty()) return cut;
        String owners = cut.stream().map(Lockout::ownerName).collect(Collectors.joining(", "));
        journal(sl, f, OpsLogEntry.Kind.LOCKOUT_ADMIN_CUT, admin + " cut " + owners + ": " + reason);
        Component c = LockoutText.msg("admin_cut", admin, owners, f.pos().toShortString(), reason).withStyle(ChatFormatting.DARK_RED);
        for (ServerPlayer p : sl.players()) p.displayClientMessage(c, false);
        return cut;
    }

    private static void journal(Level level, Found f, OpsLogEntry.Kind kind, String detail) {
        OpsSubsystem ops = OpsWorld.ops(level);
        if (ops != null) ops.journal(f.point().simId(), kind, f.pos().toShortString() + " " + detail);
    }

    static net.minecraft.network.chat.MutableComponent result(LockResult r) {
        return LockoutText.msg("result." + r.name().toLowerCase(Locale.ROOT));
    }
}

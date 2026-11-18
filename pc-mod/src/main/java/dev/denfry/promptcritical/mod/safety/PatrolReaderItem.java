package dev.denfry.promptcritical.mod.safety;

import dev.denfry.promptcritical.sim.ops.PatrolRound;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Считыватель обходчика {@code patrol_reader} (13 §13.3.1). Маршрут живёт в считывателе: Shift + ПКМ по метке —
 * добавить её в конец маршрута; ПКМ по метке — отметить посещение ({@link PatrolRound}: круг по порядку) и получить
 * карточку пункта ({@link Patrols}); Shift + ПКМ в воздух — состояние маршрута.
 */
public final class PatrolReaderItem extends Item {
    public PatrolReaderItem(Properties props) {
        super(props.stacksTo(1));
    }

    static SafetyComponents.Route route(ItemStack s) {
        SafetyComponents.Route r = s.get(SafetyComponents.PATROL_ROUTE.get());
        return r == null ? SafetyComponents.Route.EMPTY : r;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!(level.getBlockEntity(ctx.getClickedPos()) instanceof PatrolCheckpointBlockEntity cp)) return InteractionResult.PASS;
        if (level.isClientSide || !(ctx.getPlayer() instanceof ServerPlayer p)) return InteractionResult.SUCCESS;
        ItemStack stack = ctx.getItemInHand();
        if (p.isSecondaryUseActive()) program(p, stack, cp.getBlockPos());
        else visit(p, stack, cp);
        return InteractionResult.SUCCESS;
    }

    /** Добавить метку в маршрут. */
    public static boolean program(ServerPlayer p, ItemStack stack, BlockPos pos) {
        SafetyComponents.Route r = route(stack);
        if (r.points().contains(pos.asLong())) {
            p.displayClientMessage(SafetyText.msg("patrol_already", r.points().indexOf(pos.asLong()) + 1), true);
            return false;
        }
        List<Long> pts = new ArrayList<>(r.points());
        pts.add(pos.asLong());
        stack.set(SafetyComponents.PATROL_ROUTE.get(), new SafetyComponents.Route(pts, 0, -1, -1));
        p.displayClientMessage(SafetyText.msg("patrol_added", pts.size()).withStyle(ChatFormatting.GREEN), true);
        return true;
    }

    /**
     * Отметка на метке: круг маршрута, посещение метки, карточка.
     *
     * @return результат по маршруту
     */
    public static PatrolRound.Result visit(ServerPlayer p, ItemStack stack, PatrolCheckpointBlockEntity cp) {
        ServerLevel level = p.serverLevel();
        SafetyComponents.Route r = route(stack);
        int index = r.points().indexOf(cp.getBlockPos().asLong());
        long now = level.getGameTime();
        PatrolRound round = new PatrolRound(r.points().size(), r.next(), r.start(), r.last());
        PatrolRound.Result res = round.visit(index, now);
        if (res != PatrolRound.Result.OUT_OF_ORDER && res != PatrolRound.Result.UNKNOWN) cp.visited(now);
        stack.set(SafetyComponents.PATROL_ROUTE.get(), new SafetyComponents.Route(r.points(), round.next(), round.roundStart(), round.lastComplete()));
        level.playSound(null, cp.getBlockPos(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.6f,
                res == PatrolRound.Result.OUT_OF_ORDER || res == PatrolRound.Result.UNKNOWN ? 0.5f : 1.5f);
        switch (res) {
            case UNKNOWN -> p.displayClientMessage(SafetyText.msg("patrol_unknown").withStyle(ChatFormatting.GOLD), false);
            case OUT_OF_ORDER -> p.displayClientMessage(SafetyText.msg("patrol_out_of_order", round.next() + 1).withStyle(ChatFormatting.GOLD), false);
            case COMPLETE -> p.displayClientMessage(SafetyText.msg("patrol_complete").withStyle(ChatFormatting.GREEN), false);
            default -> p.displayClientMessage(SafetyText.msg("patrol_point", index + 1, r.points().size()), false);
        }
        if (res != PatrolRound.Result.OUT_OF_ORDER && res != PatrolRound.Result.UNKNOWN)
            for (Component line : Patrols.lines(Patrols.card(level, cp.getBlockPos()))) p.displayClientMessage(line, false);
        return res;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive()) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) {
            SafetyComponents.Route r = route(stack);
            long left = r.last() < 0 ? -1 : Math.max(0, Math.round(PatrolRound.PERIOD_S - (level.getGameTime() - r.last()) / PatrolRound.TICKS_PER_S));
            player.displayClientMessage(left < 0 ? SafetyText.msg("patrol_status_new", r.points().size())
                    : SafetyText.msg("patrol_status", r.points().size(), r.next() + 1, left / 60), false);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.promptcritical.patrol_reader.hint").withStyle(ChatFormatting.GRAY));
        SafetyComponents.Route r = route(stack);
        if (!r.points().isEmpty())
            tooltip.add(Component.translatable("item.promptcritical.patrol_reader.route", r.points().size(), r.next() + 1).withStyle(ChatFormatting.AQUA));
    }
}

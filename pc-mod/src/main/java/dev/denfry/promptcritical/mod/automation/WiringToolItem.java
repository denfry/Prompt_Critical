package dev.denfry.promptcritical.mod.automation;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock;
import dev.denfry.promptcritical.sim.automation.AutomationKind;
import dev.denfry.promptcritical.sim.automation.TerminalType;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * {@code wiring_tool} (03c §3c.2, 03d §3d.2.1): виртуальный провод от клеммы к клемме — клик по клемме блока, затем
 * по второй (не дальше 64 м по манхэттенской метрике, только клеммы одного типа). Shift + клик по клемме — снять её
 * провода; Shift + ПКМ в воздух — отменить начатый провод. Клемма — ближайшая к точке клика (клиент подсвечивает её).
 */
public class WiringToolItem extends Item {
    /** Наибольшее расстояние от точки клика до клеммы, блока. */
    public static final double PICK_RADIUS = 0.22;

    public WiringToolItem(Properties props) {
        super(props);
    }

    /** Подпись клеммы: обозначение и тип. */
    public static Component describe(AutomationKind kind, int t) {
        AutomationKind.Terminal term = kind.terminal(t);
        return AutomationText.msg(AutomationText.TERMINAL, term.label(), AutomationText.msg(typeKey(term.type())));
    }

    private static String typeKey(TerminalType t) {
        return switch (t) {
            case DISCRETE -> AutomationText.TYPE_DISCRETE;
            case ANALOG_CURRENT -> AutomationText.TYPE_CURRENT;
            case ANALOG_VOLTAGE -> AutomationText.TYPE_VOLTAGE;
        };
    }

    /** Блок с клеммами, клеткой которого является {@code pos} (ячейка КРУ — две клетки). */
    @Nullable
    public static WiredDevice deviceAt(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        BlockPos c = pos;
        if (s.getBlock() instanceof MultiblockPartBlock) {
            BlockPos ctl = PcBlock.structureController(level, MultiblockPartBlock.anchor(s, pos));
            if (ctl != null) c = ctl;
        } else if (s.getBlock() instanceof PcBlock pc) {
            c = pc.controllerPos(s, pos);
        }
        return level.getBlockEntity(c) instanceof WiredDevice d ? d : null;
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext ctx) {
        Level level = ctx.getLevel();
        WiredDevice here = deviceAt(level, ctx.getClickedPos());
        if (here == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        Player player = ctx.getPlayer();
        int t = here.terminalAt(ctx.getClickLocation(), PICK_RADIUS);
        if (t < 0) return say(player, AutomationText.msg(AutomationText.NO_TERMINAL));
        SimLevelBinding b = SimBindings.get((ServerLevel) level);
        if (b == null) return InteractionResult.FAIL;
        if (player != null && player.isShiftKeyDown()) {
            int n = b.automation().disconnect(here, t);
            return say(player, AutomationText.msg(AutomationText.WIRE_REMOVED, describe(here.wiredKind(), t), n));
        }
        AutomationComponents.TerminalLink start = stack.get(AutomationComponents.TERMINAL_LINK.get());
        WiredDevice there = start == null ? null : level.getBlockEntity(start.pos()) instanceof WiredDevice d ? d : null;
        if (there == null || start.terminal() >= there.wiredKind().terminalCount()) {
            stack.set(AutomationComponents.TERMINAL_LINK.get(), new AutomationComponents.TerminalLink(here.wiredPos().immutable(), t));
            return say(player, AutomationText.msg(AutomationText.WIRE_FROM, describe(here.wiredKind(), t)));
        }
        int ts = start.terminal();
        if (there == here && ts == t) return say(player, AutomationText.msg(AutomationText.WIRE_SAME));
        TerminalType a = there.wiredKind().terminal(ts).type(), c = here.wiredKind().terminal(t).type();
        if (a != c) return say(player, AutomationText.msg(AutomationText.WIRE_TYPE, describe(there.wiredKind(), ts),
                describe(here.wiredKind(), t)));
        int len = there.wiredPos().distManhattan(here.wiredPos());
        if (len > AutomationWorld.MAX_WIRE_M)
            return say(player, AutomationText.msg(AutomationText.WIRE_LONG, len, AutomationWorld.MAX_WIRE_M));
        if (!b.automation().connect(there, ts, here, t)) return say(player, AutomationText.msg(AutomationText.WIRE_EXISTS));
        stack.remove(AutomationComponents.TERMINAL_LINK.get());
        level.playSound(null, here.wiredPos(), SoundEvents.TRIPWIRE_ATTACH, SoundSource.BLOCKS, 0.6f, 1.4f);
        return say(player, AutomationText.msg(AutomationText.WIRE_DONE, describe(there.wiredKind(), ts), describe(here.wiredKind(), t), len));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown() || !stack.has(AutomationComponents.TERMINAL_LINK.get())) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) {
            stack.remove(AutomationComponents.TERMINAL_LINK.get());
            player.displayClientMessage(AutomationText.msg(AutomationText.WIRE_CANCEL), true);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(AutomationComponents.TERMINAL_LINK.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        AutomationComponents.TerminalLink l = stack.get(AutomationComponents.TERMINAL_LINK.get());
        if (l != null) tooltip.add(Component.literal(l.pos().toShortString() + " #" + l.terminal()).withStyle(ChatFormatting.GRAY));
    }

    private static InteractionResult say(@Nullable Player player, Component msg) {
        if (player != null) player.displayClientMessage(msg, true);
        return InteractionResult.SUCCESS;
    }
}

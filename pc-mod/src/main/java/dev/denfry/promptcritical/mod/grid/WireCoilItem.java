package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.registry.PcItems;
import java.util.List;
import java.util.Locale;
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
import net.minecraft.world.level.block.Block;

/**
 * Катушка провода (07 §7.4.2): клик по коннектору начинает пролёт, клик по второму — натягивает его (одна катушка на
 * пролёт). {@code wire_coil_lv} — изолированный медный 0,4 кВ (касание блоков не замыкает), {@code overhead_conductor}
 * — голый медный М-35 для 0,4 и 6–10 кВ. Shift + ПКМ в воздух — отменить начатый пролёт.
 */
public class WireCoilItem extends Item {
    /** Провод каталога §7.4.1 на катушке эпохи I1. */
    public static final String CONDUCTOR = "m35";
    private final boolean insulated;

    public WireCoilItem(Properties props, boolean insulated) {
        super(props);
        this.insulated = insulated;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!(level.getBlockEntity(ctx.getClickedPos()) instanceof ConnectorBlockEntity here)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();
        if (insulated && here.mediumVoltage()) return say(player, GridText.WIRE_KIND);
        BlockPos start = stack.get(GridComponents.WIRE_LINK.get());
        if (start == null || !(level.getBlockEntity(start) instanceof ConnectorBlockEntity there)) {
            stack.set(GridComponents.WIRE_LINK.get(), here.getBlockPos().immutable());
            return say(player, GridText.WIRE_START);
        }
        if (start.equals(here.getBlockPos())) return say(player, GridText.WIRE_SAME);
        if (there.mediumVoltage() != here.mediumVoltage()) return say(player, GridText.WIRE_CLASS);
        if (here.linkedTo(start)) return say(player, GridText.WIRE_EXISTS);
        double len = Math.sqrt(start.distSqr(here.getBlockPos()));
        int max = here.maxSpan();
        if (len > max) {
            if (player != null) player.displayClientMessage(GridText.msg(GridText.WIRE_TOO_LONG, fmt(len), max), true);
            return InteractionResult.FAIL;
        }
        SimLevelBinding b = SimBindings.get((ServerLevel) level);
        if (b == null) return InteractionResult.FAIL;
        b.grids().addSpan(there, here, CONDUCTOR, insulated);
        stack.remove(GridComponents.WIRE_LINK.get());
        if (player == null || !player.getAbilities().instabuild) stack.shrink(1);
        level.playSound(null, here.getBlockPos(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1, 1.2f);
        if (player != null) player.displayClientMessage(GridText.msg(GridText.WIRE_DONE, fmt(len)), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown() || !stack.has(GridComponents.WIRE_LINK.get())) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) {
            stack.remove(GridComponents.WIRE_LINK.get());
            player.displayClientMessage(GridText.msg(GridText.WIRE_CANCEL), true);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(GridComponents.WIRE_LINK.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        BlockPos p = stack.get(GridComponents.WIRE_LINK.get());
        if (p != null) tooltip.add(Component.literal(p.toShortString()).withStyle(ChatFormatting.GRAY));
    }

    private static InteractionResult say(Player player, String key) {
        if (player != null) player.displayClientMessage(GridText.msg(key), true);
        return InteractionResult.SUCCESS;
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /** Провод снятого пролёта выпадает катушкой. */
    static void drop(Level level, BlockPos pos, boolean insulated, String conductor) {
        Item item = insulated ? PcItems.WIRE_COIL_LV.get() : PcItems.OVERHEAD_CONDUCTOR.get();
        Block.popResource(level, pos, new ItemStack(item));
    }
}

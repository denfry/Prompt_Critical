package dev.denfry.promptcritical.mod.acoustics;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Запчасти и инструмент обслуживания вращающихся машин (04a §4a.7.5): ПКМ по машине Create и удерживать —
 * работы идут {@link Kind#seconds} реального времени. Комплект подшипника и центровка — на стоящем валу, балансировка
 * пробным грузом — на ходу. Комплект и грузы расходуются, приспособление для центровки изнашивается.
 */
public final class MachineServiceItem extends Item {
    public enum Kind {
        BEARING_KIT(MachineAcoustics.Service.BEARING, 60, true),
        BALANCING_WEIGHTS(MachineAcoustics.Service.BALANCE, 10, true),
        ALIGNMENT_TOOL(MachineAcoustics.Service.ALIGN, 30, false);

        public final MachineAcoustics.Service service;
        public final int seconds;
        public final boolean consumed;

        Kind(MachineAcoustics.Service service, int seconds, boolean consumed) {
            this.service = service;
            this.seconds = seconds;
            this.consumed = consumed;
        }
    }

    /**
     * Машина, над которой идут работы (свой ключ у клиента и у сервера). В одиночной игре клиент и встроенный сервер —
     * разные потоки одной JVM, поэтому карта синхронизирована.
     */
    private static final Map<Player, BlockPos> TARGET = java.util.Collections.synchronizedMap(new WeakHashMap<>());
    static final double REACH_M = 6;

    private final Kind kind;

    public MachineServiceItem(Properties props, Kind kind) {
        super(kind.consumed ? props.stacksTo(16) : props.durability(64));
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player p = ctx.getPlayer();
        BlockPos pos = ctx.getClickedPos();
        if (p == null || !(ctx.getLevel().getBlockEntity(pos) instanceof KineticBlockEntity)) return InteractionResult.PASS;
        if (!MachineAcoustics.canService(ctx.getLevel(), pos, kind.service)) {
            if (!ctx.getLevel().isClientSide)
                p.displayClientMessage(Component.translatable(kind.service.running
                        ? "message.promptcritical.machine_service.needs_running" : "message.promptcritical.machine_service.needs_stopped")
                        .withStyle(ChatFormatting.GOLD), true);
            return InteractionResult.FAIL;
        }
        TARGET.put(p, pos.immutable());
        p.startUsingItem(ctx.getHand());
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return kind.seconds * 20;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BRUSH;
    }

    private static boolean stillValid(Player p, Kind kind) {
        BlockPos pos = TARGET.get(p);
        return pos != null && p.position().distanceTo(pos.getCenter()) <= REACH_M
                && MachineAcoustics.canService(p.level(), pos, kind.service);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!level.isClientSide && entity instanceof Player p && remaining % 20 == 0 && !stillValid(p, kind)) p.releaseUsingItem();
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof Player p)) return stack;
        BlockPos pos = TARGET.remove(p);
        if (!(level instanceof ServerLevel sl) || pos == null) return stack;
        if (!MachineAcoustics.service(sl, pos, kind.service)) return stack;
        p.displayClientMessage(Component.translatable("message.promptcritical.machine_service.done." + kind.name().toLowerCase(java.util.Locale.ROOT))
                .withStyle(ChatFormatting.GREEN), true);
        if (p.getAbilities().instabuild) return stack;
        if (kind.consumed) stack.shrink(1);
        else stack.hurtAndBreak(1, p, EquipmentSlot.MAINHAND);
        return stack;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (entity instanceof Player p) TARGET.remove(p);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.promptcritical." + kind.name().toLowerCase(java.util.Locale.ROOT) + ".hint", kind.seconds)
                .withStyle(ChatFormatting.GRAY));
    }
}

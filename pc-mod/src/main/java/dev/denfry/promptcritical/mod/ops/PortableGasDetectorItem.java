package dev.denfry.promptcritical.mod.ops;

import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.ops.PermitRegistry;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Переносной газоанализатор {@code portable_gas_detector} (13 §13.2.3; модель — четыре сенсора: термокаталитический
 * НКПР, электрохимические O₂, H₂S, CO, 03d §3d.8.1). ПКМ с удержанием {@value #T90_S} с (время установления показаний
 * T90) — замер в точке игрока, он заносится в наряды, в объёме которых стоит игрок. Shift + ПКМ с поверочной смесью
 * {@code calibration_gas} в другой руке — проверка газом (bump test); без неё не старше 24 ч замер при
 * {@code ENFORCED} не принимается.
 */
public final class PortableGasDetectorItem extends Item {
    /** T90 газоанализатора, с (§13.2.3). */
    public static final int T90_S = 30;

    public PortableGasDetectorItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (player.isSecondaryUseActive() && other.is(PcItems.CALIBRATION_GAS.get())) {
            if (!level.isClientSide) {
                bumpTest(stack, level);
                if (!player.getAbilities().instabuild) other.shrink(1);
                level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4f, 1.6f);
                player.displayClientMessage(PermitText.msg("gas.bumped").withStyle(ChatFormatting.GREEN), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!level.isClientSide) player.displayClientMessage(PermitText.msg("gas.hold", T90_S), true);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    /** Проверка газом: отметка времени мира на приборе. */
    public static void bumpTest(ItemStack detector, Level level) {
        detector.set(Permits.BUMP_TICK.get(), level.getGameTime());
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return T90_S * 20;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPYGLASS;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer p) {
            Permits.measure(p, stack, Permits.enforcement());
            level.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.5f, 1.8f);
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> out, TooltipFlag flag) {
        Long bump = stack.get(Permits.BUMP_TICK.get());
        out.add(Component.translatable(bump == null ? "item.promptcritical.portable_gas_detector.unbumped"
                : "item.promptcritical.portable_gas_detector.bumped").withStyle(ChatFormatting.GRAY));
        out.add(Component.translatable("item.promptcritical.portable_gas_detector.hint", T90_S,
                (int) (PermitRegistry.BUMP_TEST_MAX_AGE_S / 3600)).withStyle(ChatFormatting.DARK_GRAY));
    }
}

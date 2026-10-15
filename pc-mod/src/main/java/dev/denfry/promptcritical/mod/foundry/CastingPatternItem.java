package dev.denfry.promptcritical.mod.foundry;

import dev.denfry.promptcritical.sim.metallurgy.CastingKind;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Деревянная модель отливки {@code casting_pattern} (08 §8.9): клик — следующая модель ({@link CastingKind}),
 * клик с Shift — модель с прибылью или без (без прибыли отливка выйдет с усадочной раковиной).
 */
public final class CastingPatternItem extends Item {
    public CastingPatternItem(Properties props) {
        super(props);
    }

    public static FoundryComponents.Pattern pattern(ItemStack stack) {
        return stack.getOrDefault(FoundryComponents.CASTING_PATTERN.get(), FoundryComponents.Pattern.DEFAULT);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        FoundryComponents.Pattern p = pattern(stack);
        FoundryComponents.Pattern next;
        if (player.isShiftKeyDown()) {
            next = new FoundryComponents.Pattern(p.kind(), !p.riser());
        } else {
            CastingKind[] kinds = CastingKind.values();
            next = new FoundryComponents.Pattern(kinds[(p.kind().ordinal() + 1) % kinds.length], p.riser());
        }
        stack.set(FoundryComponents.CASTING_PATTERN.get(), next);
        if (!level.isClientSide) player.displayClientMessage(FoundryText.pattern(next), true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack)).append(" — ").append(FoundryText.kind(pattern(stack).kind()));
    }
}

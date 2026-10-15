package dev.denfry.promptcritical.mod.content;

import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Инструкция по эксплуатации установки (11 §11.1.2): глава справочника {@code procedure_ref} в виде документа. Выдаётся
 * при постройке установки, кладётся на {@code document_lectern} у пульта.
 */
public class OperatingProcedureItem extends Item {
    public OperatingProcedureItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ResourceLocation ref = stack.get(PcDataComponents.PROCEDURE_REF.get());
        if (ref == null) return InteractionResultHolder.pass(stack);
        if (player instanceof ServerPlayer sp) HandbookService.open(sp, ref, true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}

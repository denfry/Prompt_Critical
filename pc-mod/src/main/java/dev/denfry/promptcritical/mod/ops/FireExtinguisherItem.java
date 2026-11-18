package dev.denfry.promptcritical.mod.ops;

import dev.denfry.promptcritical.sim.ops.FireSuppression;
import dev.denfry.promptcritical.sim.ops.FireSuppression.Agent;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Огнетушитель (13 §13.5.3): вода, пена, порошок ABCE, CO₂, порошок D. ПКМ по очагу или рядом — тушит очаги в радиусе
 * 1 блока (1–4 м²) по таблице классов §13.5.1; не тот класс — не тушит или опасен. Десять пусков.
 */
public final class FireExtinguisherItem extends Item {
    private final Agent agent;

    public FireExtinguisherItem(Properties p, Agent agent) {
        super(p.durability(10));
        this.agent = agent;
    }

    public Agent agent() {
        return agent;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        Player p = ctx.getPlayer();
        BlockPos at = ctx.getClickedPos().relative(ctx.getClickedFace());
        if (FireService.fires(sl, at, 1).isEmpty()) return InteractionResult.PASS;
        sl.sendParticles(agent == Agent.WATER ? net.minecraft.core.particles.ParticleTypes.SPLASH
                : net.minecraft.core.particles.ParticleTypes.POOF, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 25, 0.6, 0.4, 0.6, 0.02);
        int out = FireService.apply(sl, at, 1, agent, p);
        if (out == 0 && p != null && FireService.classAt(sl, at) != FireSuppression.FireClass.D)
            p.displayClientMessage(OpsFireText.msg("ineffective").withStyle(ChatFormatting.GOLD), true);
        if (p != null) ctx.getItemInHand().hurtAndBreak(1, p, EquipmentSlot.MAINHAND);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(OpsFireText.msg("tip." + agent.name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.GRAY));
    }
}

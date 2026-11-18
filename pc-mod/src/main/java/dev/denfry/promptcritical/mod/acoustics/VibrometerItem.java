package dev.denfry.promptcritical.mod.acoustics;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.denfry.promptcritical.sim.acoustics.VibrationSpectrum;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Ручной виброметр-анализатор (04a §4a.8.1; по классу — СД-21, Fluke 810): ПКМ по любой вращающейся машине Create —
 * экран прибора с общим уровнем, зоной ISO 10816 для класса машины, спектром по диагностическим полосам, огибающей
 * ВЧ, температурой корпуса подшипника (ИК-пирометр) и уровнем шума в точке замера (шумомер).
 */
public final class VibrometerItem extends Item {
    public VibrometerItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel().getBlockEntity(ctx.getClickedPos()) instanceof KineticBlockEntity)) return InteractionResult.PASS;
        if (!(ctx.getLevel() instanceof ServerLevel level) || !(ctx.getPlayer() instanceof ServerPlayer player))
            return InteractionResult.SUCCESS;
        MachineAcoustics.Probe probe = MachineAcoustics.probe(level, ctx.getClickedPos());
        VibrationSpectrum s = MachineAcoustics.spectrum(level, ctx.getClickedPos());
        if (probe == null || s == null) return InteractionResult.PASS;
        Vec3 ear = player.getEyePosition();
        double noise = NoiseService.levelAt(level, ear);
        PacketDistributor.sendToPlayer(player, VibrationReading.measure(s, probe.powerW(), noise, level.random));
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.promptcritical.vibrometer.hint").withStyle(ChatFormatting.GRAY));
    }
}

package dev.denfry.promptcritical.mod.production;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Газлифтный клапан (09 §9.6.4): глубина установки и давление открытия p_tro — компонент предмета, задаются при
 * установке. Правый клик в воздух — глубина +100 м (100–2000 м по кругу), с Shift — p_tro +0,5 МПа (3–12 МПа по кругу).
 * Клапан ставят на устье ({@link WellheadBlockEntity}); с Shift по устью — вынуть все клапаны (подъём НКТ).
 */
public final class GasLiftValveItem extends Item {
    public static final int MIN_DEPTH = 100, MAX_DEPTH = 2000, DEPTH_STEP = 100, MIN_PTRO_KPA = 3000, MAX_PTRO_KPA = 12000, PTRO_STEP = 500;

    /** Глубина, м, и давление открытия, кПа. */
    public record Data(int depthM, int ptroKpa) {
        public static final Data DEFAULT = new Data(500, 7000);
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("depth_m").forGetter(Data::depthM),
                Codec.INT.fieldOf("ptro_kpa").forGetter(Data::ptroKpa)).apply(i, Data::new));
        public static final StreamCodec<ByteBuf, Data> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
    }

    public GasLiftValveItem(Properties props) {
        super(props);
    }

    public static Data data(ItemStack stack) {
        Data d = stack.get(PcDataComponents.GAS_LIFT_VALVE.get());
        return d == null ? Data.DEFAULT : d;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Data d = data(stack);
        Data next = player.isShiftKeyDown()
                ? new Data(d.depthM(), d.ptroKpa() + PTRO_STEP > MAX_PTRO_KPA ? MIN_PTRO_KPA : d.ptroKpa() + PTRO_STEP)
                : new Data(d.depthM() + DEPTH_STEP > MAX_DEPTH ? MIN_DEPTH : d.depthM() + DEPTH_STEP, d.ptroKpa());
        stack.set(PcDataComponents.GAS_LIFT_VALVE.get(), next);
        if (!level.isClientSide) player.displayClientMessage(ProductionText.msg("valve_set", next.depthM(),
                ProductionText.num(next.ptroKpa() / 1000.0, 1)), true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Data d = data(stack);
        tooltip.add(ProductionText.msg("valve_tip", d.depthM(), ProductionText.num(d.ptroKpa() / 1000.0, 1)).withStyle(ChatFormatting.GRAY));
    }
}

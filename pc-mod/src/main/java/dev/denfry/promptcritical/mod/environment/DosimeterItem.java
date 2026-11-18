package dev.denfry.promptcritical.mod.environment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.sound.PcSfx;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Дозиметр-радиометр ДКС-04 (по классу — и ДРГ-01Т): мощность дозы и накопленная доза владельца. В руке показывает
 * показания строкой над хотбаром раз в секунду и щёлкает: газоразрядный счётчик СБМ-20 даёт ≈ {@value #CPS_PER_USV_H}
 * имп/с на мкЗв/ч (175 имп/мин). Выше {@value #MAX_SV_H} Зв/ч счётчик захлёбывается — «перегрузка». Показания — с
 * сервера в компоненте предмета.
 */
public final class DosimeterItem extends Item {
    public static final double CPS_PER_USV_H = 2.9, MAX_SV_H = 10;

    public record Reading(float rateSvH, float doseSv) {
        public static final Codec<Reading> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("rate").forGetter(Reading::rateSvH),
                Codec.FLOAT.fieldOf("dose").forGetter(Reading::doseSv)).apply(i, Reading::new));
        public static final StreamCodec<ByteBuf, Reading> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Reading::rateSvH, ByteBufCodecs.FLOAT, Reading::doseSv, Reading::new);
    }

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PromptCritical.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Reading>> READING =
            COMPONENTS.registerComponentType("dosimeter_reading", b -> b.persistent(Reading.CODEC).networkSynchronized(Reading.STREAM_CODEC));

    public DosimeterItem(Properties props) {
        super(props.stacksTo(1));
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }

    /** Мощность дозы человеческими единицами: мкЗв/ч, мЗв/ч, Зв/ч. */
    public static String rate(double svH) {
        if (svH >= MAX_SV_H) return ">10 Sv/h";
        if (svH >= 1) return String.format(Locale.ROOT, "%.2f Sv/h", svH);
        if (svH >= 1e-3) return String.format(Locale.ROOT, "%.2f mSv/h", svH * 1e3);
        return String.format(Locale.ROOT, "%.2f µSv/h", svH * 1e6);
    }

    public static String dose(double sv) {
        if (sv >= 1) return String.format(Locale.ROOT, "%.2f Sv", sv);
        if (sv >= 1e-3) return String.format(Locale.ROOT, "%.2f mSv", sv * 1e3);
        return String.format(Locale.ROOT, "%.1f µSv", sv * 1e6);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(entity instanceof Player player)) return;
        boolean inHand = selected || player.getOffhandItem() == stack;
        if (!level.isClientSide) {
            if (level.getGameTime() % 20 != 0 || !(player instanceof ServerPlayer sp)) return;
            Reading r = new Reading((float) RadiationService.lastRateSvH(sp), (float) RadiationService.totalSv(sp));
            stack.set(READING.get(), r);
            // сутки с дозиметром (достижение dosimeter, 11 §11.2.2): игровые тики, пока прибор в инвентаре
            var data = sp.getPersistentData();
            long worn = data.getLong("promptcritical:dosimeter_ticks") + 20;
            data.putLong("promptcritical:dosimeter_ticks", worn);
            if (worn % 24000 < 20) dev.denfry.promptcritical.mod.content.SimAchievements.direct(sp, dev.denfry.promptcritical.mod.content.SimAchievements.DOSIMETER_DAY, "days", worn / 24000.0);
            if (inHand) {
                ChatFormatting c = r.rateSvH() >= 1e-3 ? ChatFormatting.RED : r.rateSvH() >= 2e-5 ? ChatFormatting.GOLD : ChatFormatting.GREEN;
                sp.displayClientMessage(Component.translatable("message.promptcritical.dosimeter.reading", rate(r.rateSvH()), dose(r.doseSv()))
                        .withStyle(c), true);
            }
            return;
        }
        if (!inHand) return;
        Reading r = stack.get(READING.get());
        if (r == null) return;
        // щелчки счётчика: пуассоновский поток, не чаще раза в тик (дальше — сплошной треск)
        double cps = Math.min(r.rateSvH(), MAX_SV_H) * 1e6 * CPS_PER_USV_H;
        double p = 1 - Math.exp(-cps / 20);
        if (level.random.nextDouble() < p)
            level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(), PcSfx.GEIGER_CLICK.get(), SoundSource.PLAYERS, 0.5f,
                    0.95f + level.random.nextFloat() * 0.1f, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Reading r = stack.get(READING.get());
        if (r == null) {
            tooltip.add(Component.translatable("message.promptcritical.dosimeter.idle").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("message.promptcritical.dosimeter.reading", rate(r.rateSvH()), dose(r.doseSv()))
                .withStyle(ChatFormatting.GRAY));
    }
}
